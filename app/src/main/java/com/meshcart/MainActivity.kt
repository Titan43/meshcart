package com.meshcart

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.meshcart.ui.screens.*
import com.meshcart.ui.theme.MeshCartTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val initialDeepLink = MutableStateFlow<String?>(null)
    private val newIntentDeepLink = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        initialDeepLink.value = intent?.data?.toString()?.takeIf { it.startsWith("meshcart://sdp") }
        setContent {
            val initial by initialDeepLink.collectAsStateWithLifecycle()
            val incoming by newIntentDeepLink.collectAsStateWithLifecycle()
            MeshCartTheme { MeshCartNav(initial, incoming) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        newIntentDeepLink.value = intent.data?.toString()?.takeIf { it.startsWith("meshcart://sdp") }
    }
}

@Composable
private fun MeshCartNav(initialDeepLink: String?, incomingDeepLink: String?) {
    val startDest = if (initialDeepLink != null) "lists?join=${Uri.encode(initialDeepLink)}" else "onboarding"
    val nav = rememberNavController()

    LaunchedEffect(incomingDeepLink) {
        val uri = incomingDeepLink ?: return@LaunchedEffect
        nav.navigate("lists?join=${Uri.encode(uri)}") {
            popUpTo("lists") { inclusive = false }
        }
    }

    NavHost(nav, startDestination = startDest) {

        composable("onboarding") {
            OnboardingScreen(onComplete = {
                nav.navigate("lists") { popUpTo("onboarding") { inclusive = true } }
            })
        }

        composable(
            route = "lists?join={joinUri}",
            arguments = listOf(navArgument("joinUri") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            })
        ) { back ->
            val joinUri = back.arguments?.getString("joinUri")
            ListsScreen(
                onListClick = { nav.navigate("list/${it.value}") },
                onSettings  = { nav.navigate("revoke") },
                autoJoinUri = joinUri
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