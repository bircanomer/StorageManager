package com.storagemanager.ui.navigation

/**
 * Uygulama içi navigasyon rotaları.
 *
 * Her rota aynı zamanda `storagemanager://screen/<route>` deep link'i ile doğrudan
 * açılabilir. Bu, bildirimlerden derin bağlantı vermeyi ve her ekranı tek tek
 * (ör. yerelleştirme taraması sırasında) doğrudan açabilmeyi mümkün kılar.
 */
sealed class Screen(val route: String) {

    /** Bu ekranın deep link deseni. */
    val deepLink: String get() = "$DEEP_LINK_PREFIX$route"

    data object Onboarding : Screen("onboarding")
    data object Dashboard : Screen("dashboard")
    data object Photos : Screen("photos")
    data object Apps : Screen("apps")
    data object Cache : Screen("cache")
    data object Files : Screen("files")
    data object Settings : Screen("settings")
    data object Trash : Screen("trash")
    data object Treemap : Screen("treemap")
    data object Downloads : Screen("downloads")
    data object SystemJunk : Screen("system_junk")
    data object Paywall : Screen("paywall")

    companion object {
        const val DEEP_LINK_PREFIX = "storagemanager://screen/"
    }
}
