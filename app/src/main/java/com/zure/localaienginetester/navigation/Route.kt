package com.zure.localaienginetester.navigation

import kotlinx.serialization.Serializable

sealed class Route {
    @Serializable data object Home : Route()
    @Serializable data class ModelList(val engineId: String) : Route()
    @Serializable data class TranslationTest(val modelName: String) : Route()
    @Serializable data class CameraPoseTest(val modelName: String) : Route()
}
