package com.athena.client.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography

@Composable
fun ResponseCard(
    text: String,
    hasAudio: Boolean,
    isPlaying: Boolean,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Markdown(
                    content = text,
                    colors = markdownColor(
                        text = MaterialTheme.colorScheme.onSurface,
                        codeText = MaterialTheme.colorScheme.onSurface,
                        codeBackground = MaterialTheme.colorScheme.surfaceVariant,
                        dividerColor = MaterialTheme.colorScheme.outline
                    ),
                    typography = markdownTypography(
                        h1 = MaterialTheme.typography.headlineMedium,
                        h2 = MaterialTheme.typography.headlineSmall,
                        h3 = MaterialTheme.typography.titleLarge,
                        h4 = MaterialTheme.typography.titleMedium,
                        h5 = MaterialTheme.typography.titleSmall,
                        h6 = MaterialTheme.typography.labelLarge,
                        paragraph = MaterialTheme.typography.bodyLarge,
                        text = MaterialTheme.typography.bodyLarge
                    )
                )
            }
            
            if (hasAudio) {
                IconButton(
                    onClick = onPlayClick,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(48.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Stop audio" else "Play audio",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}
