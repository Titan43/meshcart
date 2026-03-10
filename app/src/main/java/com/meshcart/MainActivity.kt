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
import kotlinx.coroutines.flow.MutableSharedFlow
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var deepLinkFlow: MutableSharedFlow<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        intent?.data?.toString()?.takeIf { it.startsWith("meshcart://join") }
            ?.let { deepLinkFlow.tryEmit(it) }
        setContent { MeshCartTheme { MeshCartNav() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.toString()?.takeIf { it.startsWith("meshcart://join") }
            ?.let { deepLinkFlow.tryEmit(it) }
    }
}

@Composable
private fun MeshCartNav() {
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
                onBack      = { nav.popBackStack() },
                onShareList = { nav.navigate("share/$listId") }
            )
        }

        composable(
            route = "share/{listId}",
            arguments = listOf(navArgument("listId") { type = NavType.StringType })
        ) {
            ShareScreen(onBack = { nav.popBackStack() })
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