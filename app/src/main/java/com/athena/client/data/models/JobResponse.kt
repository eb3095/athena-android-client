package com.athena.client.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class JobSubmitResponse(
    @SerialName("job_id") val jobId: String,
    val status: String
)

@Serializable
data class JobStatusResponse(
    @SerialName("job_id") val jobId: String,
    val status: String,
    val response: String? = null,
    val audio: String? = null,
    val error: String? = null,
    val voice: String? = null
)

@Serializable
data class StreamJobRequest(
    val prompt: String,
    @SerialName("speaker_voice") val speakerVoice: String? = null,
    @SerialName("sentence_pause_ms") val sentencePauseMs: Int? = null
)

@Serializable
data class SentenceAudio(
    val index: Int,
    val text: String,
    val audio: String? = null,
    val status: String
)

@Serializable
data class StreamJobStatusResponse(
    @SerialName("job_id") val jobId: String,
    val status: String,
    val response: String? = null,
    val sentences: List<SentenceAudio> = emptyList(),
    @SerialName("combined_audio") val combinedAudio: String? = null,
    val voice: String? = null,
    val error: String? = null
)

@Serializable
data class ConversationMessage(
    val role: String,
    val content: String
)

@Serializable
data class ConversationJobRequest(
    val messages: List<ConversationMessage>,
    val speaker: Boolean = false,
    @SerialName("speaker_voice") val speakerVoice: String? = null
)

@Serializable
data class ConversationStreamJobRequest(
    val messages: List<ConversationMessage>,
    @SerialName("speaker_voice") val speakerVoice: String? = null,
    @SerialName("sentence_pause_ms") val sentencePauseMs: Int? = null
)

@Serializable
data class FormatTextRequest(
    val text: String
)

@Serializable
data class FormatTextResponse(
    @SerialName("formatted_text") val formattedText: String
)

@Serializable
data class SummarizeRequest(
    val text: String,
    @SerialName("max_words") val maxWords: Int = 6
)

@Serializable
data class SummarizeResponse(
    val summary: String
)
