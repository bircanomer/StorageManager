package com.storagemanager.ui.navigation

/**
 * Uygulama içi navigasyon rotaları.
 */
sealed class Screen(val route: String) {
    data object Onboarding : Screen("onboarding")
    data object Dashboard : Screen("dashboard")
    data object Photos : Screen("photos")
    data object Apps : Screen("apps")
    data object Cache : Screen("cache")
    data object Files : Screen("files")
    data object Settings : Screen("settings")
}
