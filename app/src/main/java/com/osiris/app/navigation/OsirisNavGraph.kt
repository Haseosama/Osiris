package com.osiris.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.osiris.app.globe.GlobeScreen
import com.osiris.app.map.MapScreen
import com.osiris.app.recon.ReconScreen
import com.osiris.app.settings.SettingsScreen

private const val JUMP_LAT_KEY = "globe_jump_lat"
private const val JUMP_LNG_KEY = "globe_jump_lng"

@Composable
fun OsirisNavGraph(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.MAP) {
        composable(Routes.MAP) { backStackEntry ->
            // Standard Jetpack Navigation "return a result to the previous screen" pattern — the
            // globe screen (pushed on top of this one, not a sibling) writes into THIS entry's
            // own SavedStateHandle just before popping back, since there's no direct way to pass
            // a value the other direction through NavHost otherwise.
            val jumpLat by backStackEntry.savedStateHandle.getStateFlow<Double?>(JUMP_LAT_KEY, null)
                .collectAsStateWithLifecycle()
            val jumpLng by backStackEntry.savedStateHandle.getStateFlow<Double?>(JUMP_LNG_KEY, null)
                .collectAsStateWithLifecycle()
            MapScreen(
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenRecon = { navController.navigate(Routes.RECON) },
                onOpenGlobe = { navController.navigate(Routes.GLOBE) },
                pendingCameraJump = jumpLat?.let { lat -> jumpLng?.let { lng -> lat to lng } },
                onPendingCameraJumpConsumed = {
                    backStackEntry.savedStateHandle[JUMP_LAT_KEY] = null
                    backStackEntry.savedStateHandle[JUMP_LNG_KEY] = null
                },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.RECON) {
            ReconScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.GLOBE) {
            GlobeScreen(
                onBack = { navController.popBackStack() },
                onZoomedToFlatMap = { lat, lng ->
                    navController.previousBackStackEntry?.savedStateHandle?.set(JUMP_LAT_KEY, lat)
                    navController.previousBackStackEntry?.savedStateHandle?.set(JUMP_LNG_KEY, lng)
                    navController.popBackStack()
                },
            )
        }
    }
}
