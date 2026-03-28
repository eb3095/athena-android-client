package com.athena.client.data

import com.athena.client.data.models.PromptRequest
import com.athena.client.data.models.PromptResponse
import com.athena.client.data.models.VoicesResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface AthenaApi {
    @POST("api/prompt")
    suspend fun prompt(@Body request: PromptRequest): PromptResponse

    @GET("api/voices")
    suspend fun getVoices(): VoicesResponse

    @GET("health")
    suspend fun health(): Unit
}
