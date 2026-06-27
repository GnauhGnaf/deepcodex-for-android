package com.example.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.app.ui.chat.ChatScreen
import com.example.app.ui.history.HistoryScreen
import com.example.app.ui.settings.SettingsScreen
import com.example.app.ui.setup.SetupScreen
import com.example.app.ui.theme.CodeepsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CodeepsTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as App
    val setupComplete by app.container.settingsRepository.setupComplete.collectAsState(initial = false)
    val navController = rememberNavController()

    // If setup not done, show setup wizard (outside NavHost — full screen takeover)
    if (!setupComplete) {
        SetupScreen(onSetupComplete = {
            // Force recomposition — setupComplete flow will update
        })
        return
    }

    NavHost(navController = navController, startDestination = "chat") {
        composable("chat") {
            ChatScreen(
                onNavigateToHistory = { navController.navigate("history") },
                onNavigateToSettings = { navController.navigate("settings") }
            )
        }
        composable("history") {
            HistoryScreen(
                onBack = { navController.popBackStack() },
                onConversationSelected = { id: String ->
                    app.container.requestLoadConversation(id)
                    navController.popBackStack()
                },
                onNewConversation = {
                    app.container.requestNewConversation()
                    navController.popBackStack()
                }
            )
        }
        composable("settings") {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
