plugins {
    alias(libs.plugins.android.library)
}

val llamaCppDir = file("src/main/cpp/llama.cpp")

android {
    namespace = "com.zure.localaiengine.engines.llama"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 26

        if (llamaCppDir.exists()) {
            externalNativeBuild {
                cmake {
                    cppFlags += listOf("-std=c++17", "-fexceptions", "-frtti")
                    arguments += listOf(
                        "-DANDROID_STL=c++_shared",
                        "-DGGML_NATIVE=OFF",
                        "-DGGML_OPENMP=OFF",
                        "-DGGML_VULKAN=OFF",
                        "-DGGML_BLAS=OFF",
                        "-DGGML_LLAMAFILE=OFF"
                    )
                }
            }
        }

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    if (llamaCppDir.exists()) {
        externalNativeBuild {
            cmake {
                path = file("src/main/cpp/CMakeLists.txt")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":core"))
}
