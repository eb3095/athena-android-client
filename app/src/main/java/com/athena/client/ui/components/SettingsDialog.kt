package com.athena.client.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.athena.client.BuildConfig
import com.athena.client.data.ApiClient
import com.athena.client.data.models.CouncilMemberInfo
import com.athena.client.data.models.Personality

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    useStreamingMode: Boolean,
    onStreamingModeChanged: (Boolean) -> Unit,
    councilUserTraits: List<String>,
    councilUserGoal: String,
    onAddTrait: (String) -> Unit,
    onRemoveTrait: (String) -> Unit,
    onGoalChanged: (String) -> Unit,
    defaultVoice: String?,
    onDefaultVoiceChanged: (String?) -> Unit,
    defaultPersonality: String?,
    onDefaultPersonalityChanged: (String?) -> Unit,
    defaultCouncilMembers: List<String>,
    onDefaultCouncilMembersChanged: (List<String>) -> Unit,
    apiKey: String?,
    onApiKeyChanged: (String?) -> Unit,
    serverUrls: String?,
    onServerUrlsChanged: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val serverStatuses by ApiClient.serverStatuses.collectAsState()
    var newTrait by remember { mutableStateOf("") }
    
    var apiKeyInput by remember { mutableStateOf(apiKey ?: "") }
    var isApiKeyFocused by remember { mutableStateOf(false) }
    var serverUrlsInput by remember { mutableStateOf(
        serverUrls ?: BuildConfig.API_SERVERS
    ) }
    
    var voices by remember { mutableStateOf<List<String>>(emptyList()) }
    var personalities by remember { mutableStateOf<List<Personality>>(emptyList()) }
    var councilMembers by remember { mutableStateOf<List<CouncilMemberInfo>>(emptyList()) }
    var isLoadingVoices by remember { mutableStateOf(false) }
    var isLoadingPersonalities by remember { mutableStateOf(false) }
    var isLoadingCouncilMembers by remember { mutableStateOf(false) }
    var councilMembersInitialized by remember { mutableStateOf(false) }
    
    BackHandler { onDismiss() }
    
    LaunchedEffect(Unit) {
        val api = ApiClient.getApi()
        if (api != null) {
            isLoadingVoices = true
            isLoadingPersonalities = true
            isLoadingCouncilMembers = true
            
            try {
                val voicesResponse = api.getVoices()
                voices = voicesResponse.voices
            } catch (_: Exception) { }
            isLoadingVoices = false
            
            try {
                val personalitiesResponse = api.getPersonalities()
                personalities = personalitiesResponse.personalities
            } catch (_: Exception) { }
            isLoadingPersonalities = false
            
            try {
                val membersResponse = api.getCouncilMembers()
                councilMembers = membersResponse.members
                if (!councilMembersInitialized && defaultCouncilMembers.isEmpty() && membersResponse.members.isNotEmpty()) {
                    onDefaultCouncilMembersChanged(membersResponse.members.map { it.name })
                }
                councilMembersInitialized = true
            } catch (_: Exception) { }
            isLoadingCouncilMembers = false
        }
    }
    
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(100f)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp)
            ) {
                SettingsSection(title = "Connection") {
                    Text(
                        text = "API Key",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Authentication token for the server",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = if (isApiKeyFocused) apiKeyInput else censorApiKey(apiKeyInput),
                        onValueChange = { 
                            apiKeyInput = it
                            onApiKeyChanged(it.takeIf { key -> key.isNotBlank() })
                        },
                        placeholder = { 
                            Text(
                                if (BuildConfig.AUTH_TOKEN.isNotBlank()) 
                                    "Using build-time key" 
                                else 
                                    "Enter API key"
                            ) 
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focusState ->
                                isApiKeyFocused = focusState.isFocused
                            },
                        trailingIcon = {
                            if (apiKeyInput.isNotBlank()) {
                                IconButton(onClick = { 
                                    apiKeyInput = ""
                                    onApiKeyChanged(null)
                                }) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Clear"
                                    )
                                }
                            }
                        }
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "Server URLs",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Comma-separated list of server URLs",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = serverUrlsInput,
                        onValueChange = { 
                            serverUrlsInput = it
                            onServerUrlsChanged(it.takeIf { urls -> urls.isNotBlank() })
                        },
                        placeholder = { Text("https://server1.com, https://server2.com") },
                        singleLine = false,
                        minLines = 2,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            if (serverUrlsInput != BuildConfig.API_SERVERS) {
                                IconButton(onClick = { 
                                    serverUrlsInput = BuildConfig.API_SERVERS
                                    onServerUrlsChanged(null)
                                }) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Reset to default"
                                    )
                                }
                            }
                        }
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                SettingsSection(title = "General") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Streaming Mode",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "Play audio as sentences complete",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = useStreamingMode,
                            onCheckedChange = onStreamingModeChanged
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                SettingsSection(title = "Defaults") {
                    Text(
                        text = "Default Voice",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Voice to use for new conversations",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (isLoadingVoices) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onDefaultVoiceChanged(null) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = defaultVoice == null,
                                onClick = { onDefaultVoiceChanged(null) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Default",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        voices.forEach { voice ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onDefaultVoiceChanged(voice) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = defaultVoice == voice,
                                    onClick = { onDefaultVoiceChanged(voice) }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = voice,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "Default Personality",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Personality to use for new conversations",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (isLoadingPersonalities) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onDefaultPersonalityChanged(null) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = defaultPersonality == null,
                                onClick = { onDefaultPersonalityChanged(null) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Default",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        personalities.filter { it.key.lowercase() != "default" }.forEach { personality ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onDefaultPersonalityChanged(personality.key) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = defaultPersonality == personality.key,
                                    onClick = { onDefaultPersonalityChanged(personality.key) }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = personality.key,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "Default Council Members",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Pre-selected council members for new sessions",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (isLoadingCouncilMembers) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        councilMembers.forEach { member ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val newList = if (defaultCouncilMembers.contains(member.name)) {
                                            defaultCouncilMembers - member.name
                                        } else {
                                            defaultCouncilMembers + member.name
                                        }
                                        onDefaultCouncilMembersChanged(newList)
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = defaultCouncilMembers.contains(member.name),
                                    onCheckedChange = { checked ->
                                        val newList = if (checked) {
                                            defaultCouncilMembers + member.name
                                        } else {
                                            defaultCouncilMembers - member.name
                                        }
                                        onDefaultCouncilMembersChanged(newList)
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = member.name,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                SettingsSection(title = "Council Mode") {
                    Text(
                        text = "Your Traits",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Describe yourself to help council members give better guidance",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        councilUserTraits.forEach { trait ->
                            InputChip(
                                selected = false,
                                onClick = { },
                                label = { Text(trait) },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Remove",
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable { onRemoveTrait(trait) }
                                    )
                                }
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newTrait,
                            onValueChange = { newTrait = it },
                            placeholder = { Text("Add trait...") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    if (newTrait.isNotBlank()) {
                                        onAddTrait(newTrait.trim())
                                        newTrait = ""
                                    }
                                }
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                if (newTrait.isNotBlank()) {
                                    onAddTrait(newTrait.trim())
                                    newTrait = ""
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "Add trait"
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "Your Goal",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "What are you trying to achieve?",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = councilUserGoal,
                        onValueChange = onGoalChanged,
                        placeholder = { Text("e.g., Make informed decisions") },
                        minLines = 2,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                if (serverStatuses.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    SettingsSection(title = "Servers") {
                        serverStatuses.forEach { status ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (status.isHealthy) Color(0xFF4CAF50) else Color(0xFFF44336)
                                        )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = status.url.trimEnd('/'),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary
    )
    Spacer(modifier = Modifier.height(12.dp))
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

private fun censorApiKey(key: String): String {
    if (key.isBlank()) return ""
    if (key.length <= 4) return key
    return key.take(4) + "*".repeat(minOf(key.length - 4, 8))
}
