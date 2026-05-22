package com.zure.localaiengine.camera.analysis.camerax

import android.content.Context
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import com.zure.localaiengine.camera.analysis.api.AnalysisLensFacing
import com.zure.localaiengine.camera.analysis.api.CameraAnalysisConfig
import com.zure.localaiengine.camera.analysis.api.CameraAnalysisController
import com.zure.localaiengine.camera.analysis.api.CameraAnalysisError
import com.zure.localaiengine.camera.analysis.api.CameraAnalysisInput
import com.zure.localaiengine.camera.analysis.api.CameraAnalysisState
import com.zure.localaiengine.camera.analysis.api.VisionInputProfile
import com.zure.localaiengine.camera.analysis.pipeline.FrameProcessContext
import com.zure.localaiengine.camera.analysis.pipeline.FrameProcessorPipeline
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class CameraXAnalysisController(
    context: Context
) : CameraAnalysisController {
    private val appContext = context.applicationContext
    private val permissionChecker = CameraPermissionChecker(appContext)
    private val frameReader = ImageProxyFrameReader()
    private val analyzerExecutor = Executors.newSingleThreadExecutor()
    private val processingScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val isProcessing = AtomicBoolean(false)
    private var provider: ProcessCameraProvider? = null
    private var lastFrameTimeMillis = 0L

    private val _state = MutableStateFlow<CameraAnalysisState>(CameraAnalysisState.Idle)
    override val state: StateFlow<CameraAnalysisState> = _state

    private val _outputs = MutableSharedFlow<CameraAnalysisInput>(
        replay = 0,
        extraBufferCapacity = 1
    )
    override val outputs: Flow<CameraAnalysisInput> = _outputs

    override suspend fun bind(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView?,
        profile: VisionInputProfile,
        config: CameraAnalysisConfig
    ) {
        if (!permissionChecker.hasCameraPermission()) {
            _state.value = CameraAnalysisState.Error(CameraAnalysisError.CameraPermissionMissing)
            return
        }
        if (config.targetWidth <= 0 || config.targetHeight <= 0 || config.maxAnalysisFps <= 0) {
            _state.value = CameraAnalysisState.Error(CameraAnalysisError.InvalidAnalysisConfig)
            return
        }

        _state.value = CameraAnalysisState.Opening
        runCatching {
            val cameraProvider = appContext.awaitCameraProvider()
            provider = cameraProvider
            val cameraSelector = config.lensFacing.toCameraSelector()
            if (!cameraProvider.hasCamera(cameraSelector)) {
                _state.value = CameraAnalysisState.Error(CameraAnalysisError.CameraUnavailable)
                return
            }

            val pipeline = FrameProcessorPipeline(profile.createPipeline(config))
            val preview = Preview.Builder().build().also { preview ->
                previewView?.let { preview.setSurfaceProvider(it.surfaceProvider) }
            }
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy(
                                Size(config.targetWidth, config.targetHeight),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                            )
                        )
                        .build()
                )
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            analysis.setAnalyzer(analyzerExecutor) { image ->
                analyzeFrame(image, config, profile, pipeline)
            }

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, analysis)
            _state.value = CameraAnalysisState.Running(config.lensFacing, profile.id, config.backend)
        }.onFailure { throwable ->
            _state.value = CameraAnalysisState.Error(CameraAnalysisError.BindFailed(throwable))
        }
    }

    override suspend fun unbind() {
        provider?.unbindAll()
        _state.value = CameraAnalysisState.Idle
    }

    private fun analyzeFrame(
        image: androidx.camera.core.ImageProxy,
        config: CameraAnalysisConfig,
        profile: VisionInputProfile,
        pipeline: FrameProcessorPipeline
    ) {
        val now = System.currentTimeMillis()
        val minFrameInterval = 1000L / config.maxAnalysisFps
        if (now - lastFrameTimeMillis < minFrameInterval || !isProcessing.compareAndSet(false, true)) {
            image.close()
            return
        }
        lastFrameTimeMillis = now

        val frame = runCatching { frameReader.read(image, config.lensFacing) }
        image.close()
        frame.onFailure {
            isProcessing.set(false)
            _state.value = CameraAnalysisState.Error(CameraAnalysisError.FrameProcessingFailed(it))
            return
        }

        processingScope.launch {
            runCatching {
                val context = FrameProcessContext(frame.getOrThrow().info, profile, config)
                pipeline.process(frame.getOrThrow(), context)
            }.onSuccess { output ->
                _outputs.tryEmit(output)
            }.onFailure { throwable ->
                _state.value = CameraAnalysisState.Error(CameraAnalysisError.FrameProcessingFailed(throwable))
            }
            isProcessing.set(false)
        }
    }

    private fun AnalysisLensFacing.toCameraSelector(): CameraSelector {
        return CameraSelector.Builder()
            .requireLensFacing(
                when (this) {
                    AnalysisLensFacing.Back -> CameraSelector.LENS_FACING_BACK
                    AnalysisLensFacing.Front -> CameraSelector.LENS_FACING_FRONT
                }
            )
            .build()
    }
}
