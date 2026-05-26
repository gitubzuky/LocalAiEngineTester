package com.zure.localaienginetester.ui.screen.camera

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import com.zure.localaiengine.camera.analysis.api.FrameTransform
import com.zure.localaiengine.core.vision.Detection
import com.zure.localaienginetester.ui.theme.LocalAIEngineTesterTheme
import com.zure.localaienginetester.util.AppLog

@Composable
fun PoseOverlay(
    pose: PoseResult?,
    frameTransform: FrameTransform?,
    personDetection: Detection? = null,
    modifier: Modifier = Modifier,
    minScore: Float = 0.15f,
    minPointScore: Float = 0.05f
) {
    val logger = remember { PoseOverlayLogger() }
    Canvas(modifier = modifier.fillMaxSize()) {
        if (pose == null || frameTransform == null) return@Canvas
        val points = pose.keypoints.associate { keypoint ->
            keypoint.index to PoseOverlayPoint(
                offset = keypoint.toPreviewOffset(frameTransform, size.width, size.height),
                score = keypoint.score
            )
        }
        drawRoi(frameTransform)
        personDetection?.let { detection -> drawDetection(detection, frameTransform) }
        logger.log(
            pose = pose,
            frameTransform = frameTransform,
            points = points,
            minScore = minScore,
            canvasWidth = size.width,
            canvasHeight = size.height
        )
        drawSkeleton(points, minScore)
        points.values
            .forEach { point ->
                drawCircle(
                    color = when {
                        point.score >= minScore -> Color(0xFF00E5A8)
                        point.score >= minPointScore -> Color(0x6680D8FF)
                        else -> Color.Transparent
                    },
                    radius = if (point.score >= minScore) 5f else 3f,
                    center = point.offset
                )
            }
    }
}

private class PoseOverlayLogger {
    private var drawCount = 0

    fun log(
        pose: PoseResult,
        frameTransform: FrameTransform,
        points: Map<Int, PoseOverlayPoint>,
        minScore: Float,
        canvasWidth: Float,
        canvasHeight: Float
    ) {
        drawCount += 1
        if (!shouldLog(drawCount)) return
        val visible = points.values.filter { it.score >= minScore }
        val visibleBones = PoseSkeleton.cocoBody17.count { bone ->
            val start = points[bone.start]
            val end = points[bone.end]
            start != null && end != null && start.score >= minScore && end.score >= minScore
        }
        AppLog.i(
            TAG,
            "overlay[$drawCount] canvas=${canvasWidth}x$canvasHeight input=${pose.inputWidth}x${pose.inputHeight} " +
                "transform=$frameTransform points=${points.size} visible@$minScore=${visible.size} bones=$visibleBones " +
                "offsets=${visible.offsetSummary()}"
        )
    }

    private fun shouldLog(count: Int): Boolean = count <= 5 || count % 30 == 0

    private fun List<PoseOverlayPoint>.offsetSummary(): String {
        if (isEmpty()) return "none"
        val minX = minOf { it.offset.x }
        val maxX = maxOf { it.offset.x }
        val minY = minOf { it.offset.y }
        val maxY = maxOf { it.offset.y }
        val minScore = minOf { it.score }
        val maxScore = maxOf { it.score }
        return "x=$minX..$maxX y=$minY..$maxY score=$minScore..$maxScore"
    }

    private companion object {
        const val TAG = "RTMPoseDebug"
    }
}

private fun DrawScope.drawDetection(detection: Detection, transform: FrameTransform) {
    val topLeft = sourceToPreviewOffset(
        sourceX = detection.boundingBox.left,
        sourceY = detection.boundingBox.top,
        transform = transform,
        canvasWidth = size.width,
        canvasHeight = size.height
    )
    val bottomRight = sourceToPreviewOffset(
        sourceX = detection.boundingBox.right,
        sourceY = detection.boundingBox.bottom,
        transform = transform,
        canvasWidth = size.width,
        canvasHeight = size.height
    )
    drawRect(
        color = Color(0xFFFF8A65),
        topLeft = topLeft,
        size = Size(
            width = bottomRight.x - topLeft.x,
            height = bottomRight.y - topLeft.y
        ),
        style = Stroke(width = 3f)
    )
}

