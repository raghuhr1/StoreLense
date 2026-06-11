package com.storelense.c66.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.storelense.c66.ui.gate.GateScanScreen
import com.storelense.c66.ui.login.LoginScreen

private object Routes {
    const val LOGIN = "login"
    const val GATE  = "gate"
}

@Composable
fun AppNavigation(startLoggedIn: Boolean) {
    val nav = rememberNavController()
    val start = if (startLoggedIn) Routes.GATE else Routes.LOGIN

    NavHost(navController = nav, startDestination = start) {

        composable(Routes.LOGIN) {
            LoginScreen(onLoginSuccess = {
                nav.navigate(Routes.GATE) {
                    popUpTo(Routes.LOGIN) { inclusive = true }
                }
            })
        }

        composable(Routes.GATE) {
            GateScanScreen(onLogout = {
                nav.navigate(Routes.LOGIN) {
                    popUpTo(0) { inclusive = true }
                }
            })
        }
    }
}
