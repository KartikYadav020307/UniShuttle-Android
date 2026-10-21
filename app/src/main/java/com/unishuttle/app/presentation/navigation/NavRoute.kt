package com.unishuttle.app.presentation.navigation

sealed class NavRoute(val route: String) {
    // Auth
    data object Login : NavRoute("login")
    data object Register : NavRoute("register")

    // Role home screens
    data object StudentMap : NavRoute("student_map")
    data object DriverDashboard : NavRoute("driver_dashboard")
    data object AdminPanel : NavRoute("admin_panel")

    // Shared
    data object NotificationCenter : NavRoute("notification_center")

    // Legacy alias kept for back-compat with Phase 1–3 code
    data object Map : NavRoute("student_map")
}
