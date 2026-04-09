package com.athena.client.ui.navigation

sealed class NavRoutes(val route: String) {
    object Home : NavRoutes("home")
    object Conversation : NavRoutes("conversation/{conversationId}") {
        fun createRoute(conversationId: String) = "conversation/$conversationId"
    }
    object Transcript : NavRoutes("transcript/{transcriptId}") {
        fun createRoute(transcriptId: String) = "transcript/$transcriptId"
    }
}
