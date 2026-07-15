package com.storagemanager.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.storagemanager.ui.dashboard.DashboardScreen
import com.storagemanager.ui.onboarding.OnboardingScreen

private const val TRANSITION_DURATION = 350

@Composable
fun NavGraph() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route,
        enterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(TRANSITION_DURATION)
            ) + fadeIn(animationSpec = tween(TRANSITION_DURATION))
        },
        exitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(TRANSITION_DURATION)
            ) + fadeOut(animationSpec = tween(TRANSITION_DURATION))
        },
        popEnterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(TRANSITION_DURATION)
            ) + fadeIn(animationSpec = tween(TRANSITION_DURATION))
        },
        popExitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(TRANSITION_DURATION)
            ) + fadeOut(animationSpec = tween(TRANSITION_DURATION))
        }
    ) {
        // ── Onboarding ──────────────────────────────────────
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onGetStarted = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        // ── Dashboard ───────────────────────────────────────
        composable(Screen.Dashboard.route) {
            DashboardScreen(navController = navController)
        }

        // ── Photos ──────────────────────────────────────────
        composable(Screen.Photos.route) {
            PlaceholderScreen(title = "Fotoğraflar")
        }

        // ── Apps ────────────────────────────────────────────
        composable(Screen.Apps.route) {
            PlaceholderScreen(title = "Uygulamalar")
        }

        // ── Cache ───────────────────────────────────────────
        composable(Screen.Cache.route) {
            PlaceholderScreen(title = "Önbellek")
        }

        // ── Files ───────────────────────────────────────────
        composable(Screen.Files.route) {
            PlaceholderScreen(title = "Büyük Dosyalar")
        }

        // ── Settings ────────────────────────────────────────
        composable(Screen.Settings.route) {
            PlaceholderScreen(title = "Ayarlar")
        }
    }
}

/**
 * Temporary placeholder for screens that haven't been implemented yet.
 */
@Composable
private fun PlaceholderScreen(title: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}
