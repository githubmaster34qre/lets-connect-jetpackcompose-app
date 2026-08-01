package com.example.letsconnect.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.letsconnect.screens.Chat
import com.example.letsconnect.screens.Login
import com.example.letsconnect.screens.Messages
import com.letsconnect.ui.screens.SignUp

@Composable
fun NavGraph() {
    // 1. Create the navigation controller tracking the backstack
    val navController = rememberNavController()

    // 2. Define the NavHost container and standard start destination
    NavHost(
        navController = navController,
        startDestination = "login_screen"
    ) {
        // 3. Map string routes to their corresponding screen composables
        composable("login_screen") {
            Login(
                onNavigateToSignUp = { navController.navigate("signup_screen") }, 
                onLoginSuccess = { navController.navigate("messages_screen") }
            )
        }
        composable("signup_screen") {
            SignUp(
                onNavigateToLogin = { navController.popBackStack() },
                onSignUpSuccess = { navController.navigate("messages_screen") }
            )
        }
        composable("messages_screen") {
            Messages(onConversationClick = { conversation ->
                navController.navigate("chat_screen/${conversation.id}/${conversation.userId}/${conversation.name}")
            })
        }
        composable("chat_screen/{conversationId}/{receiverId}/{receiverName}") { backStackEntry ->
            val conversationId = backStackEntry.arguments?.getString("conversationId")?.toIntOrNull() ?: 0
            val receiverId = backStackEntry.arguments?.getString("receiverId")?.toUIntOrNull() ?: 2u
            val receiverName = backStackEntry.arguments?.getString("receiverName") ?: "Unknown"
            Chat(
                conversationId = conversationId,
                receiverId = receiverId,
                receiverName = receiverName,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
