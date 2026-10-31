package com.stopaddict.admin.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.stopaddict.admin.ui.auth.AuthScreen
import com.stopaddict.admin.ui.dashboard.DashboardScreen
import com.stopaddict.admin.ui.device.DeviceDetailScreen
import com.stopaddict.admin.ui.invitation.InvitationScreen
import com.stopaddict.admin.ui.releases.ReleasesScreen

object Routes {
    const val AUTH = "auth"
    const val DASHBOARD = "dashboard"
    const val INVITATION = "invitation"
    const val RELEASES = "releases"
    const val DEVICE = "device"
}

@Composable
fun AdminNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.AUTH) {
        composable(Routes.AUTH) {
            AuthScreen(onAuthenticated = {
                nav.navigate(Routes.DASHBOARD) {
                    popUpTo(Routes.AUTH) { inclusive = true }
                }
            })
        }
        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onNewInvitation = { nav.navigate(Routes.INVITATION) },
                onOpenDevice = { id -> nav.navigate("${Routes.DEVICE}/$id") },
                onOpenReleases = { nav.navigate(Routes.RELEASES) },
            )
        }
        composable(Routes.INVITATION) { InvitationScreen() }
        composable(Routes.RELEASES) { ReleasesScreen() }
        composable("${Routes.DEVICE}/{deviceId}") { entry ->
            DeviceDetailScreen(deviceId = entry.arguments?.getString("deviceId").orEmpty())
        }
    }
}
