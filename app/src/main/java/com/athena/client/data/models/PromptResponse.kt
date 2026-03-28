package com.athena.client.data.models

import kotlinx.serialization.Serializable

@Serializable
data class PromptResponse(
    val response: String,
    val audio: String? = null
)
