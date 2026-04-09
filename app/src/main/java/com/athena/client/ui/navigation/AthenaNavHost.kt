package com.athena.client.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.athena.client.data.local.ConversationType
import com.athena.client.ui.ConversationScreen
import com.athena.client.ui.HomeScreen
import com.athena.client.ui.TranscriptScreen
import com.athena.client.ui.components.AppNavigationDrawer
import com.athena.client.viewmodel.AppViewModel
import kotlinx.coroutines.launch

@Composable
fun AthenaNavHost(
    navController: NavHostController = rememberNavController(),
    appViewModel: AppViewModel = viewModel()
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val conversations by appViewModel.conversations.collectAsState()
    val deleteConfirmation by appViewModel.showDeleteConfirmation.collectAsState()
    val useStreamingMode by appViewModel.useStreamingMode.collectAsState()
    
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    
    val currentConversationId = when {
        currentRoute?.startsWith("conversation/") == true -> {
            navBackStackEntry?.arguments?.getString("conversationId")
        }
        currentRoute?.startsWith("transcript/") == true -> {
            navBackStackEntry?.arguments?.getString("transcriptId")
        }
        else -> null
    }
    
    var showDeleteAllConfirmation by remember { mutableStateOf(false) }
    
    if (deleteConfirmation != null) {
        AlertDialog(
            onDismissRequest = { appViewModel.dismissDeleteConfirmation() },
            title = { Text("Delete") },
            text = { 
                Text("Are you sure you want to delete \"${deleteConfirmation?.title}\"?") 
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteConfirmation?.let { conversation ->
                            appViewModel.deleteConversation(conversation)
                            if (currentConversationId == conversation.id) {
                                appViewModel.setCurrentConversation(null)
                                navController.navigate(NavRoutes.Home.route) {
                                    popUpTo(0) { inclusive = true }
                                }
                            }
                        }
                        appViewModel.dismissDeleteConfirmation()
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { appViewModel.dismissDeleteConfirmation() }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    if (showDeleteAllConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteAllConfirmation = false },
            title = { Text("Delete All") },
            text = { Text("Are you sure you want to delete all conversations and transcripts?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        appViewModel.deleteAllConversations()
                        appViewModel.setCurrentConversation(null)
                        showDeleteAllConfirmation = false
                        navController.navigate(NavRoutes.Home.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                ) {
                    Text("Delete All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            AppNavigationDrawer(
                conversations = conversations,
                currentConversationId = currentConversationId,
                onNewConversation = {
                    scope.launch {
                        drawerState.close()
                        val id = appViewModel.createConversation()
                        navController.navigate(NavRoutes.Conversation.createRoute(id))
                    }
                },
                onNewTranscript = {
                    scope.launch {
                        drawerState.close()
                        val id = appViewModel.createTranscript()
                        navController.navigate(NavRoutes.Transcript.createRoute(id))
                    }
                },
                onSelectConversation = { conversation ->
                    scope.launch {
                        drawerState.close()
                        appViewModel.setCurrentConversation(conversation.id)
                        when (conversation.type) {
                            ConversationType.CONVERSATION -> {
                                navController.navigate(
                                    NavRoutes.Conversation.createRoute(conversation.id)
                                )
                            }
                            ConversationType.TRANSCRIPT -> {
                                navController.navigate(
                                    NavRoutes.Transcript.createRoute(conversation.id)
                                )
                            }
                        }
                    }
                },
                onDeleteConversation = { conversation ->
                    appViewModel.requestDeleteConversation(conversation)
                },
                onDeleteAll = {
                    showDeleteAllConfirmation = true
                }
            )
        }
    ) {
        NavHost(
            navController = navController,
            startDestination = NavRoutes.Home.route,
            modifier = Modifier.fillMaxSize()
        ) {
            composable(NavRoutes.Home.route) {
                HomeScreen(
                    onNewConversation = {
                        scope.launch {
                            val id = appViewModel.createConversation()
                            navController.navigate(NavRoutes.Conversation.createRoute(id)) {
                                popUpTo(NavRoutes.Home.route) { inclusive = true }
                            }
                        }
                    },
                    onNewTranscript = {
                        scope.launch {
                            val id = appViewModel.createTranscript()
                            navController.navigate(NavRoutes.Transcript.createRoute(id)) {
                                popUpTo(NavRoutes.Home.route) { inclusive = true }
                            }
                        }
                    },
                    onMenuClick = {
                        scope.launch { drawerState.open() }
                    },
                    useStreamingMode = useStreamingMode,
                    onStreamingModeChanged = { appViewModel.setStreamingMode(it) }
                )
            }
            
            composable(
                route = NavRoutes.Conversation.route,
                arguments = listOf(
                    navArgument("conversationId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val conversationId = backStackEntry.arguments?.getString("conversationId") ?: return@composable
                ConversationScreen(
                    conversationId = conversationId,
                    onMenuClick = {
                        scope.launch { drawerState.open() }
                    }
                )
            }
            
            composable(
                route = NavRoutes.Transcript.route,
                arguments = listOf(
                    navArgument("transcriptId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val transcriptId = backStackEntry.arguments?.getString("transcriptId") ?: return@composable
                TranscriptScreen(
                    transcriptId = transcriptId,
                    onMenuClick = {
                        scope.launch { drawerState.open() }
                    }
                )
            }
        }
    }
}
