package com.meshcart

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.meshcart.ui.screens.*
import com.meshcart.ui.theme.MeshCartTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MeshCartTheme { MeshCartNav(intent) } }
    }
}

@Composable
private fun MeshCartNav(intent: Intent) {
    val nav = rememberNavController()

    NavHost(nav, startDestination = "onboarding") {

        composable("onboarding") {
            OnboardingScreen(onComplete = {
                nav.navigate("lists") { popUpTo("onboarding") { inclusive = true } }
            })
        }

        composable("lists") {
            ListsScreen(
                onListClick = { nav.navigate("list/${it.value}") },
                onSettings  = { nav.navigate("revoke") }
            )
        }

        composable(
            route = "list/{listId}",
            arguments = listOf(navArgument("listId") { type = NavType.StringType })
        ) { back ->
            val listId = back.arguments?.getString("listId") ?: return@composable
            ListDetailScreen(
                onBack         = { nav.popBackStack() },
                onShowInviteQr = { nav.navigate("invite/$listId") }
            )
        }

        composable(
            route = "invite/{listId}",
            arguments = listOf(navArgument("listId") { type = NavType.StringType })
        ) { back ->
            val listId = back.arguments?.getString("listId") ?: return@composable
            InviteScreen(
                onBack      = { nav.popBackStack() },
                onConnected = {
                    nav.navigate("list/$listId") {
                        popUpTo("invite/$listId") { inclusive = true }
                    }
                }
            )
        }

        composable("revoke") {
            RevokeIdentityScreen(
                onBack    = { nav.popBackStack() },
                onRevoked = {
                    nav.navigate("onboarding") { popUpTo(0) { inclusive = true } }
                }
            )
        }
    }
}