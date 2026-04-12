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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import com.athena.client.data.local.MessageRole
import com.athena.client.speech.SpeechRecognizerManager
import com.athena.client.ui.components.MicButton
import com.athena.client.ui.components.PersonalitySelector
import com.athena.client.ui.components.SettingsDialog
import com.athena.client.ui.components.SwipeableInputBar
import com.athena.client.ui.components.ThinkingIndicator
import com.athena.client.ui.components.VoiceSelector
import com.athena.client.viewmodel.ConversationViewModel
import com.mikepenz.markdown.m3.Markdown
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
    conversationId: String,
    onMenuClick: () -> Unit,
    viewModel: ConversationViewModel = viewModel()
) {
    val context = LocalContext.current
    val view = LocalView.current
    val uiState by viewModel.uiState.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(conversationId) {
        viewModel.loadConversation(conversationId)
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
    var showSettingsDialog by remember { mutableStateOf(false) }
    var isTextMode by remember { mutableStateOf(false) }

    val speechRecognizer = remember {
        SpeechRecognizerManager(
            context = context,
            onResult = { result -> viewModel.sendMessage(result, fromVoice = true) },
            onPartialResult = {},
            onError = { viewModel.setListening(false) },
            onListeningStateChanged = { listening -> viewModel.setListening(listening) }
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
    var currentStreamingMessageId by remember { mutableStateOf<String?>(null) }
    var nextSentenceToPlay by remember { mutableIntStateOf(0) }
    var isPlayingStreamingSentence by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isStreamingMuted) {
        if (uiState.isStreamingMuted && isPlayingStreamingSentence) {
            audioPlayer.stop()
            isPlayingStreamingSentence = false
            viewModel.setPlayingMessage(null)
        }
    }
    
    LaunchedEffect(uiState.streamingMessageId, uiState.streamingSentences, isPlayingStreamingSentence, uiState.isStreamingMuted) {
        val streamingId = uiState.streamingMessageId
        if (streamingId != null) {
            if (currentStreamingMessageId != streamingId) {
                currentStreamingMessageId = streamingId
                nextSentenceToPlay = 0
            }

            if (!isPlayingStreamingSentence && !uiState.isStreamingMuted) {
                val completedSentences = uiState.streamingSentences.filter { 
                    it.status == "completed" && it.audio != null 
                }
                val nextSentence = completedSentences.find { it.index == nextSentenceToPlay }

                if (nextSentence?.audio != null) {
                    isPlayingStreamingSentence = true
                    viewModel.setPlayingMessage(streamingId)
                    audioPlayer.play(
                        base64Audio = nextSentence.audio,
                        onCompletion = {
                            nextSentenceToPlay++
                            isPlayingStreamingSentence = false
                        },
                        onError = {
                            nextSentenceToPlay++
                            isPlayingStreamingSentence = false
                            viewModel.setPlayingMessage(null)
                        }
                    )
                }
            }
        }
    }
    
    LaunchedEffect(uiState.streamingComplete, nextSentenceToPlay, isPlayingStreamingSentence, uiState.isStreamingMuted) {
        val shouldClear = uiState.streamingComplete && currentStreamingMessageId != null && (
            (uiState.isStreamingMuted && !isPlayingStreamingSentence) ||
            (!isPlayingStreamingSentence && nextSentenceToPlay >= uiState.streamingSentences.size)
        )
        if (shouldClear) {
            viewModel.setPlayingMessage(null)
            viewModel.clearStreamingState()
            currentStreamingMessageId = null
        }
    }

    LaunchedEffect(uiState.messages.size, uiState.initialLoadComplete) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)

            if (uiState.initialLoadComplete && previousMessageCount >= 0 && 
                uiState.messages.size > previousMessageCount) {
                val latestMessage = uiState.messages.last()
                if (latestMessage.role == MessageRole.ASSISTANT && 
                    uiState.streamingMessageId == null) {
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
        }
        if (uiState.initialLoadComplete && previousMessageCount < 0) {
            previousMessageCount = uiState.messages.size
        } else if (previousMessageCount >= 0) {
            previousMessageCount = uiState.messages.size
        }
    }

    if (showSettingsDialog) {
        SettingsDialog(
            useStreamingMode = uiState.useStreamingMode,
            onStreamingModeChanged = { viewModel.setStreamingMode(it) },
            councilUserTraits = uiState.councilUserTraits,
            councilUserGoal = uiState.councilUserGoal,
            onAddTrait = { viewModel.addCouncilUserTrait(it) },
            onRemoveTrait = { viewModel.removeCouncilUserTrait(it) },
            onGoalChanged = { viewModel.setCouncilUserGoal(it) },
            defaultVoice = uiState.defaultVoice,
            onDefaultVoiceChanged = { viewModel.setDefaultVoice(it) },
            defaultPersonality = uiState.defaultPersonality,
            onDefaultPersonalityChanged = { viewModel.setDefaultPersonality(it) },
            defaultCouncilMembers = uiState.defaultCouncilMembers,
            onDefaultCouncilMembersChanged = { viewModel.setDefaultCouncilMembers(it) },
            apiKey = uiState.apiKey,
            onApiKeyChanged = { viewModel.setApiKey(it) },
            serverUrls = uiState.serverUrls,
            onServerUrlsChanged = { viewModel.setServerUrls(it) },
            onDismiss = { showSettingsDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.title,
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
                actions = {
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Settings"
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
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
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
                            text = "Start a conversation",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Tap the microphone to speak",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = navBarPadding + 100.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.messages, key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        isPlaying = uiState.playingMessageId == message.id,
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
                    .align(Alignment.BottomCenter),
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
                                .padding(bottom = 8.dp)
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
                            .padding(bottom = 8.dp)
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
                
                androidx.compose.animation.AnimatedVisibility(
                    visible = uiState.streamingMessageId != null,
                    enter = androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.fadeOut()
                ) {
                    Row(
                        modifier = Modifier
                            .padding(bottom = 8.dp)
                            .background(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = MaterialTheme.shapes.medium
                            )
                            .clickable { viewModel.toggleStreamingMute() }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (uiState.isStreamingMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                            contentDescription = if (uiState.isStreamingMuted) "Unmute" else "Mute",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (uiState.isStreamingMuted) "Audio muted" else "Tap to mute",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                SwipeableInputBar(
                    isTextMode = isTextMode,
                    onTextModeChange = { isTextMode = it },
                    onSend = { text -> viewModel.sendMessage(text) },
                    placeholder = "Type a message...",
                    enabled = isConnected && !showProgress
                ) {
                    PersonalitySelector(
                        selectedPersonalityKey = uiState.selectedPersonalityKey,
                        serverPersonalities = uiState.serverPersonalities,
                        customPersonalities = uiState.customPersonalities,
                        isLoading = uiState.isLoadingPersonalities,
                        onExpand = { if (isConnected) viewModel.fetchPersonalities() },
                        onPersonalitySelected = { key, customPrompt -> 
                            viewModel.setSelectedPersonality(key, customPrompt)
                        },
                        onAddCustomPersonality = { name, prompt ->
                            viewModel.addCustomPersonality(name, prompt)
                        },
                        onDeleteCustomPersonality = { key ->
                            viewModel.deleteCustomPersonality(key)
                        },
                        enabled = isConnected
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    MicButton(
                        isListening = uiState.isListening,
                        isProcessing = showProgress || !speechAvailable || !isConnected,
                        onClick = {
                            if (!isConnected || !speechAvailable) return@MicButton
                            
                            if (!hasPermission) {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                return@MicButton
                            }
                            
                            if (uiState.isListening) {
                                speechRecognizer.stopListening()
                            } else if (!showProgress) {
                                speechRecognizer.startListening()
                            }
                        }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
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

@Composable
private fun MessageBubble(
    message: com.athena.client.data.local.MessageEntity,
    isPlaying: Boolean,
    onPlayClick: () -> Unit,
    onShareClick: (() -> Unit)?
) {
    val isUser = message.role == MessageRole.USER
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (isUser) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isUser) "You" else "Assistant",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isUser) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    }
                )
                if (message.voice != null && !isUser) {
                    Text(
                        text = message.voice,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            if (isUser) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            } else {
                Markdown(
                    content = message.content,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            if (!isUser && message.audioPath != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onPlayClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    if (onShareClick != null) {
                        IconButton(
                            onClick = onShareClick,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Share,
                                contentDescription = "Share",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
