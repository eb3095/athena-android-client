package com.athena.client.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PromptRequest(
    val prompt: String,
    val speaker: Boolean = true,
    @SerialName("speaker_voice") val speakerVoice: String? = null
)
