package com.athena.client.data

import com.athena.client.data.models.ConversationJobRequest
import com.athena.client.data.models.ConversationStreamJobRequest
import com.athena.client.data.models.FormatTextRequest
import com.athena.client.data.models.FormatTextResponse
import com.athena.client.data.models.JobStatusResponse
import com.athena.client.data.models.JobSubmitResponse
import com.athena.client.data.models.PersonalitiesResponse
import com.athena.client.data.models.PromptRequest
import com.athena.client.data.models.PromptResponse
import com.athena.client.data.models.SpeakRequest
import com.athena.client.data.models.StreamJobRequest
import com.athena.client.data.models.StreamJobStatusResponse
import com.athena.client.data.models.SummarizeRequest
import com.athena.client.data.models.SummarizeResponse
import com.athena.client.data.models.VoicesResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface AthenaApi {
    @POST("api/prompt")
    suspend fun prompt(@Body request: PromptRequest): PromptResponse

    @POST("api/prompt/job")
    suspend fun submitPromptJob(@Body request: PromptRequest): JobSubmitResponse

    @GET("api/prompt/job/{jobId}")
    suspend fun getJobStatus(@Path("jobId") jobId: String): JobStatusResponse

    @GET("api/voices")
    suspend fun getVoices(): VoicesResponse

    @GET("api/personalities")
    suspend fun getPersonalities(): PersonalitiesResponse

    @POST("api/speak/job")
    suspend fun submitSpeakJob(@Body request: SpeakRequest): JobSubmitResponse

    @GET("api/speak/job/{jobId}")
    suspend fun getSpeakJobStatus(@Path("jobId") jobId: String): JobStatusResponse

    @POST("api/stream/job")
    suspend fun submitStreamJob(@Body request: StreamJobRequest): JobSubmitResponse

    @GET("api/stream/job/{jobId}")
    suspend fun getStreamJobStatus(@Path("jobId") jobId: String): StreamJobStatusResponse

    @POST("api/conversation/job")
    suspend fun submitConversationJob(@Body request: ConversationJobRequest): JobSubmitResponse

    @GET("api/conversation/job/{jobId}")
    suspend fun getConversationJobStatus(@Path("jobId") jobId: String): JobStatusResponse

    @POST("api/conversation/stream/job")
    suspend fun submitConversationStreamJob(@Body request: ConversationStreamJobRequest): JobSubmitResponse

    @GET("api/conversation/stream/job/{jobId}")
    suspend fun getConversationStreamJobStatus(@Path("jobId") jobId: String): StreamJobStatusResponse

    @POST("api/format/text")
    suspend fun formatText(@Body request: FormatTextRequest): FormatTextResponse

    @POST("api/summarize")
    suspend fun summarize(@Body request: SummarizeRequest): SummarizeResponse

    @GET("health")
    suspend fun health(): Unit
}
