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
