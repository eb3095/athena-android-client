package com.athena.client.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.athena.client.data.local.PersonalityEntity
import com.athena.client.data.models.Personality

@Composable
fun PersonalitySelector(
    selectedPersonalityKey: String?,
    serverPersonalities: List<Personality>,
    customPersonalities: List<PersonalityEntity>,
    isLoading: Boolean,
    onExpand: () -> Unit,
    onPersonalitySelected: (String?, String?) -> Unit,
    onAddCustomPersonality: (String, String) -> Unit,
    onDeleteCustomPersonality: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    
    val displayText = when {
        selectedPersonalityKey != null -> selectedPersonalityKey.replaceFirstChar { it.uppercase() }
        else -> "Default"
    }
    
    val contentAlpha = if (enabled) 1f else 0.5f

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = contentAlpha))
                .clickable(enabled = enabled) {
                    onExpand()
                    expanded = true
                }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Psychology,
                contentDescription = "Personality",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = displayText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(80.dp)
            )
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = "Expand",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        DropdownMenu(
            expanded = expanded && !isLoading,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Default") },
                onClick = {
                    onPersonalitySelected(null, null)
                    expanded = false
                }
            )
            
            if (serverPersonalities.isNotEmpty()) {
                HorizontalDivider()
                serverPersonalities.forEach { personality ->
                    DropdownMenuItem(
                        text = { Text(personality.key.replaceFirstChar { it.uppercase() }) },
                        onClick = {
                            onPersonalitySelected(personality.key, null)
                            expanded = false
                        }
                    )
                }
            }
            
            if (customPersonalities.isNotEmpty()) {
                HorizontalDivider()
                Text(
                    text = "Custom",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                customPersonalities.forEach { personality ->
                    DropdownMenuItem(
                        text = { 
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = personality.key.replaceFirstChar { it.uppercase() },
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { onDeleteCustomPersonality(personality.key) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = "Delete",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        },
                        onClick = {
                            onPersonalitySelected(personality.key, personality.personality)
                            expanded = false
                        }
                    )
                }
            }
            
            HorizontalDivider()
            DropdownMenuItem(
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Add custom"
                    )
                },
                text = { Text("Add custom...") },
                onClick = {
                    expanded = false
                    showAddDialog = true
                }
            )
        }
    }
    
    if (showAddDialog) {
        AddPersonalityDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name, prompt ->
                onAddCustomPersonality(name, prompt)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun AddPersonalityDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Custom Personality") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("Personality prompt") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { 
                    if (name.isNotBlank() && prompt.isNotBlank()) {
                        onAdd(name.lowercase().replace(" ", "_"), prompt) 
                    }
                },
                enabled = name.isNotBlank() && prompt.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
