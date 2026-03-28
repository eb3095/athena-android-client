package com.athena.client.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.athena.client.audio.AudioPlayer
import com.athena.client.speech.SpeechRecognizerManager
import com.athena.client.ui.components.MicButton
import com.athena.client.ui.components.ResponseCard
import com.athena.client.ui.components.ThinkingIndicator
import com.athena.client.ui.components.VoiceSelector
import com.athena.client.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    val view = LocalView.current
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    
    // Keep screen on while loading or playing audio
    LaunchedEffect(uiState.isLoading, uiState.playingResponseId) {
        view.keepScreenOn = uiState.isLoading || uiState.playingResponseId != null
    }
    
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }
    
    val audioPlayer = remember { AudioPlayer() }
    
    var speechAvailable by remember { mutableStateOf(true) }
    
    val speechRecognizer = remember {
        SpeechRecognizerManager(
            context = context,
            onResult = { result ->
                viewModel.sendPrompt(result)
            },
            onPartialResult = {},
            onError = { error ->
                viewModel.setListening(false)
            },
            onListeningStateChanged = { listening ->
                viewModel.setListening(listening)
            }
        ).also { speechAvailable = it.initialize() }
    }
    
    LaunchedEffect(speechAvailable) {
        if (!speechAvailable) {
            snackbarHostState.showSnackbar("Speech recognition unavailable on this device")
        }
    }
    
    DisposableEffect(Unit) {
        onDispose {
            speechRecognizer.destroy()
            audioPlayer.release()
        }
    }
    
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }
    
    // Track previous response count for auto-play
    var previousResponseCount by remember { mutableStateOf(0) }
    
    LaunchedEffect(uiState.responses.size) {
        if (uiState.responses.isNotEmpty()) {
            listState.animateScrollToItem(uiState.responses.size - 1)
            
            // Auto-play audio when a new response arrives
            if (uiState.responses.size > previousResponseCount) {
                val latestResponse = uiState.responses.last()
                latestResponse.audioBase64?.let { audio ->
                    audioPlayer.stop()
                    viewModel.setPlayingResponse(latestResponse.id)
                    audioPlayer.play(
                        base64Audio = audio,
                        onCompletion = {
                            viewModel.setPlayingResponse(null)
                        },
                        onError = {
                            viewModel.setPlayingResponse(null)
                        }
                    )
                }
            }
        }
        previousResponseCount = uiState.responses.size
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = "Athena",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 100.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    items = uiState.responses,
                    key = { it.id }
                ) { response ->
                    ResponseCard(
                        text = response.text,
                        hasAudio = response.audioBase64 != null,
                        isPlaying = uiState.playingResponseId == response.id,
                        onPlayClick = {
                            if (uiState.playingResponseId == response.id) {
                                audioPlayer.stop()
                                viewModel.setPlayingResponse(null)
                            } else {
                                response.audioBase64?.let { audio ->
                                    audioPlayer.stop()
                                    viewModel.setPlayingResponse(response.id)
                                    audioPlayer.play(
                                        base64Audio = audio,
                                        onCompletion = {
                                            viewModel.setPlayingResponse(null)
                                        },
                                        onError = {
                                            viewModel.setPlayingResponse(null)
                                        }
                                    )
                                }
                            }
                        }
                    )
                }
                
                if (uiState.isLoading) {
                    item {
                        ThinkingIndicator(
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MicButton(
                    isListening = uiState.isListening,
                    isProcessing = uiState.isLoading || !speechAvailable,
                    onClick = {
                        if (!speechAvailable) return@MicButton
                        
                        if (!hasPermission) {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            return@MicButton
                        }
                        
                        if (uiState.isListening) {
                            speechRecognizer.stopListening()
                        } else if (!uiState.isLoading) {
                            speechRecognizer.startListening()
                        }
                    }
                )
                Spacer(modifier = Modifier.width(16.dp))
                VoiceSelector(
                    selectedVoice = uiState.selectedVoice,
                    voices = uiState.voices,
                    isLoading = uiState.isLoadingVoices,
                    onExpand = { viewModel.fetchVoices() },
                    onVoiceSelected = { viewModel.setSelectedVoice(it) }
                )
            }
        }
    }
}
