package com.zure.localaienginetester.ui.screen.camera

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.zure.localaiengine.camera.analysis.api.AnalysisLensFacing
import com.zure.localaiengine.camera.analysis.api.CameraAnalysisConfig
import com.zure.localaiengine.camera.analysis.api.CameraAnalysisError
import com.zure.localaiengine.camera.analysis.api.CameraAnalysisState
import com.zure.localaiengine.camera.analysis.api.PreprocessBackend
import com.zure.localaiengine.camera.analysis.camerax.CameraXAnalysisController
import com.zure.localaiengine.camera.analysis.profiles.RtmposeBody2dProfile
import com.zure.localaienginetester.base.UiState
import com.zure.localaienginetester.ui.component.AppScaffold
import com.zure.localaienginetester.ui.theme.LocalAIEngineTesterTheme
import kotlinx.coroutines.launch

@Composable
fun PoseCameraScreen(
    navController: NavController,
    viewModel: PoseCameraViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val data = (uiState as? UiState.Success)?.data
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val controller = remember { CameraXAnalysisController(context) }
    val cameraState by controller.state.collectAsStateWithLifecycle()
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onPermissionChanged(granted)
    }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        viewModel.onPermissionChanged(granted)
        if (!granted) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    LaunchedEffect(data?.hasCameraPermission, previewView) {
        val view = previewView
        if (data?.hasCameraPermission == true && view != null) {
            controller.bind(
                lifecycleOwner = lifecycleOwner,
                previewView = view,
                profile = RtmposeBody2dProfile.create(),
                config = CameraAnalysisConfig(
                    lensFacing = AnalysisLensFacing.Back,
                    maxAnalysisFps = (1000L / data.analysisIntervalMillis).toInt().coerceAtLeast(1),
                    backend = PreprocessBackend.Auto
                )
            )
        }
    }

    LaunchedEffect(controller) {
        controller.outputs.collect { input ->
            viewModel.analyze(input)
        }
    }

    LaunchedEffect(cameraState) {
        val state = cameraState
        if (state is CameraAnalysisState.Error) {
            viewModel.onCameraError(state.error.toDisplayMessage())
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            coroutineScope.launch { controller.unbind() }
        }
    }

    PoseCameraContent(
        uiState = uiState,
        cameraState = cameraState,
        onBackClick = { navController.popBackStack() },
        onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
        cameraPreview = {
            AndroidView(
                factory = { viewContext ->
                    PreviewView(viewContext).also { view ->
                        view.scaleType = PreviewView.ScaleType.FILL_CENTER
                        previewView = view
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    )
}

@Composable
private fun PoseCameraContent(
    uiState: UiState<PoseCameraUiData>,
    cameraState: CameraAnalysisState,
    onBackClick: () -> Unit,
    onRequestPermission: () -> Unit,
    cameraPreview: @Composable () -> Unit
) {
    val data = (uiState as? UiState.Success)?.data
    AppScaffold(
        title = "姿态相机测试",
        onBackClick = onBackClick
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (data?.hasCameraPermission == true) {
                cameraPreview()
                PoseOverlay(
                    pose = data.pose,
                    frameTransform = data.frameTransform,
                    modifier = Modifier.fillMaxSize()
                )
                PoseStatusBar(
                    data = data,
                    cameraState = cameraState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                )
            } else {
                PermissionContent(
                    message = data?.lastError ?: "需要相机权限才能进行姿态分析。",
                    onRequestPermission = onRequestPermission,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun PoseStatusBar(
    data: PoseCameraUiData,
    cameraState: CameraAnalysisState,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = Color.Black.copy(alpha = 0.56f)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = data.modelName,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (data.isAnalyzing) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "间隔 ${data.analysisIntervalMillis}ms · ${cameraState.label()} · " +
                    "耗时 ${data.lastInferenceMillis ?: "-"}ms · " +
                    "点 ${data.detectedKeypoints} · 最高分 ${data.maxPoseScore.formatScore()}",
                color = Color.White.copy(alpha = 0.86f),
                style = MaterialTheme.typography.bodySmall
            )
            data.lastError?.let { message ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = message,
                    color = Color(0xFFFFCDD2),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun PermissionContent(
    message: String,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRequestPermission) {
            Text(text = "授权相机")
        }
    }
}

private fun CameraAnalysisState.label(): String {
    return when (this) {
        CameraAnalysisState.Idle -> "空闲"
        CameraAnalysisState.Opening -> "打开中"
        is CameraAnalysisState.Running -> "分析中"
        is CameraAnalysisState.Error -> "错误"
    }
}

private fun Float?.formatScore(): String {
    return this?.let { String.format("%.2f", it) } ?: "-"
}

private fun CameraAnalysisError.toDisplayMessage(): String {
    return when (this) {
        CameraAnalysisError.CameraPermissionMissing -> "缺少 CAMERA 权限。"
        CameraAnalysisError.CameraUnavailable -> "未找到可用摄像头。"
        CameraAnalysisError.LifecycleNotReady -> "页面生命周期未准备好。"
        CameraAnalysisError.InvalidAnalysisConfig -> "相机分析配置无效。"
        is CameraAnalysisError.BindFailed -> cause.message ?: "相机绑定失败。"
        is CameraAnalysisError.FrameProcessingFailed -> cause.message ?: "相机帧处理失败。"
        is CameraAnalysisError.NativeBackendUnavailable -> cause?.message ?: "Native 预处理不可用。"
    }
}

@Preview(showBackground = true)
@Composable
fun PoseCameraScreenPreview() {
    LocalAIEngineTesterTheme {
        PoseCameraContent(
            uiState = UiState.Success(
                PoseCameraUiData(
                    modelName = "rtmpose_body2d.tflite",
                    hasCameraPermission = true,
                    pose = PoseCameraMockData.pose,
                    frameTransform = PoseCameraMockData.transform,
                    lastInferenceMillis = 42L
                )
            ),
            cameraState = CameraAnalysisState.Running(
                lensFacing = AnalysisLensFacing.Back,
                profileId = "rtmpose_body2d",
                backend = PreprocessBackend.Auto
            ),
            onBackClick = {},
            onRequestPermission = {},
            cameraPreview = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF202124))
                )
            }
        )
    }
}
