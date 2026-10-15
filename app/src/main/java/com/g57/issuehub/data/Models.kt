package com.g57.issuehub.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Profile(
    val id: String,
    val username: String,
    val role: String = "user",
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class Issue(
    val id: String,
    @SerialName("issue_number") val issueNumber: Int,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("user_username") val userUsername: String? = null,
    val title: String,
    val description: String,
    @SerialName("problem_type") val problemType: String,
    val status: String = "open",
    val game: String,
    @SerialName("game_version") val gameVersion: String? = null,
    val driver: String,
    val emulator: String,
    @SerialName("emulator_version") val emulatorVersion: String? = null,
    val dxvk: String? = null,
    val wine: String? = null,
    val proton: String? = null,
    val vkd3d: String? = null,
    val box64: String? = null,
    val gpu: String? = null,
    val soc: String? = null,
    @SerialName("android_version") val androidVersion: String? = null,
    @SerialName("device_model") val deviceModel: String? = null,
    @SerialName("developer_note") val developerNote: String? = null,
    @SerialName("last_admin_viewed_at") val lastAdminViewedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class Attachment(
    val id: String,
    @SerialName("issue_id") val issueId: String,
    val type: String,
    val filename: String,
    @SerialName("storage_path") val storagePath: String,
    @SerialName("mime_type") val mimeType: String? = null,
    val size: Long? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class Notification(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("issue_id") val issueId: String? = null,
    val title: String,
    val message: String,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("read_at") val readAt: String? = null
)

@Serializable
data class IssueMessage(
    val id: String,
    @SerialName("issue_id") val issueId: String,
    @SerialName("sender_id") val senderId: String,
    val body: String,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class CreateIssueInput(
    @SerialName("user_id") val userId: String,
    val title: String,
    val description: String,
    @SerialName("problem_type") val problemType: String,
    val game: String,
    @SerialName("game_version") val gameVersion: String? = null,
    val driver: String,
    val emulator: String,
    @SerialName("emulator_version") val emulatorVersion: String? = null,
    val dxvk: String? = null,
    val wine: String? = null,
    val proton: String? = null,
    val vkd3d: String? = null,
    val box64: String? = null,
    val gpu: String? = null,
    val soc: String? = null,
    @SerialName("android_version") val androidVersion: String? = null,
    @SerialName("device_model") val deviceModel: String? = null
)
