package com.unishuttle.app.presentation.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.unishuttle.app.presentation.ui.screen.driver.DriverHomeScreen
import com.unishuttle.app.presentation.ui.screen.notifications.NotificationCenterScreen
import com.unishuttle.app.presentation.ui.screen.student.StudentMapScreen
import com.unishuttle.app.presentation.viewmodel.AuthUiState
import com.unishuttle.app.presentation.viewmodel.AuthViewModel

private const val UNSUPPORTED_ROLE_MESSAGE = "This account type isn't available yet."

/**
 * Root navigation graph with the auth guard.
 *
 * - Loading → plain theme-background view (no flash of any screen).
 * - SignedOut → login, back stack cleared.
 * - SignedIn(student) → student graph only.
 * - SignedIn(driver) → driver graph only.
 * - Any other role → signed out with an error shown on login.
 *
 * The manifest declares no deep links, so the guard is the only way the role
 * destinations can be reached — students cannot get to the driver graph and
 * drivers cannot get to the student graph.
 */
@Composable
fun UniShuttleNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    authViewModel: AuthViewModel = hiltViewModel(),
) {
    val authState by authViewModel.uiState.collectAsStateWithLifecycle()

    // Show nothing but the themed background until Firebase resolves the session.
    if (authState is AuthUiState.Loading) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        return
    }

    val startDestination = when (val state = authState) {
        is AuthUiState.SignedIn -> when (state.role) {
            UserRole.STUDENT -> NavRoute.StudentMap.route
            UserRole.DRIVER -> NavRoute.DriverDashboard.route
            else -> NavRoute.Login.route
        }
        else -> NavRoute.Login.route
    }

    // Guard: react to every auth state change and clear the back stack on each
    // transition so a role change or sign-out can never leave stale history.
    LaunchedEffect(authState) {
        val target = when (val state = authState) {
            is AuthUiState.SignedIn -> when (state.role) {
                UserRole.STUDENT -> NavRoute.StudentMap.route
                UserRole.DRIVER -> NavRoute.DriverDashboard.route
                // TODO(later steps): admin role setup and routing.
                else -> {
                    authViewModel.signOutWithError(UNSUPPORTED_ROLE_MESSAGE)
                    null
                }
            }
            is AuthUiState.SignedOut -> NavRoute.Login.route
            AuthUiState.Loading -> null
        }
        if (target != null && navController.currentDestination?.route != target) {
            navController.navigate(target) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
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
                onLogout = { authViewModel.logout() },
            )
        }

        // ── Driver (Step 1 placeholder; Step 4 replaces it) ──
        composable(NavRoute.DriverDashboard.route) {
            DriverHomeScreen(
                onLogout = { authViewModel.logout() },
            )
        }

        // ── Admin (unreachable in Step 1 — the guard signs unknown roles out) ──
        composable(NavRoute.AdminPanel.route) {
            AdminPanelScreen(
                onLogout = { authViewModel.logout() },
            )
        }

        // ── Shared ──
        composable(NavRoute.NotificationCenter.route) {
            NotificationCenterScreen(onBack = { navController.popBackStack() })
        }
    }
}
