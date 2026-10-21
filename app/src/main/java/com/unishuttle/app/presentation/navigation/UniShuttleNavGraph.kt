package com.unishuttle.app.presentation.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.unishuttle.app.domain.model.UserRole
import com.unishuttle.app.presentation.ui.screen.admin.AdminPanelScreen
import com.unishuttle.app.presentation.ui.screen.auth.LoginScreen
import com.unishuttle.app.presentation.ui.screen.auth.RegisterScreen
import com.unishuttle.app.presentation.ui.screen.driver.DriverDashboardScreen
import com.unishuttle.app.presentation.ui.screen.notifications.NotificationCenterScreen
import com.unishuttle.app.presentation.ui.screen.student.StudentMapScreen
import com.unishuttle.app.presentation.viewmodel.AuthViewModel

/**
 * Root navigation graph.
 *
 * Auth gate logic:
 * - While auth state is being resolved → show a loading spinner (no flash of wrong screen).
 * - Not signed in → start on Login.
 * - Signed in as STUDENT → start on StudentMap.
 * - Signed in as DRIVER  → start on DriverDashboard.
 * - Signed in as ADMIN   → start on AdminPanel.
 */
@Composable
fun UniShuttleNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    authViewModel: AuthViewModel = hiltViewModel(),
) {
    val authState by authViewModel.uiState.collectAsStateWithLifecycle()

    // Show a spinner until Firebase resolves the auth state on cold launch
    if (!authState.isAuthResolved) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val startDestination = when (authState.currentUser?.role) {
        UserRole.DRIVER -> NavRoute.DriverDashboard.route
        UserRole.ADMIN -> NavRoute.AdminPanel.route
        UserRole.STUDENT -> NavRoute.StudentMap.route
        null -> NavRoute.Login.route
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        // ── Auth ──
        composable(NavRoute.Login.route) {
            LoginScreen()
        }

        composable(NavRoute.Register.route) {
            RegisterScreen(
                onBack = { navController.popBackStack() },
            )
        }

        // ── Student ──
        composable(NavRoute.StudentMap.route) {
            StudentMapScreen(
                onNavigateToNotifications = {
                    navController.navigate(NavRoute.NotificationCenter.route)
                },
                onLogout = {
                    authViewModel.logout()
                    navController.navigate(NavRoute.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }

        // ── Driver ──
        composable(NavRoute.DriverDashboard.route) {
            DriverDashboardScreen(
                onLogout = {
                    authViewModel.logout()
                    navController.navigate(NavRoute.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }

        // ── Admin ──
        composable(NavRoute.AdminPanel.route) {
            AdminPanelScreen(
                onLogout = {
                    authViewModel.logout()
                    navController.navigate(NavRoute.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }

        // ── Shared ──
        composable(NavRoute.NotificationCenter.route) {
            NotificationCenterScreen(onBack = { navController.popBackStack() })
        }
    }
}
