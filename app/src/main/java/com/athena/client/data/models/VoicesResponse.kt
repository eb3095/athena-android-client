package com.athena.client.data.models

import kotlinx.serialization.Serializable

@Serializable
data class VoicesResponse(
    val voices: List<String>
)
