package com.zure.localaienginetester.ui.screen.camera

import androidx.compose.ui.geometry.Offset

data class PoseKeypoint(
    val index: Int,
    val x: Float,
    val y: Float,
    val score: Float
)

data class PoseResult(
    val keypoints: List<PoseKeypoint>,
    val inputWidth: Int,
    val inputHeight: Int
)

data class PoseBone(
    val start: Int,
    val end: Int
)

data class PoseOverlayPoint(
    val offset: Offset,
    val score: Float
)

object PoseSkeleton {
    val cocoBody17: List<PoseBone> = listOf(
        PoseBone(5, 7),
        PoseBone(7, 9),
        PoseBone(6, 8),
        PoseBone(8, 10),
        PoseBone(5, 6),
        PoseBone(5, 11),
        PoseBone(6, 12),
        PoseBone(11, 12),
        PoseBone(11, 13),
        PoseBone(13, 15),
        PoseBone(12, 14),
        PoseBone(14, 16),
        PoseBone(0, 1),
        PoseBone(0, 2),
        PoseBone(1, 3),
        PoseBone(2, 4),
        PoseBone(0, 5),
        PoseBone(0, 6)
    )
}
