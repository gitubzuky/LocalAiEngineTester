package com.zure.localaienginetester.di

import com.zure.localaiengine.core.engine.AIEngineManager
import com.zure.localaiengine.core.engine.EngineFactory
import com.zure.localaiengine.core.engine.EngineRegistry
import com.zure.localaienginetester.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AIEngineModule {

    @Provides
    @Singleton
    fun provideEngineFactories(): Set<EngineFactory> {
        return BuildConfig.PACKAGED_AI_ENGINES
            .split(",")
            .mapNotNull { engineId -> engineFactoryClassNames[engineId.trim()] }
            .map { className -> createEngineFactory(className) }
            .toSet()
    }

    @Provides
    @Singleton
    fun provideEngineRegistry(factories: Set<@JvmSuppressWildcards EngineFactory>): EngineRegistry {
        return EngineRegistry(factories)
    }

    @Provides
    @Singleton
    fun provideAIEngineManager(registry: EngineRegistry): AIEngineManager {
        return AIEngineManager(registry)
    }

    private fun createEngineFactory(className: String): EngineFactory {
        val instance = Class.forName(className)
            .getDeclaredConstructor()
            .newInstance()
        return instance as EngineFactory
    }

    private val engineFactoryClassNames = mapOf(
        "tflite" to "com.zure.localaiengine.engines.tflite.TfLiteEngineFactory",
        "llama" to "com.zure.localaiengine.engines.llama.LlamaEngineFactory",
        "onnxruntime" to "com.zure.localaiengine.engines.onnxruntime.OnnxRuntimeEngineFactory",
        "sherpa-onnx" to "com.zure.localaienginetester.engine.SherpaOnnxTtsEngineFactory"
    )
}
