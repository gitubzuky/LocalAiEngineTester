plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

val supportedAIEngines = setOf("tflite", "llama")
val packagedAIEngines = setOf(
    "tflite",
     "llama",
)

val unknownAIEngines = packagedAIEngines - supportedAIEngines
require(unknownAIEngines.isEmpty()) {
    "Unknown packagedAIEngines value(s): ${unknownAIEngines.joinToString()}. Supported values: ${supportedAIEngines.joinToString()}."
}

android {
    namespace = "com.zure.localaienginetester"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.zure.localaienginetester"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField(
            "String",
            "PACKAGED_AI_ENGINES",
            "\"${packagedAIEngines.joinToString(",")}\""
        )
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "LOGCAT_ENABLED", "true")
            buildConfigField("boolean", "LOG_FILE_ENABLED", "false")
            buildConfigField("boolean", "SYSTEM_LOGCAT_FILE_ENABLED", "false")
        }
        release {
            buildConfigField("boolean", "LOGCAT_ENABLED", "false")
            buildConfigField("boolean", "LOG_FILE_ENABLED", "true")
            buildConfigField("boolean", "SYSTEM_LOGCAT_FILE_ENABLED", "true")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }

    lint {
        disable += "OldTargetApi"
    }
}

dependencies {
    implementation(project(":core"))
    if ("tflite" in packagedAIEngines) {
        implementation(project(":engines:tflite"))
    }
    if ("llama" in packagedAIEngines) {
        implementation(project(":engines:llama"))
    }

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.lifecycle.viewmodel.compose)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Retrofit + OkHttp
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Navigation
    implementation(libs.navigation.compose)

    // Lifecycle ViewModel
    implementation(libs.lifecycle.viewmodel.compose)

    // BqLog
    implementation(libs.bqlog.android)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
