plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.zure.localaiengine.camera.analysis"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 26

        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++17")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    api(project(":core"))

    implementation(libs.androidx.core.ktx)
    api(libs.androidx.camera.core)
    api(libs.androidx.camera.lifecycle)
    api(libs.androidx.camera.view)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.kotlinx.coroutines.core)
}
