plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.zure.localaiengine.engines.onnxruntime"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.onnxruntime.android)
}
