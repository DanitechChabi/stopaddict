package com.stopaddict.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.stopaddict.app.ui.auth.AuthScreen
import com.stopaddict.app.ui.dashboard.DashboardScreen
import com.stopaddict.app.ui.pairing.PairingScreen

object Routes {
    const val WELCOME = "welcome"
    const val AUTH = "auth"
    const val GATE = "gate"
    const val PAIRING = "pairing"
    const val DASHBOARD = "dashboard"
}

@Composable
fun UserNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.WELCOME) {
        composable(Routes.WELCOME) {
            WelcomeScreen(onStart = { nav.navigate(Routes.AUTH) })
        }
        composable(Routes.AUTH) {
            AuthScreen(onAuthenticated = {
                nav.navigate(Routes.GATE) { popUpTo(Routes.WELCOME) { inclusive = true } }
            })
        }
        composable(Routes.GATE) {
            GateScreen(
                onHasDevice = { nav.navigate(Routes.DASHBOARD) { popUpTo(Routes.GATE) { inclusive = true } } },
                onNoDevice = { nav.navigate(Routes.PAIRING) { popUpTo(Routes.GATE) { inclusive = true } } },
            )
        }
        composable(Routes.PAIRING) {
            PairingScreen(onPaired = {
                nav.navigate(Routes.DASHBOARD) { popUpTo(Routes.PAIRING) { inclusive = true } }
            })
        }
        composable(Routes.DASHBOARD) { DashboardScreen() }
    }
}
