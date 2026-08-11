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
import com.dgraciano.breathe.ui.settings.SettingsScreen
import com.dgraciano.breathe.ui.stats.StatsScreen
import com.dgraciano.breathe.ui.util.rememberReducedMotion

object Routes {
    const val ONBOARDING   = "onboarding"
    const val HOME         = "home"
    const val APP_SELECT   = "app_select"
    const val STATS        = "stats"
    const val ACHIEVEMENTS = "achievements"
    const val SETTINGS     = "settings"
}

@Composable
fun BreatheNavGraph() {
    val nav = rememberNavController()
    val onboardingViewModel: OnboardingViewModel = hiltViewModel()

    // Captured once: NavHost reads startDestination only on first composition, so a value
    // that changes later is silently ignored. Gate on what actually powers interception.
    val startDest = remember {
        val ready = onboardingViewModel.hasAccessibility.value &&
            onboardingViewModel.hasOverlayPermission.value
        if (ready) Routes.HOME else Routes.ONBOARDING
    }

    // Decorative screen transitions; skipped when the user has animations turned off.
    val reducedMotion = rememberReducedMotion()
    val duration = if (reducedMotion) 0 else 700

    NavHost(
        navController = nav,
        startDestination = startDest,
        enterTransition = {
            fadeIn(animationSpec = tween(duration)) + slideInHorizontally(animationSpec = tween(duration)) { it / 10 }
        },
        exitTransition = {
            fadeOut(animationSpec = tween(duration)) + slideOutHorizontally(animationSpec = tween(duration)) { -it / 10 }
        },
        popEnterTransition = {
            fadeIn(animationSpec = tween(duration)) + slideInHorizontally(animationSpec = tween(duration)) { -it / 10 }
        },
        popExitTransition = {
            fadeOut(animationSpec = tween(duration)) + slideOutHorizontally(animationSpec = tween(duration)) { it / 10 }
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
                onAchievements = { nav.navigate(Routes.ACHIEVEMENTS) },
                onSettings     = { nav.navigate(Routes.SETTINGS) }
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
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
    }
}
