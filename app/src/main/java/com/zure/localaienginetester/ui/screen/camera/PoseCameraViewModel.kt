package com.zure.localaienginetester.ui.screen.camera

import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.zure.localaiengine.camera.analysis.api.CameraAnalysisConfig
import com.zure.localaiengine.camera.analysis.api.CameraAnalysisInput
import com.zure.localaiengine.camera.analysis.api.CameraFramePreprocessor
import com.zure.localaiengine.camera.analysis.api.CropPolicy
import com.zure.localaiengine.camera.analysis.api.PreprocessBackend
import com.zure.localaiengine.camera.analysis.profiles.RtmposeBody2dProfile
import com.zure.localaiengine.core.engine.AIEngine
import com.zure.localaiengine.core.engine.AIEngineManager
import com.zure.localaiengine.core.engine.EngineConfig
import com.zure.localaiengine.core.inference.InferenceOutput
import com.zure.localaiengine.core.inference.InferenceRequest
import com.zure.localaiengine.core.inference.InferenceTask
import com.zure.localaiengine.core.model.ModelFormat
import com.zure.localaiengine.core.vision.BoundingBox
import com.zure.localaiengine.core.vision.Detection
import com.zure.localaienginetester.base.BaseViewModel
import com.zure.localaienginetester.base.ErrorEvent
import com.zure.localaienginetester.base.UiEvent
import com.zure.localaienginetester.base.UiState
import com.zure.localaienginetester.data.model.LocalModelDiscovery
import com.zure.localaienginetester.domain.entity.LocalModel
import com.zure.localaienginetester.domain.entity.ModelSource
import com.zure.localaienginetester.navigation.Route
import com.zure.localaienginetester.util.AppLog
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@HiltViewModel
class PoseCameraViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val aiEngineManager: AIEngineManager,
    private val modelDiscovery: LocalModelDiscovery
) : BaseViewModel<PoseCameraEvent, UiState<PoseCameraUiData>>() {

    private val route = savedStateHandle.toRoute<Route.CameraPoseTest>()
    private val decoder = RtmposeOutputDecoder()
    private val rtmDetPersonDecoder = RtmDetPersonDecoder()
    private val yoloPersonDecoder = YoloPersonDecoder()
    private val inferenceMutex = Mutex()
    private var detectorEngine: AIEngine? = null
    private var detectorType = PersonDetectorType.Yolo
    private var analyzeLogCount = 0
    private var lastDetectorRunElapsedRealtime = 0L
    private var stablePersonDetection: Detection? = null
    private var stableDetectionMisses = 0
    private var lastDetectionElapsedMillis: Long? = null
    private val _uiState = MutableStateFlow<UiState<PoseCameraUiData>>(
        UiState.Success(PoseCameraUiData(modelName = route.modelName))
    )
    val uiState: StateFlow<UiState<PoseCameraUiData>> = _uiState.asStateFlow()

    init {
        loadDetector()
    }

    private fun loadDetector() {
        viewModelScope.launch {
            runCatching {
                val detectorModel = withContext(Dispatchers.IO) { findPersonDetectorModel() }
                val model = detectorModel.model
                detectorType = detectorModel.type
                AppLog.i(
                    TAG,
                    "person-detector model found type=${detectorModel.type.label} " +
                        "name=${model.name} source=${model.source} path=${model.path}"
                )
                val modelFile = withContext(Dispatchers.IO) { modelDiscovery.prepareModelFile(model) }
                val engine = aiEngineManager.loadStandaloneEngine(
                    model.engineId,
                    EngineConfig(
                        modelPath = modelFile.absolutePath,
                        modelFormat = model.format,
                        options = mapOf(
                            "numThreads" to "4",
                            "useXNNPACK" to "false"
                        )
                    )
                )
                detectorEngine = engine
                updateData { data ->
                    data.copy(
                        detectorModelName = model.name,
                        detectorType = detectorModel.type,
                        lastError = null
                    )
                }
                AppLog.i(
                    TAG,
                    "person-detector loaded type=${detectorModel.type.label} " +
                        "name=${model.name} file=${modelFile.absolutePath} size=${modelFile.length()}"
                )
            }.onFailure { throwable ->
                val message = throwable.message ?: "人体检测模型加载失败。"
                updateData { data -> data.copy(lastError = message) }
                AppLog.e(TAG, "person-detector load failed: $message", throwable)
                sendEvent(PoseCameraEvent.Error(message))
            }
        }
    }

    fun onPermissionChanged(granted: Boolean) {
        updateData { data ->
            data.copy(
                hasCameraPermission = granted,
                lastError = if (granted) null else "需要相机权限才能进行姿态分析。"
            )
        }
    }

    fun onCameraError(message: String) {
        updateData { data -> data.copy(lastError = message, isAnalyzing = false) }
        sendEvent(PoseCameraEvent.Error(message))
    }

    fun onCycleDetectorPreprocessMode() {
        resetStableDetection()
        updateData { data ->
            val nextMode = data.detectorPreprocessMode.next()
            AppLog.i(TAG, "rtmdet-preprocess mode changed to ${nextMode.label}")
            data.copy(
                detectorPreprocessMode = nextMode,
                pose = null,
                personDetection = null,
                detectedPersons = 0,
                detectedKeypoints = 0,
                maxPoseScore = null,
                lastDetectionMillis = null,
                lastInferenceMillis = null,
                lastError = null
            )
        }
    }

    fun analyze(input: CameraAnalysisInput) {
        if (inferenceMutex.isLocked) return
        viewModelScope.launch {
            inferenceMutex.withLock {
                val detector = detectorEngine
                if (detector == null) {
                    updateData { data -> data.copy(lastError = "人体检测模型加载中。") }
                    return@withLock
                }
                val sourceFrame = input.sourceFrame
                if (sourceFrame == null) {
                    updateData { data -> data.copy(lastError = "当前相机帧缺少 ROI 复用数据。") }
                    return@withLock
                }
                updateData { data -> data.copy(isAnalyzing = true, lastError = null) }
                runCatching {
                    analyzeLogCount += 1
                    if (shouldLog(analyzeLogCount)) {
                        AppLog.i(
                            TAG,
                            "analyze[$analyzeLogCount] profile=${input.profileId} " +
                                "frame=${input.frameInfo.sourceWidth}x${input.frameInfo.sourceHeight} " +
                                "rotation=${input.frameInfo.rotationDegrees} lens=${input.frameInfo.lensFacing} " +
                                "transform=${input.transform} " +
                                "inputs=${input.tensors.joinToString { "${it.name}:${it.shape.contentToString()}:${it.data.javaClass.simpleName}" }}"
                        )
                    }
                    val detectionState = detectStablePerson(
                        detector = detector,
                        input = input
                    )
                    val person = detectionState.person
                    if (shouldLog(analyzeLogCount)) {
                        AppLog.i(
                            TAG,
                            "person-detector[$analyzeLogCount] type=${detectorType.label} " +
                                "stable detectorRan=${detectionState.detectorRan} " +
                                "reused=${detectionState.reused} rawCount=${detectionState.rawCount} " +
                                "raw=${detectionState.rawPerson?.summary() ?: "none"} " +
                                "iou=${detectionState.iou ?: "none"} " +
                                "misses=$stableDetectionMisses stable=${person?.summary() ?: "none"}"
                        )
                    }
                    if (person == null) {
                        updateData { data ->
                            data.copy(
                                isAnalyzing = false,
                                pose = null,
                                personDetection = null,
                                detectedPersons = 0,
                                detectedKeypoints = 0,
                                maxPoseScore = null,
                                lastDetectionMillis = detectionState.detectorElapsedMillis,
                                lastInferenceMillis = null,
                                frameTransform = input.transform,
                                lastError = null
                            )
                        }
                        return@runCatching
                    }
                    val poseRoi = person.boundingBox.toRtmposeRoi(input.transform)
                    if (shouldLog(analyzeLogCount)) {
                        AppLog.i(
                            TAG,
                            "person-detector[$analyzeLogCount] selected=${person.summary()} " +
                                "rtmposeRoi=(${poseRoi.left},${poseRoi.top},${poseRoi.width},${poseRoi.height})"
                        )
                    }

                    val poseInput = CameraFramePreprocessor.process(
                        frame = sourceFrame,
                        profile = RtmposeBody2dProfile.create(
                            cropPolicy = poseRoi
                        ),
                        config = CameraAnalysisConfig(
                            lensFacing = input.frameInfo.lensFacing,
                            targetWidth = input.frameInfo.sourceWidth,
                            targetHeight = input.frameInfo.sourceHeight,
                            backend = PreprocessBackend.Auto
                        )
                    )
                    val result = aiEngineManager.infer(
                        InferenceRequest(
                            task = InferenceTask.TENSOR,
                            inputs = poseInput.tensors
                        )
                    )
                    val pose = decoder.decode(
                        outputs = result.outputs,
                        inputWidth = poseInput.transform.modelInputWidth,
                        inputHeight = poseInput.transform.modelInputHeight
                    )
                    if (shouldLog(analyzeLogCount)) {
                        AppLog.i(
                            TAG,
                            "analyze[$analyzeLogCount] result elapsed=${result.elapsedMillis}ms " +
                                "outputs=${result.outputs.filterIsInstance<com.zure.localaiengine.core.inference.InferenceOutput.Tensor>().joinToString { "${it.name}:${it.shape.contentToString()}" }} " +
                                "posePoints=${pose.keypoints.size} maxScore=${pose.keypoints.maxOfOrNull { it.score }}"
                        )
                    }
                    updateData { data ->
                        data.copy(
                            isAnalyzing = false,
                            pose = pose,
                            frameTransform = poseInput.transform,
                            personDetection = person,
                            detectedPersons = 1,
                            detectedKeypoints = pose.keypoints.size,
                            maxPoseScore = pose.keypoints.maxOfOrNull { it.score },
                            lastInferenceMillis = result.elapsedMillis,
                            lastDetectionMillis = detectionState.detectorElapsedMillis,
                            lastError = null
                        )
                    }
                }.onFailure { throwable ->
                    val message = throwable.message ?: "姿态分析失败。"
                    AppLog.e(TAG, "analyze[$analyzeLogCount] failed: $message", throwable)
                    updateData { data -> data.copy(isAnalyzing = false, lastError = message) }
                    sendEvent(PoseCameraEvent.Error(message))
                }
            }
        }
    }

    override fun onCleared() {
        val engine = detectorEngine
        detectorEngine = null
        if (engine != null) {
            runBlocking(Dispatchers.IO) { engine.close() }
        }
        super.onCleared()
    }

    private fun findPersonDetectorModel(): DetectorModel {
        val engine = aiEngineManager.availableEngines.firstOrNull { it.id == "tflite" }
            ?: error("未打包 TFLite 引擎，无法加载人体检测模型。")
        val discoveryResult = modelDiscovery.discover(engine)
        val tfliteModels = discoveryResult.models.filter { model ->
            model.engineId == "tflite" &&
                model.format == ModelFormat.TFLITE
        }
        val yoloCandidates = tfliteModels.filter { model ->
            model.name.contains("yolo", ignoreCase = true) &&
                model.name.contains("person", ignoreCase = true)
        }
        val rtmDetCandidates = tfliteModels.filter { model ->
            model.name.contains("rtmdet", ignoreCase = true) &&
                model.name.contains("person", ignoreCase = true)
        }
        val yolo = yoloCandidates.selectPreferred()
        val rtmDet = rtmDetCandidates.selectPreferred()
        val selected = rtmDet?.let { DetectorModel(PersonDetectorType.RtmDet, it) }
            ?: yolo?.let { DetectorModel(PersonDetectorType.Yolo, it) }
            ?: error(
                "未找到 RTMDet/YOLO person detector TFLite 模型。请将模型放入 " +
                    "app/src/main/assets/models/tflite/，或外部目录 " +
                    "${discoveryResult.externalDirectoryPath ?: "models/tflite"}，并确保文件名包含 yolo+person 或 rtmdet+person。"
            )
        AppLog.i(
            TAG,
            "person-detector yoloCandidates=${yoloCandidates.joinToString { "${it.name}:${it.source}:${it.path}" }} " +
                "rtmDetCandidates=${rtmDetCandidates.joinToString { "${it.name}:${it.source}:${it.path}" }} " +
                "selected=${selected.type.label}:${selected.model.name}:${selected.model.source}"
        )
        return selected
    }

    private fun List<LocalModel>.selectPreferred(): LocalModel? {
        return firstOrNull { it.source == ModelSource.Assets } ?: firstOrNull()
    }

    private suspend fun detectStablePerson(
        detector: AIEngine,
        input: CameraAnalysisInput
    ): StableDetectionState {
        val now = SystemClock.elapsedRealtime()
        val cached = stablePersonDetection
        val shouldRunDetector = cached == null ||
            now - lastDetectorRunElapsedRealtime >= DETECTOR_INTERVAL_MILLIS
        if (!shouldRunDetector) {
            return StableDetectionState(
                person = cached,
                rawPerson = null,
                rawCount = 0,
                detectorElapsedMillis = lastDetectionElapsedMillis,
                detectorRan = false,
                reused = true,
                iou = null
            )
        }

        val detectionResult = detector.infer(
            InferenceRequest(
                task = InferenceTask.TENSOR,
                inputs = input.tensors
            )
        )
        lastDetectorRunElapsedRealtime = now
        lastDetectionElapsedMillis = detectionResult.elapsedMillis
        if (shouldLog(analyzeLogCount)) {
            AppLog.i(
                TAG,
                "person-detector[$analyzeLogCount] type=${detectorType.label} " +
                    "elapsed=${detectionResult.elapsedMillis}ms " +
                    "outputs=${detectionResult.outputs.tensorSummary()}"
            )
        }
        val detections = when (detectorType) {
            PersonDetectorType.Yolo -> yoloPersonDecoder.decode(
                outputs = detectionResult.outputs,
                transform = input.transform
            )
            PersonDetectorType.RtmDet -> rtmDetPersonDecoder.decode(
                outputs = detectionResult.outputs,
                transform = input.transform
            )
        }
        val rawPerson = detections.bestPerson(input.transform)
        val stableBefore = stablePersonDetection
        val iou = if (stableBefore != null && rawPerson != null) {
            stableBefore.boundingBox.iou(rawPerson.boundingBox)
        } else {
            null
        }
        val stable = stabilizePersonDetection(
            current = stableBefore,
            raw = rawPerson,
            iou = iou
        )
        return StableDetectionState(
            person = stable,
            rawPerson = rawPerson,
            rawCount = detections.size,
            detectorElapsedMillis = detectionResult.elapsedMillis,
            detectorRan = true,
            reused = stable != null && rawPerson != null && stable !== rawPerson,
            iou = iou
        )
    }

    private fun stabilizePersonDetection(
        current: Detection?,
        raw: Detection?,
        iou: Float?
    ): Detection? {
        if (raw == null) {
            stableDetectionMisses += 1
            if (stableDetectionMisses <= MAX_STABLE_DETECTION_MISSES) return current
            stablePersonDetection = null
            return null
        }
        stableDetectionMisses = 0
        if (current == null) {
            stablePersonDetection = raw
            return raw
        }

        val shouldAcceptRaw = (iou ?: 0f) >= STABLE_DETECTION_MIN_IOU ||
            raw.score - current.score >= STABLE_DETECTION_SCORE_GAIN
        val next = if (shouldAcceptRaw) {
            raw.copy(
                score = raw.score,
                boundingBox = current.boundingBox.smoothTo(raw.boundingBox, STABLE_DETECTION_EMA_ALPHA)
            )
        } else {
            current
        }
        stablePersonDetection = next
        return next
    }

    private fun resetStableDetection() {
        stablePersonDetection = null
        stableDetectionMisses = 0
        lastDetectorRunElapsedRealtime = 0L
        lastDetectionElapsedMillis = null
    }

    private fun List<Detection>.bestPerson(
        transform: com.zure.localaiengine.camera.analysis.api.FrameTransform
    ): Detection? {
        val width = transform.sourceWidth.takeIf { it > 0f } ?: transform.cropWidth
        val height = transform.sourceHeight.takeIf { it > 0f } ?: transform.cropHeight
        val sourceArea = width * height
        val filtered = if (sourceArea > 0f) {
            filter { detection -> detection.boundingBox.area() / sourceArea <= 0.8f }
        } else {
            this
        }
        return (filtered.ifEmpty { this }).maxWithOrNull(
            compareBy<Detection> { it.score }
                .thenBy { -it.boundingBox.area() }
        )
    }

    private fun BoundingBox.toRtmposeRoi(transform: com.zure.localaiengine.camera.analysis.api.FrameTransform): CropPolicy.Roi {
        val targetRatio = 192f / 256f
        val boxWidth = (right - left).coerceAtLeast(1f)
        val boxHeight = (bottom - top).coerceAtLeast(1f)
        val centerX = (left + right) / 2f
        val centerY = (top + bottom) / 2f
        var roiWidth = boxWidth * 1.25f
        var roiHeight = boxHeight * 1.25f
        if (roiWidth / roiHeight > targetRatio) {
            roiHeight = roiWidth / targetRatio
        } else {
            roiWidth = roiHeight * targetRatio
        }
        val sourceWidth = transform.sourceWidth.takeIf { it > 0f } ?: transform.cropWidth
        val sourceHeight = transform.sourceHeight.takeIf { it > 0f } ?: transform.cropHeight
        val left = (centerX - roiWidth / 2f).coerceIn(0f, (sourceWidth - roiWidth).coerceAtLeast(0f))
        val top = (centerY - roiHeight / 2f).coerceIn(0f, (sourceHeight - roiHeight).coerceAtLeast(0f))
        return CropPolicy.Roi(
            left = left,
            top = top,
            width = roiWidth.coerceAtMost(sourceWidth),
            height = roiHeight.coerceAtMost(sourceHeight)
        )
    }

    private fun BoundingBox.area(): Float {
        return (right - left).coerceAtLeast(0f) * (bottom - top).coerceAtLeast(0f)
    }

    private fun BoundingBox.iou(other: BoundingBox): Float {
        val interLeft = maxOf(left, other.left)
        val interTop = maxOf(top, other.top)
        val interRight = minOf(right, other.right)
        val interBottom = minOf(bottom, other.bottom)
        val interWidth = (interRight - interLeft).coerceAtLeast(0f)
        val interHeight = (interBottom - interTop).coerceAtLeast(0f)
        val interArea = interWidth * interHeight
        val union = area() + other.area() - interArea
        return if (union <= 0f) 0f else interArea / union
    }

    private fun BoundingBox.smoothTo(target: BoundingBox, alpha: Float): BoundingBox {
        return BoundingBox(
            left = left.lerp(target.left, alpha),
            top = top.lerp(target.top, alpha),
            right = right.lerp(target.right, alpha),
            bottom = bottom.lerp(target.bottom, alpha)
        )
    }

    private fun Float.lerp(target: Float, alpha: Float): Float {
        return this + (target - this) * alpha
    }

    private fun List<InferenceOutput>.tensorSummary(): String {
        return filterIsInstance<InferenceOutput.Tensor>()
            .joinToString(prefix = "[", postfix = "]") { tensor ->
                "${tensor.name}:${tensor.shape.contentToString()}:${tensor.data.javaClass.simpleName}"
            }
    }

    private fun Detection.summary(): String {
        return "score=$score box=(${boundingBox.left},${boundingBox.top},${boundingBox.right},${boundingBox.bottom})"
    }

    private fun updateData(reducer: (PoseCameraUiData) -> PoseCameraUiData) {
        val current = (_uiState.value as? UiState.Success)?.data
            ?: PoseCameraUiData(modelName = route.modelName)
        _uiState.value = UiState.Success(reducer(current))
    }

    private fun shouldLog(count: Int): Boolean = count <= 5 || count % 30 == 0

    private data class StableDetectionState(
        val person: Detection?,
        val rawPerson: Detection?,
        val rawCount: Int,
        val detectorElapsedMillis: Long?,
        val detectorRan: Boolean,
        val reused: Boolean,
        val iou: Float?
    )

    private data class DetectorModel(
        val type: PersonDetectorType,
        val model: LocalModel
    )

    private companion object {
        const val TAG = "RTMPoseDebug"
        const val DETECTOR_INTERVAL_MILLIS = 900L
        const val MAX_STABLE_DETECTION_MISSES = 3
        const val STABLE_DETECTION_MIN_IOU = 0.2f
        const val STABLE_DETECTION_SCORE_GAIN = 0.04f
        const val STABLE_DETECTION_EMA_ALPHA = 0.25f
    }
}

sealed class PoseCameraEvent : UiEvent {
    data class Error(override val message: String) : PoseCameraEvent(), ErrorEvent
}
