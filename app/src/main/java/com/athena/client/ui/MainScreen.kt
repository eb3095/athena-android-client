package com.athena.client.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.ToneGenerator
import android.media.AudioManager
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.athena.client.audio.AudioPlayer
import com.athena.client.speech.SpeechRecognizerManager
import com.athena.client.ui.components.MicButton
import com.athena.client.ui.components.MimicButton
import com.athena.client.ui.components.ResponseCard
import com.athena.client.ui.components.SettingsDialog
import com.athena.client.ui.components.SpeakButton
import com.athena.client.ui.components.SpeakDialog
import com.athena.client.ui.components.ThinkingIndicator
import com.athena.client.ui.components.VoiceSelector
import com.athena.client.viewmodel.MainViewModel
import com.athena.client.viewmodel.ResponseType
import kotlinx.coroutines.launch
import java.io.File

private enum class ListenMode {
    PROMPT,
    MIMIC
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    val view = LocalView.current
    val uiState by viewModel.uiState.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // Keep screen on while loading, polling, or playing audio
    val showProgress = uiState.isLoading || uiState.isPolling
    LaunchedEffect(showProgress, uiState.playingResponseId) {
        view.keepScreenOn = showProgress || uiState.playingResponseId != null
    }
    
    // Auto-scroll to bottom when app resumes
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && uiState.responses.isNotEmpty()) {
                coroutineScope.launch {
                    listState.animateScrollToItem(uiState.responses.size - 1)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    // Share audio helper function
    fun shareAudio(audioBase64: String, voice: String?) {
        try {
            val audioBytes = Base64.decode(audioBase64, Base64.DEFAULT)
            val fileName = "athena_audio_${System.currentTimeMillis()}.mp3"
            val cacheDir = File(context.cacheDir, "shared_audio")
            cacheDir.mkdirs()
            val audioFile = File(cacheDir, fileName)
            audioFile.writeBytes(audioBytes)
            
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                audioFile
            )
            
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/mpeg"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Athena Audio" + (voice?.let { " ($it)" } ?: ""))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share audio"))
        } catch (e: Exception) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Failed to share audio: ${e.message}")
            }
        }
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
    var showSpeakDialog by remember { mutableStateOf(false) }
    var listenMode by remember { mutableStateOf(ListenMode.PROMPT) }
    var isMimicListening by remember { mutableStateOf(false) }
    
    val speechRecognizer = remember {
        SpeechRecognizerManager(
            context = context,
            onResult = { result ->
                when (listenMode) {
                    ListenMode.PROMPT -> viewModel.sendPrompt(result)
                    ListenMode.MIMIC -> viewModel.speakText(result)
                }
                isMimicListening = false
            },
            onPartialResult = {},
            onError = { error ->
                viewModel.setListening(false)
                isMimicListening = false
            },
            onListeningStateChanged = { listening ->
                viewModel.setListening(listening)
                if (!listening) isMimicListening = false
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
    
    
    // Track previous response count for auto-play
    var previousResponseCount by remember { mutableStateOf(0) }
    
    // Track streaming audio playback
    var currentStreamingResponseId by remember { mutableStateOf<String?>(null) }
    var nextSentenceToPlay by remember { mutableStateOf(0) }
    var isPlayingStreamingSentence by remember { mutableStateOf(false) }
    
    // Handle streaming audio playback
    LaunchedEffect(uiState.responses, isPlayingStreamingSentence) {
        val streamingResponse = uiState.responses.find { it.isStreaming && !it.streamingComplete }
            ?: uiState.responses.find { it.id == currentStreamingResponseId }
        
        if (streamingResponse != null) {
            if (currentStreamingResponseId != streamingResponse.id) {
                currentStreamingResponseId = streamingResponse.id
                nextSentenceToPlay = 0
            }
            
            if (!isPlayingStreamingSentence) {
                val completedSentences = streamingResponse.sentenceAudios.filter { it.status == "completed" && it.audio != null }
                val nextSentence = completedSentences.find { it.index == nextSentenceToPlay }
                
                if (nextSentence != null && nextSentence.audio != null) {
                    isPlayingStreamingSentence = true
                    viewModel.setPlayingResponse(streamingResponse.id)
                    audioPlayer.play(
                        base64Audio = nextSentence.audio,
                        onCompletion = {
                            nextSentenceToPlay++
                            isPlayingStreamingSentence = false
                            if (streamingResponse.streamingComplete && nextSentenceToPlay >= streamingResponse.sentenceAudios.size) {
                                viewModel.setPlayingResponse(null)
                                currentStreamingResponseId = null
                            }
                        },
                        onError = {
                            nextSentenceToPlay++
                            isPlayingStreamingSentence = false
                            viewModel.setPlayingResponse(null)
                        }
                    )
                }
            }
        }
    }
    
    LaunchedEffect(uiState.responses.size) {
        if (uiState.responses.isNotEmpty()) {
            listState.animateScrollToItem(uiState.responses.size - 1)
            
            // Auto-play audio when a new non-streaming response arrives
            if (uiState.responses.size > previousResponseCount) {
                val latestResponse = uiState.responses.last()
                if (!latestResponse.isStreaming) {
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
        }
        previousResponseCount = uiState.responses.size
    }

    var showSettingsDialog by remember { mutableStateOf(false) }

    if (showSpeakDialog) {
        SpeakDialog(
            onDismiss = { showSpeakDialog = false },
            onConfirm = { text -> viewModel.speakText(text) }
        )
    }

    if (showSettingsDialog) {
        SettingsDialog(
            useStreamingMode = uiState.useStreamingMode,
            onStreamingModeChanged = { viewModel.setStreamingMode(it) },
            onDismiss = { showSettingsDialog = false }
        )
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
                actions = {
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
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
                    val hasAnyAudio = if (response.isStreaming) {
                        response.sentenceAudios.any { it.audio != null }
                    } else {
                        response.audioBase64 != null
                    }
                    
                    val showShareButton = if (response.isStreaming) {
                        response.streamingComplete && response.combinedAudio != null
                    } else {
                        response.audioBase64 != null
                    }
                    
                    ResponseCard(
                        text = response.text,
                        hasAudio = hasAnyAudio,
                        isPlaying = uiState.playingResponseId == response.id,
                        isTranscript = response.type == ResponseType.TRANSCRIPT,
                        voice = response.voice,
                        onPlayClick = {
                            if (uiState.playingResponseId == response.id) {
                                audioPlayer.stop()
                                viewModel.setPlayingResponse(null)
                                if (response.isStreaming) {
                                    isPlayingStreamingSentence = false
                                }
                            } else {
                                val audioToPlay = if (response.isStreaming && response.streamingComplete) {
                                    response.combinedAudio
                                } else {
                                    response.audioBase64
                                }
                                audioToPlay?.let { audio ->
                                    audioPlayer.stop()
                                    if (response.isStreaming) {
                                        currentStreamingResponseId = null
                                        isPlayingStreamingSentence = false
                                    }
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
                        },
                        onShareClick = if (showShareButton) {
                            val audioForShare = response.combinedAudio ?: response.audioBase64
                            audioForShare?.let { audio -> { shareAudio(audio, response.voice) } }
                        } else null
                    )
                }
                
                if (showProgress) {
                    item {
                        ThinkingIndicator(
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Request error banner (fades after 15 seconds, or tap X)
                androidx.compose.animation.AnimatedVisibility(
                    visible = uiState.error != null,
                    enter = androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.fadeOut()
                ) {
                    uiState.error?.let { error ->
                        LaunchedEffect(error) {
                            // Play error sound
                            try {
                                val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
                                toneGenerator.startTone(ToneGenerator.TONE_PROP_NACK, 200)
                                kotlinx.coroutines.delay(300)
                                toneGenerator.release()
                            } catch (e: Exception) {
                                // Ignore if tone can't be played
                            }
                            kotlinx.coroutines.delay(15000)
                            viewModel.clearError()
                        }
                        Row(
                            modifier = Modifier
                                .padding(bottom = 16.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.error,
                                    shape = MaterialTheme.shapes.medium
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Error,
                                contentDescription = "Error",
                                tint = MaterialTheme.colorScheme.onError,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onError
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Dismiss",
                                tint = MaterialTheme.colorScheme.onError,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable { viewModel.clearError() }
                            )
                        }
                    }
                }
                
                // Connection error banner
                if (!isConnected) {
                    Row(
                        modifier = Modifier
                            .padding(bottom = 16.dp)
                            .background(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = MaterialTheme.shapes.medium
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CloudOff,
                            contentDescription = "Disconnected",
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Connection issue",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SpeakButton(
                        onClick = { showSpeakDialog = true },
                        enabled = isConnected && !showProgress
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    MimicButton(
                        isListening = isMimicListening,
                        isProcessing = showProgress || !speechAvailable || !isConnected,
                        onClick = {
                            if (!isConnected) return@MimicButton
                            if (!speechAvailable) return@MimicButton
                            
                            if (!hasPermission) {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                return@MimicButton
                            }
                            
                            if (uiState.isListening) {
                                speechRecognizer.stopListening()
                                isMimicListening = false
                            } else if (!showProgress) {
                                listenMode = ListenMode.MIMIC
                                isMimicListening = true
                                speechRecognizer.startListening()
                            }
                        }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    MicButton(
                        isListening = uiState.isListening && !isMimicListening,
                        isProcessing = showProgress || !speechAvailable || !isConnected,
                        onClick = {
                            if (!isConnected) return@MicButton
                            if (!speechAvailable) return@MicButton
                            
                            if (!hasPermission) {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                return@MicButton
                            }
                            
                            if (uiState.isListening) {
                                speechRecognizer.stopListening()
                            } else if (!showProgress) {
                                listenMode = ListenMode.PROMPT
                                speechRecognizer.startListening()
                            }
                        }
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    VoiceSelector(
                        selectedVoice = uiState.selectedVoice,
                        voices = uiState.voices,
                        isLoading = uiState.isLoadingVoices,
                        onExpand = { if (isConnected) viewModel.fetchVoices() },
                        onVoiceSelected = { viewModel.setSelectedVoice(it) },
                        enabled = isConnected
                    )
                }
            }
        }
    }
}
