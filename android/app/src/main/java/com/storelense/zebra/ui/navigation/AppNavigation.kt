package com.storelense.zebra.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.storelense.zebra.ui.dashboard.DashboardScreen
import com.storelense.zebra.ui.login.LoginScreen
import com.storelense.zebra.ui.login.LoginViewModel
import com.storelense.zebra.ui.refill.RefillDetailScreen
import com.storelense.zebra.ui.refill.RefillListScreen
import com.storelense.zebra.ui.soh.SohListScreen
import com.storelense.zebra.ui.soh.SohScanScreen

sealed class Screen(val route: String) {
    data object Login        : Screen("login")
    data object Dashboard    : Screen("dashboard")
    data object SohList      : Screen("soh")
    data object SohScan      : Screen("soh/{sessionId}") {
        fun route(id: String) = "soh/$id"
    }
    data object RefillList   : Screen("refill")
    data object RefillDetail : Screen("refill/{taskId}") {
        fun route(id: String) = "refill/$id"
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val loginVm: LoginViewModel = hiltViewModel()
    val isAuthed by loginVm.isAuthed.collectAsStateWithLifecycle()

    val start = if (isAuthed) Screen.Dashboard.route else Screen.Login.route

    NavHost(navController = navController, startDestination = start) {

        composable(Screen.Login.route) {
            LoginScreen(
                viewModel = hiltViewModel(),
                onLoginSuccess = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Dashboard.route) {
            DashboardScreen(
                viewModel   = hiltViewModel(),
                onGoSoh     = { navController.navigate(Screen.SohList.route) },
                onGoRefill  = { navController.navigate(Screen.RefillList.route) },
                onLogout    = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.SohList.route) {
            SohListScreen(
                viewModel      = hiltViewModel(),
                onSessionClick = { id -> navController.navigate(Screen.SohScan.route(id)) },
                onBack         = { navController.popBackStack() }
            )
        }

        composable(
            route     = Screen.SohScan.route,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
        ) {
            SohScanScreen(
                viewModel = hiltViewModel(),
                onDone    = { navController.popBackStack() }
            )
        }

        composable(Screen.RefillList.route) {
            RefillListScreen(
                viewModel    = hiltViewModel(),
                onTaskClick  = { id -> navController.navigate(Screen.RefillDetail.route(id)) },
                onBack       = { navController.popBackStack() }
            )
        }

        composable(
            route     = Screen.RefillDetail.route,
            arguments = listOf(navArgument("taskId") { type = NavType.StringType })
        ) {
            RefillDetailScreen(
                viewModel = hiltViewModel(),
                onBack    = { navController.popBackStack() }
            )
        }
    }
}
