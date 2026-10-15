package com.g57.issuehub.data

import com.g57.issuehub.BuildConfig

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.minutes

class G57Repository {
    private val sb = SupabaseProvider.client

    suspend fun signInAdmin(username: String, password: String): Profile {
        val clean = username.trim().lowercase()
        require(clean.length in 3..24) { "Username must be 3-24 characters." }
        require(clean.all { it.isLetterOrDigit() || it == '_' || it == '-' }) { "Use letters, numbers, _ or -." }
        val email = "$clean@g57.app"
        sb.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
        val userId = sb.auth.currentUserOrNull()?.id ?: error("No authenticated user")
        return profile(userId)
    }

    suspend fun signInUser(username: String, password: String): Profile {
        val clean = validateUsername(username)
        require(password.isNotBlank()) { "Enter your password." }
        sb.auth.signInWith(Email) {
            email = "$clean@g57.app"
            this.password = password
        }
        val userId = sb.auth.currentUserOrNull()?.id ?: error("No authenticated user")
        return profile(userId)
    }

    suspend fun registerUser(username: String, password: String): Profile {
        val clean = validateUsername(username)
        require(password.length >= 8) { "Password must be at least 8 characters." }
        sb.auth.signUpWith(Email) {
            email = "$clean@g57.app"
            this.password = password
            data = buildJsonObject { put("username", clean) }
        }
        val userId = sb.auth.currentUserOrNull()?.id
            ?: error("Account created, but no session was returned. In Supabase, disable email confirmation for username-only accounts.")
        return profile(userId)
    }

    private fun validateUsername(username: String): String {
        val clean = username.trim().lowercase()
        require(clean.length in 3..24) { "Username must be 3-24 characters." }
        require(clean.all { it.isLetterOrDigit() || it == '_' || it == '-' }) { "Use letters, numbers, _ or -." }
        return clean
    }

    suspend fun enterUser(username: String): Profile {
        val clean = username.trim().lowercase()
        require(clean.length in 3..24) { "Username must be 3-24 characters." }
        require(clean.all { it.isLetterOrDigit() || it == '_' || it == '-' }) { "Use letters, numbers, _ or -." }

        val existing = sb.auth.currentUserOrNull()
        if (existing != null) {
            // Do not sign out when a profile request fails (for example, a temporary network error).
            // Only replace the anonymous session after a successful lookup proves it belongs to another username/role.
            val existingProfile = profile(existing.id)
            if (existingProfile.role == "user" && existingProfile.username.equals(clean, ignoreCase = true)) {
                return existingProfile
            }
            sb.auth.signOut()
        }
        sb.auth.signInAnonymously(
            data = buildJsonObject { put("username", clean) }
        )
        val userId = sb.auth.currentUserOrNull()?.id ?: error("No anonymous user session")
        return profile(userId)
    }

    suspend fun signOut() = sb.auth.signOut()

    suspend fun profile(userId: String): Profile =
        sb.from("profiles").select(Columns.ALL) { filter { eq("id", userId) } }
            .decodeSingle<Profile>()

    suspend fun allProfiles(): List<Profile> =
        sb.from("profiles").select(Columns.ALL) { order(column = "created_at", order = Order.ASCENDING) }
            .decodeList()

    suspend fun myIssues(): List<Issue> =
        sb.from("issues").select(Columns.ALL) { order(column = "created_at", order = Order.DESCENDING) }
            .decodeList()

    suspend fun allIssues(): List<Issue> {
        val issues = sb.from("issues").select(Columns.ALL) { order(column = "created_at", order = Order.DESCENDING) }.decodeList<Issue>()
        val profiles = allProfiles().associateBy { it.id }
        return issues.map { issue -> issue.copy(userUsername = issue.userId?.let { profiles[it]?.username }) }
    }

    suspend fun resolvedIssues(): List<Issue> =
        allIssues().filter { it.status == "fixed" || it.status == "closed" }

    suspend fun attachments(issueId: String): List<Attachment> =
        sb.from("attachments").select(Columns.ALL) {
            filter { eq("issue_id", issueId) }
            order(column = "created_at", order = Order.ASCENDING)
        }.decodeList()

    suspend fun notifications(userId: String): List<Notification> =
        sb.from("notifications").select(Columns.ALL) {
            filter { eq("user_id", userId) }
            order(column = "created_at", order = Order.DESCENDING)
        }.decodeList()

