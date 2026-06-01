package com.zure.localaienginetester.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.zure.localaienginetester.ui.screen.camera.PoseCameraScreen
import com.zure.localaienginetester.ui.screen.home.HomeScreen
import com.zure.localaienginetester.ui.screen.model.ModelListScreen
import com.zure.localaienginetester.ui.screen.translation.TranslationScreen
import com.zure.localaienginetester.ui.screen.tts.TtsTestScreen

@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Route.Home
    ) {
        composable<Route.Home> { HomeScreen(navController = navController) }
        composable<Route.ModelList> { ModelListScreen(navController = navController) }
        composable<Route.TranslationTest> { TranslationScreen(navController = navController) }
        composable<Route.CameraPoseTest> { PoseCameraScreen(navController = navController) }
        composable<Route.TtsTest> { TtsTestScreen(navController = navController) }
    }
}
