plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.zure.localaiengine.sherpa.onnx.tts.bundle.kokoro"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 26
    }
}