private fun DrawScope.drawRoi(transform: FrameTransform) {
    val topLeft = sourceToPreviewOffset(
        sourceX = transform.cropLeft,
        sourceY = transform.cropTop,
        transform = transform,
        canvasWidth = size.width,
        canvasHeight = size.height
    )
    val bottomRight = sourceToPreviewOffset(
        sourceX = transform.cropLeft + transform.cropWidth,
        sourceY = transform.cropTop + transform.cropHeight,
        transform = transform,
        canvasWidth = size.width,
        canvasHeight = size.height
    )
    drawRect(
        color = Color(0xFF40C4FF),
        topLeft = topLeft,
        size = Size(
            width = bottomRight.x - topLeft.x,
            height = bottomRight.y - topLeft.y
        ),
        style = Stroke(width = 3f)
    )
}

private fun PoseKeypoint.toPreviewOffset(
    transform: FrameTransform,
    canvasWidth: Float,
    canvasHeight: Float
): Offset {
    val sourceWidth = transform.sourceWidth.takeIf { it > 0f } ?: (transform.cropWidth + transform.cropLeft * 2f)
    val sourceHeight = transform.sourceHeight.takeIf { it > 0f } ?: (transform.cropHeight + transform.cropTop * 2f)
    if (sourceWidth <= 0f || sourceHeight <= 0f) {
        return Offset.Zero
    }

    val sourceX = x / transform.scaleX + transform.cropLeft
    val sourceY = y / transform.scaleY + transform.cropTop
    return sourceToPreviewOffset(sourceX, sourceY, transform, canvasWidth, canvasHeight)
}

private fun sourceToPreviewOffset(
    sourceX: Float,
    sourceY: Float,
    transform: FrameTransform,
    canvasWidth: Float,
    canvasHeight: Float
): Offset {
    val sourceWidth = transform.sourceWidth.takeIf { it > 0f } ?: (transform.cropWidth + transform.cropLeft * 2f)
    val sourceHeight = transform.sourceHeight.takeIf { it > 0f } ?: (transform.cropHeight + transform.cropTop * 2f)
    if (sourceWidth <= 0f || sourceHeight <= 0f) {
        return Offset.Zero
    }

    val previewScale = maxOf(canvasWidth / sourceWidth, canvasHeight / sourceHeight)
    val previewLeft = (canvasWidth - sourceWidth * previewScale) / 2f
    val previewTop = (canvasHeight - sourceHeight * previewScale) / 2f
    return Offset(
        x = previewLeft + sourceX * previewScale,
        y = previewTop + sourceY * previewScale
    )
}

private fun DrawScope.drawSkeleton(
    points: Map<Int, PoseOverlayPoint>,
    minScore: Float
) {
    PoseSkeleton.cocoBody17.forEach { bone ->
        val start = points[bone.start]
        val end = points[bone.end]
        if (start != null && end != null && start.score >= minScore && end.score >= minScore) {
            drawLine(
                color = Color(0xFFFFD54F),
                start = start.offset,
                end = end.offset,
                strokeWidth = 4f,
                cap = StrokeCap.Round
            )
        }
    }
}

object PoseCameraMockData {
    val transform = FrameTransform(
        modelInputWidth = 192,
        modelInputHeight = 256,
        cropWidth = 192f,
        cropHeight = 256f,
        scaleX = 1f,
        scaleY = 1f
    )

    val pose = PoseResult(
        inputWidth = 192,
        inputHeight = 256,
        keypoints = listOf(
            PoseKeypoint(0, 96f, 35f, 0.9f),
            PoseKeypoint(5, 66f, 78f, 0.9f),
            PoseKeypoint(6, 125f, 78f, 0.9f),
            PoseKeypoint(7, 51f, 125f, 0.8f),
            PoseKeypoint(8, 140f, 125f, 0.8f),
            PoseKeypoint(9, 43f, 170f, 0.75f),
            PoseKeypoint(10, 148f, 170f, 0.75f),
            PoseKeypoint(11, 78f, 156f, 0.9f),
            PoseKeypoint(12, 115f, 156f, 0.9f),
            PoseKeypoint(13, 74f, 210f, 0.85f),
            PoseKeypoint(14, 119f, 210f, 0.85f),
            PoseKeypoint(15, 72f, 248f, 0.8f),
            PoseKeypoint(16, 121f, 248f, 0.8f)
        )
    )
}

@Preview(showBackground = true)
@Composable
fun PoseOverlayPreview() {
    LocalAIEngineTesterTheme {
        PoseOverlay(
            pose = PoseCameraMockData.pose,
            frameTransform = PoseCameraMockData.transform
        )
    }
}
