package com.dgraciano.breathe.ui.nav

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dgraciano.breathe.ui.achievements.AchievementsScreen
import com.dgraciano.breathe.ui.appselect.AppSelectScreen
import com.dgraciano.breathe.ui.home.HomeScreen
import com.dgraciano.breathe.ui.onboarding.OnboardingScreen
import com.dgraciano.breathe.ui.onboarding.OnboardingViewModel
import com.dgraciano.breathe.ui.stats.StatsScreen

object Routes {
    const val ONBOARDING   = "onboarding"
    const val HOME         = "home"
    const val APP_SELECT   = "app_select"
    const val STATS        = "stats"
    const val ACHIEVEMENTS = "achievements"
}

@Composable
fun BreatheNavGraph() {
    val nav = rememberNavController()
    val onboardingViewModel: OnboardingViewModel = hiltViewModel()

    // Keyed on the two permissions interception actually needs. It used to key on usage
    // access, which has been optional since detection moved to the accessibility service —
    // so anyone who declined it landed here on every launch despite a working setup.
    //
    // Remembered because NavHost reads startDestination once. Recomputing it later cannot
    // re-route anything; it would only make the graph disagree with itself. The ViewModel
    // refreshes permission state in its init, so this reads a settled value.
    val startDest = remember {
        if (onboardingViewModel.isSetupComplete()) Routes.HOME else Routes.ONBOARDING
    }

    NavHost(
        navController = nav,
        startDestination = startDest,
        enterTransition = {
            fadeIn(animationSpec = tween(700)) + slideInHorizontally(animationSpec = tween(700)) { it / 10 }
        },
        exitTransition = {
            fadeOut(animationSpec = tween(700)) + slideOutHorizontally(animationSpec = tween(700)) { -it / 10 }
        },
        popEnterTransition = {
            fadeIn(animationSpec = tween(700)) + slideInHorizontally(animationSpec = tween(700)) { -it / 10 }
        },
        popExitTransition = {
            fadeOut(animationSpec = tween(700)) + slideOutHorizontally(animationSpec = tween(700)) { it / 10 }
        }
    ) {
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
                onAddApp       = { nav.navigate(Routes.APP_SELECT) },
                onViewStats    = { nav.navigate(Routes.STATS) },
                onAchievements = { nav.navigate(Routes.ACHIEVEMENTS) }
            )
        }
        composable(Routes.APP_SELECT) {
            AppSelectScreen(onDone = { nav.popBackStack() })
        }
        composable(Routes.STATS) {
            StatsScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.ACHIEVEMENTS) {
            AchievementsScreen(onBack = { nav.popBackStack() })
        }
    }
}
