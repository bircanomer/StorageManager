package com.storagemanager.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import androidx.navigation.compose.rememberNavController
import com.storagemanager.ui.dashboard.DashboardScreen
import com.storagemanager.ui.onboarding.OnboardingScreen

private const val TRANSITION_DURATION = 350

@Composable
fun NavGraph(
    startDestinationViewModel: StartDestinationViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val navController = rememberNavController()
    val startRoute by startDestinationViewModel.startRoute.collectAsStateWithLifecycle()

    // Tercih okunana kadar boş yüzey göster — yanlış ekranın anlık görünmesini engeller
    val destination = startRoute ?: run {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }

    NavHost(
        navController = navController,
        startDestination = destination,
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
        composable(
            Screen.Onboarding.route,
            deepLinks = listOf(navDeepLink { uriPattern = Screen.Onboarding.deepLink })
        ) {
            OnboardingScreen(
                onGetStarted = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        // ── Dashboard ───────────────────────────────────────
        composable(
            Screen.Dashboard.route,
            deepLinks = listOf(navDeepLink { uriPattern = Screen.Dashboard.deepLink })
        ) {
            DashboardScreen(navController = navController)
        }

        // ── Photos ──────────────────────────────────────────
        composable(
            Screen.Photos.route,
            deepLinks = listOf(navDeepLink { uriPattern = Screen.Photos.deepLink })
        ) {
            com.storagemanager.ui.photos.PhotosScreen(navController = navController)
        }

        // ── Apps ────────────────────────────────────────────
        composable(
            Screen.Apps.route,
            deepLinks = listOf(navDeepLink { uriPattern = Screen.Apps.deepLink })
        ) {
            com.storagemanager.ui.apps.AppsScreen(navController = navController)
        }

        // ── Cache ───────────────────────────────────────────
        composable(
            Screen.Cache.route,
            deepLinks = listOf(navDeepLink { uriPattern = Screen.Cache.deepLink })
        ) {
            com.storagemanager.ui.cache.CacheScreen(navController = navController)
        }

        // ── Files ───────────────────────────────────────────
        composable(
            Screen.Files.route,
            deepLinks = listOf(navDeepLink { uriPattern = Screen.Files.deepLink })
        ) {
            com.storagemanager.ui.files.FilesScreen(navController = navController)
        }

        // ── Settings ────────────────────────────────────────
        composable(
            Screen.Settings.route,
            deepLinks = listOf(navDeepLink { uriPattern = Screen.Settings.deepLink })
        ) {
            com.storagemanager.ui.settings.SettingsScreen(navController = navController)
        }

        // ── Trash / Recycle Bin ─────────────────────────────
        composable(
            Screen.Trash.route,
            deepLinks = listOf(navDeepLink { uriPattern = Screen.Trash.deepLink })
        ) {
            com.storagemanager.ui.trash.TrashScreen(navController = navController)
        }

        // ── Storage Treemap ─────────────────────────────────
        composable(
            Screen.Treemap.route,
            deepLinks = listOf(navDeepLink { uriPattern = Screen.Treemap.deepLink })
        ) {
            com.storagemanager.ui.treemap.StorageTreemapScreen(navController = navController)
        }

        // ── Downloads & Duplicates ──────────────────────────
        composable(
            Screen.Downloads.route,
            deepLinks = listOf(navDeepLink { uriPattern = Screen.Downloads.deepLink })
        ) {
            com.storagemanager.ui.downloads.DownloadsScreen(navController = navController)
        }

        // ── System Junk & Empty Folders ─────────────────────
        composable(
            Screen.SystemJunk.route,
            deepLinks = listOf(navDeepLink { uriPattern = Screen.SystemJunk.deepLink })
        ) {
            com.storagemanager.ui.junk.SystemJunkScreen(navController = navController)
        }

        // ── Paywall / Pro ───────────────────────────────────
        composable(
            Screen.Paywall.route,
            deepLinks = listOf(navDeepLink { uriPattern = Screen.Paywall.deepLink })
        ) {
            com.storagemanager.ui.paywall.PaywallScreen(navController = navController)
        }
    }
}
