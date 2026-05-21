package com.zure.localaienginetester

import android.app.Application
import com.zure.localaienginetester.util.AppLog
import dagger.hilt.android.HiltAndroidApp

/**
 * Application 类。Hilt 入口点，触发依赖图生成。
 */
@HiltAndroidApp
class LocalAIEngineTesterApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLog.init()
    }
}