    suspend fun visiblePresence(): List<UserPresence> =
        sb.from("user_presence").select(Columns.ALL).decodeList()

    suspend fun updatePresence(online: Boolean) {
        val userId = sb.auth.currentUserOrNull()?.id ?: return
        sb.from("user_presence").upsert(buildJsonObject {
            put("user_id", userId)
            put("is_online", online)
        }) { onConflict = "user_id" }
    }

    suspend fun registerPushToken(token: String) {
        require(token.isNotBlank()) { "Push token is empty." }
        val userId = sb.auth.currentUserOrNull()?.id ?: error("Sign in before registering notifications.")
        sb.from("device_push_tokens").upsert(buildJsonObject {
            put("token", token)
            put("user_id", userId)
            put("platform", "android")
            put("updated_at", java.time.Instant.now().toString())
        }) { onConflict = "token" }
    }

    suspend fun deletePushToken(token: String) {
        val userId = sb.auth.currentUserOrNull()?.id ?: return
        sb.from("device_push_tokens").delete {
            filter { eq("token", token); eq("user_id", userId) }
        }
    }

    suspend fun issueById(issueId: String): Issue =
        sb.from("issues").select(Columns.ALL) {
            filter { eq("id", issueId) }
        }.decodeSingle()

    private suspend fun requestChatPush(messageId: String) {
        val accessToken = sb.auth.currentAccessTokenOrNull() ?: return
        val projectUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
        require(projectUrl.startsWith("https://")) { "Push endpoint is not configured." }
        withContext(Dispatchers.IO) {
            val connection = (URL("$projectUrl/functions/v1/send-chat-push").openConnection() as HttpURLConnection)
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = 8000
                connection.readTimeout = 12000
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
                connection.setRequestProperty("Authorization", "Bearer $accessToken")
                connection.outputStream.use {
                    it.write(JSONObject().put("message_id", messageId).toString().toByteArray(Charsets.UTF_8))
                }
                val status = connection.responseCode
                if (status !in 200..299) {
                    val errorBody = runCatching {
                        (connection.errorStream ?: connection.inputStream).bufferedReader().use { it.readText() }
                    }.getOrNull().orEmpty()
                    throw IllegalStateException("Push service returned HTTP $status: ${errorBody.take(200)}")
                }
            } finally {
                connection.disconnect()
            }
        }
    }

    suspend fun issueMessages(issueId: String): List<IssueMessage> =
        sb.from("issue_messages").select(Columns.ALL) {
            filter { eq("issue_id", issueId) }
            order(column = "created_at", order = Order.ASCENDING)
        }.decodeList()

    suspend fun markMessagesDelivered(messages: List<IssueMessage>, readerId: String) {
        val now = java.time.Instant.now().toString()
        messages.filter { it.senderId != readerId && it.deliveredAt == null }.forEach { message ->
            sb.from("issue_messages").update(buildJsonObject {
                put("delivered_at", now)
            }) {
                filter { eq("id", message.id); eq("issue_id", message.issueId) }
            }
        }
    }

    suspend fun markIssueMessagesRead(issueId: String, readerId: String) {
        val incoming = issueMessages(issueId).filter { it.senderId != readerId && it.readAt == null }
        if (incoming.isEmpty()) return
        val now = java.time.Instant.now().toString()
        incoming.forEach { message ->
            sb.from("issue_messages").update(buildJsonObject {
                if (message.deliveredAt == null) put("delivered_at", now)
                put("read_at", now)
            }) {
                filter { eq("id", message.id); eq("issue_id", issueId) }
            }
        }
    }

    suspend fun issueTypingStates(issueId: String): List<IssueTypingState> =
        sb.from("issue_typing_states").select(Columns.ALL) {
            filter { eq("issue_id", issueId) }
        }.decodeList()

    suspend fun updateTypingState(issueId: String, active: Boolean) {
        val userId = sb.auth.currentUserOrNull()?.id ?: return
        if (active) {
            sb.from("issue_typing_states").upsert(buildJsonObject {
                put("issue_id", issueId)
                put("user_id", userId)
                put("expires_at", java.time.Instant.now().plusSeconds(10).toString())
            }) { onConflict = "issue_id,user_id" }
        } else {
            sb.from("issue_typing_states").delete {
                filter { eq("issue_id", issueId); eq("user_id", userId) }
            }
        }
    }

    suspend fun issueNotificationMode(issueId: String): String {
        val userId = sb.auth.currentUserOrNull()?.id ?: return "all"
        return runCatching {
            sb.from("issue_notification_preferences").select(Columns.ALL) {
                filter { eq("issue_id", issueId); eq("user_id", userId) }
            }.decodeList<IssueNotificationPreference>().firstOrNull()?.mode ?: "all"
        }.getOrDefault("all")
    }

    suspend fun setIssueNotificationMode(issueId: String, mode: String) {
        require(mode in setOf("all", "important", "muted")) { "Choose a valid notification mode." }
        val userId = sb.auth.currentUserOrNull()?.id ?: error("Sign in before changing notification preferences.")
        sb.from("issue_notification_preferences").upsert(buildJsonObject {
            put("issue_id", issueId)
            put("user_id", userId)
            put("mode", mode)
            put("updated_at", java.time.Instant.now().toString())
        }) { onConflict = "user_id,issue_id" }
    }

    suspend fun sendIssueMessage(issueId: String, body: String, important: Boolean = false): IssueMessage {
        val cleanBody = body.trim()
        require(cleanBody.isNotEmpty()) { "Write a message first." }
        require(cleanBody.length <= 4000) { "Messages must be 4000 characters or less." }
        val senderId = sb.auth.currentUserOrNull()?.id ?: error("Your session has expired. Sign in again.")
        val sent = sb.from("issue_messages").insert(buildJsonObject {
            put("issue_id", issueId)
            put("sender_id", senderId)
            put("body", cleanBody)
            put("is_important", important)
        }) { select() }.decodeSingle<IssueMessage>()
        // Chat delivery must succeed even when optional Firebase push has not been configured.
        runCatching { requestChatPush(sent.id) }
        return sent
    }

    suspend fun createIssue(input: CreateIssueInput): Issue =
        sb.from("issues").insert(input) { select() }.decodeSingle()

    private fun requireSignedInSession() {
        check(sb.auth.currentUserOrNull() != null) {
            "Your sign-in session is missing or expired. Sign out and sign in again before changing this issue."
        }
    }

    suspend fun updateStatus(issueId: String, status: String) {
        requireSignedInSession()
        sb.from("issues").update(buildJsonObject { put("status", status) }) { filter { eq("id", issueId) } }
    }

    suspend fun markIssueViewedByAdmin(issueId: String) {
        // This is a quiet, best-effort timestamp. Never send a PATCH with the public API key alone.
        if (sb.auth.currentUserOrNull() == null) return
        sb.from("issues").update(buildJsonObject {
            put("last_admin_viewed_at", java.time.Instant.now().toString())
        }) { filter { eq("id", issueId) } }
    }

    suspend fun reopenOwnIssue(issueId: String) {
        requireSignedInSession()
        sb.from("issues").update(buildJsonObject { put("status", "open") }) {
            filter {
                eq("id", issueId)
                eq("status", "closed")
            }
        }
    }

    suspend fun updateDeveloperNote(issueId: String, note: String) {
        requireSignedInSession()
        sb.from("issues").update(buildJsonObject { put("developer_note", note) }) { filter { eq("id", issueId) } }
    }

    suspend fun markNotificationRead(notificationId: String) {
        sb.from("notifications").update(buildJsonObject { put("read_at", java.time.Instant.now().toString()) }) {
            filter { eq("id", notificationId) }
        }
    }

    suspend fun uploadAttachment(
        issueId: String,
        filename: String,
        mime: String,
        bytes: ByteArray,
        type: String
    ): String {
        val safeName = filename.replace(Regex("[^A-Za-z0-9._-]"), "_").take(160)
        val path = "$issueId/${System.currentTimeMillis()}_$safeName"
        sb.storage.from("issue-files").upload(path, bytes) {
            upsert = false
        }
        sb.from("attachments").insert(buildJsonObject {
            put("issue_id", issueId)
            put("type", type)
            put("filename", filename)
            put("storage_path", path)
            put("mime_type", mime)
            put("size", bytes.size.toLong())
        })
        return path
    }

    suspend fun signedUrl(path: String): String =
        sb.storage.from("issue-files").createSignedUrl(path = path, expiresIn = 20.minutes)

    suspend fun deleteIssue(issueId: String) {
        val files = attachments(issueId)
        if (files.isNotEmpty()) {
            sb.storage.from("issue-files").delete(*files.map { it.storagePath }.toTypedArray())
        }
        sb.from("issues").delete { filter { eq("id", issueId) } }
    }
}
