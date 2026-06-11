package com.dgraciano.breathe.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dgraciano.breathe.ui.appselect.AppSelectScreen
import com.dgraciano.breathe.ui.home.HomeScreen
import com.dgraciano.breathe.ui.onboarding.OnboardingScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val APP_SELECT = "app_select"
}

@Composable
fun BreatheNavGraph() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.ONBOARDING) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onPermissionsGranted = {
                    nav.navigate(Routes.HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.HOME) {
            HomeScreen(onAddApp = { nav.navigate(Routes.APP_SELECT) })
        }
        composable(Routes.APP_SELECT) {
            AppSelectScreen(onDone = { nav.popBackStack() })
        }
    }
}
