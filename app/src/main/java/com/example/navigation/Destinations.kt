package com.example.navigation

/**
 * Navigation destination configurations representing the entire application map.
 */
sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Login : Screen("login")
    object Register : Screen("register")
    object Dashboard : Screen("dashboard")
    object Members : Screen("members")
    object Loans : Screen("loans")
    object Contributions : Screen("contributions")
    object Payments : Screen("payments")
    object Reports : Screen("reports")
    object Admin : Screen("admin")
    object Treasury : Screen("treasury")
    object Notifications : Screen("notifications")
    object Settings : Screen("settings")
    object Activities : Screen("activities")
    object TreasuryActivities : Screen("treasury_activities")
}
