package com.athena.client.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CouncilMemberConfig(
    val name: String,
    val prompt: String
)

@Serializable
data class CouncilMemberInfo(
    val name: String,
    val prompt: String
)

@Serializable
data class CouncilMembersResponse(
    val members: List<CouncilMemberInfo>
)

@Serializable
data class CouncilJobRequest(
    val messages: List<ConversationMessage>,
    @SerialName("speaker_voice") val speakerVoice: String? = null,
    @SerialName("council_members") val councilMembers: List<String>? = null,
    @SerialName("custom_members") val customMembers: List<CouncilMemberConfig>? = null,
    @SerialName("user_traits") val userTraits: List<String>? = null,
    @SerialName("user_goal") val userGoal: String? = null
)

@Serializable
data class CouncilStreamJobRequest(
    val messages: List<ConversationMessage>,
    @SerialName("speaker_voice") val speakerVoice: String? = null,
    @SerialName("sentence_pause_ms") val sentencePauseMs: Int? = null,
    @SerialName("council_members") val councilMembers: List<String>? = null,
    @SerialName("custom_members") val customMembers: List<CouncilMemberConfig>? = null,
    @SerialName("user_traits") val userTraits: List<String>? = null,
    @SerialName("user_goal") val userGoal: String? = null
)

@Serializable
data class CouncilMemberNote(
    @SerialName("from_member") val fromMember: String,
    val note: String
)

@Serializable
data class CouncilMemberResponse(
    val name: String,
    @SerialName("initial_response") val initialResponse: String,
    @SerialName("notes_received") val notesReceived: List<CouncilMemberNote>,
    @SerialName("final_note") val finalNote: String
)

@Serializable
data class CouncilJobStatusResponse(
    @SerialName("job_id") val jobId: String,
    val status: String,
    @SerialName("advisor_response") val advisorResponse: String? = null,
    @SerialName("member_responses") val memberResponses: List<CouncilMemberResponse>? = null,
    val audio: String? = null,
    val voice: String? = null,
    val error: String? = null
)

@Serializable
data class CouncilStreamJobStatusResponse(
    @SerialName("job_id") val jobId: String,
    val status: String,
    @SerialName("advisor_response") val advisorResponse: String? = null,
    @SerialName("member_responses") val memberResponses: List<CouncilMemberResponse>? = null,
    val sentences: List<SentenceAudio> = emptyList(),
    @SerialName("combined_audio") val combinedAudio: String? = null,
    val voice: String? = null,
    val error: String? = null
)
