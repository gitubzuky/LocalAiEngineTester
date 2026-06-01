package com.zure.localaienginetester.ui.screen.camera

import com.zure.localaiengine.camera.analysis.api.FrameTransform
import com.zure.localaiengine.core.inference.InferenceOutput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RtmDetPersonDecoderTest {
    @Test
    fun decode_keepsDistantPersonTopKBoxes() {
        val decoder = RtmDetPersonDecoder()
        val outputs = listOf(
            InferenceOutput.Tensor(
                name = "dets",
                shape = intArrayOf(1, 3, 5),
                data = arrayOf(
                    arrayOf(
                        floatArrayOf(0f, 0f, 640f, 640f, 0.04f),
                        floatArrayOf(120f, 90f, 170f, 150f, 0.21f),
                        floatArrayOf(300f, 100f, 520f, 420f, 0.12f)
                    )
                )
            ),
            InferenceOutput.Tensor(
                name = "labels",
                shape = intArrayOf(1, 3),
                data = intArrayOf(0, 0, 0)
            )
        )

        val detections = decoder.decode(outputs, fullFrameTransform())

        assertEquals(2, detections.size)
        assertTrue(detections.any { it.score == 0.21f })
    }

    private fun fullFrameTransform(): FrameTransform {
        return FrameTransform(
            modelInputWidth = 640,
            modelInputHeight = 640,
            sourceWidth = 640f,
            sourceHeight = 640f,
            cropWidth = 640f,
            cropHeight = 640f,
            scaleX = 1f,
            scaleY = 1f
        )
    }
}
