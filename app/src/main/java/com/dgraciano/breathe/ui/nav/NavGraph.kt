package com.dgraciano.breathe.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dgraciano.breathe.ui.appselect.AppSelectScreen
import com.dgraciano.breathe.ui.home.HomeScreen
import com.dgraciano.breathe.ui.onboarding.OnboardingScreen
import com.dgraciano.breathe.ui.stats.StatsScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val APP_SELECT = "app_select"
    const val STATS = "stats"
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
            HomeScreen(
                onAddApp = { nav.navigate(Routes.APP_SELECT) },
                onViewStats = { nav.navigate(Routes.STATS) }
            )
        }
        composable(Routes.APP_SELECT) {
            AppSelectScreen(onDone = { nav.popBackStack() })
        }
        composable(Routes.STATS) {
            StatsScreen(onBack = { nav.popBackStack() })
        }
    }
}
