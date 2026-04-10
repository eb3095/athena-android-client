package com.athena.client.data.models

import kotlinx.serialization.Serializable

@Serializable
data class Personality(
    val key: String,
    val personality: String
)

@Serializable
data class PersonalitiesResponse(
    val personalities: List<Personality>
)
