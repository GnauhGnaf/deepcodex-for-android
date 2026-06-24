package com.example.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.app.ui.chat.ChatScreen
import com.example.app.ui.history.HistoryScreen
import com.example.app.ui.settings.SettingsScreen
import com.example.app.ui.theme.DeepSeekCodexTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DeepSeekCodexTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "chat") {
        composable("chat") {
            ChatScreen(
                onNavigateToHistory = { navController.navigate("history") },
                onNavigateToSettings = { navController.navigate("settings") }
            )
        }
        composable("history") {
            val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as App
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
