package com.athena.client.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.athena.client.audio.AudioPlayer
import com.athena.client.speech.SpeechRecognizerManager
import com.athena.client.ui.components.MimicButton
import com.athena.client.ui.components.ResponseCard
import com.athena.client.ui.components.SpeakButton
import com.athena.client.ui.components.SpeakDialog
import com.athena.client.ui.components.ThinkingIndicator
import com.athena.client.ui.components.VoiceSelector
import com.athena.client.viewmodel.TranscriptViewModel
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranscriptScreen(
    transcriptId: String,
    onMenuClick: () -> Unit,
    viewModel: TranscriptViewModel = viewModel()
) {
    val context = LocalContext.current
    val view = LocalView.current
    val uiState by viewModel.uiState.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(transcriptId) {
        viewModel.loadTranscript(transcriptId)
    }

    val showProgress = uiState.isLoading || uiState.isPolling
    LaunchedEffect(showProgress, uiState.playingMessageId) {
        view.keepScreenOn = showProgress || uiState.playingMessageId != null
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && uiState.messages.isNotEmpty()) {
                coroutineScope.launch {
                    listState.animateScrollToItem(uiState.messages.size - 1)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
    ) { granted -> hasPermission = granted }

    val audioPlayer = remember { AudioPlayer() }
    var speechAvailable by remember { mutableStateOf(true) }
    var showSpeakDialog by remember { mutableStateOf(false) }
    var isMimicListening by remember { mutableStateOf(false) }

    val speechRecognizer = remember {
        SpeechRecognizerManager(
            context = context,
            onResult = { result ->
                viewModel.speakText(result)
                isMimicListening = false
            },
            onPartialResult = {},
            onError = {
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

    var previousMessageCount by remember { mutableIntStateOf(-1) }

    LaunchedEffect(uiState.messages.size, uiState.initialLoadComplete) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)

            if (uiState.initialLoadComplete && previousMessageCount >= 0 && 
                uiState.messages.size > previousMessageCount) {
                val latestMessage = uiState.messages.last()
                latestMessage.audioPath?.let { audioPath ->
                    val audio = viewModel.loadAudio(audioPath)
                    if (audio != null) {
                        audioPlayer.stop()
                        viewModel.setPlayingMessage(latestMessage.id)
                        audioPlayer.play(
                            base64Audio = audio,
                            onCompletion = { viewModel.setPlayingMessage(null) },
                            onError = { viewModel.setPlayingMessage(null) }
                        )
                    }
                }
            }
        }
        if (uiState.initialLoadComplete && previousMessageCount < 0) {
            previousMessageCount = uiState.messages.size
        } else if (previousMessageCount >= 0) {
            previousMessageCount = uiState.messages.size
        }
    }

    if (showSpeakDialog) {
        SpeakDialog(
            onDismiss = { showSpeakDialog = false },
            onConfirm = { text -> viewModel.speakText(text) }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Transcript",
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(
                            imageVector = Icons.Filled.Menu,
                            contentDescription = "Menu"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
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
            if (uiState.messages.isEmpty() && !showProgress) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Text(
                            text = "Create transcripts",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Type or speak text to hear it in different voices",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 100.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.messages, key = { it.id }) { message ->
                    ResponseCard(
                        text = message.content,
                        hasAudio = message.audioPath != null,
                        isPlaying = uiState.playingMessageId == message.id,
                        isTranscript = true,
                        voice = message.voice,
                        onPlayClick = {
                            if (uiState.playingMessageId == message.id) {
                                audioPlayer.stop()
                                viewModel.setPlayingMessage(null)
                            } else {
                                message.audioPath?.let { audioPath ->
                                    val audio = viewModel.loadAudio(audioPath)
                                    if (audio != null) {
                                        audioPlayer.stop()
                                        viewModel.setPlayingMessage(message.id)
                                        audioPlayer.play(
                                            base64Audio = audio,
                                            onCompletion = { viewModel.setPlayingMessage(null) },
                                            onError = { viewModel.setPlayingMessage(null) }
                                        )
                                    }
                                }
                            }
                        },
                        onShareClick = message.audioPath?.let { audioPath ->
                            { 
                                val audio = viewModel.loadAudio(audioPath)
                                if (audio != null) {
                                    shareAudio(audio, message.voice)
                                }
                            }
                        }
                    )
                }

                if (showProgress) {
                    item {
                        ThinkingIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = uiState.error != null,
                    enter = androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.fadeOut()
                ) {
                    uiState.error?.let { error ->
                        LaunchedEffect(error) {
                            try {
                                val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
                                toneGenerator.startTone(ToneGenerator.TONE_PROP_NACK, 200)
                                kotlinx.coroutines.delay(300)
                                toneGenerator.release()
                            } catch (_: Exception) {}
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

                Row(verticalAlignment = Alignment.CenterVertically) {
                    SpeakButton(
                        onClick = { showSpeakDialog = true },
                        enabled = isConnected && !showProgress
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    MimicButton(
                        isListening = isMimicListening,
                        isProcessing = showProgress || !speechAvailable || !isConnected,
                        onClick = {
                            if (!isConnected || !speechAvailable) return@MimicButton
                            
                            if (!hasPermission) {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                return@MimicButton
                            }
                            
                            if (uiState.isListening) {
                                speechRecognizer.stopListening()
                                isMimicListening = false
                            } else if (!showProgress) {
                                isMimicListening = true
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
