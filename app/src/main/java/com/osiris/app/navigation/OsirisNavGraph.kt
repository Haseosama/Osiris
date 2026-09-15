package com.osiris.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.osiris.app.globe.GlobeScreen
import com.osiris.app.map.MapScreen
import com.osiris.app.recon.ReconScreen
import com.osiris.app.settings.SettingsScreen

@Composable
fun OsirisNavGraph(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.MAP) {
        composable(Routes.MAP) {
            MapScreen(
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenRecon = { navController.navigate(Routes.RECON) },
                onOpenGlobe = { navController.navigate(Routes.GLOBE) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.RECON) {
            ReconScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.GLOBE) {
            GlobeScreen(onBack = { navController.popBackStack() })
        }
    }
}
