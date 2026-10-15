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

    suspend fun issueMessages(issueId: String): List<IssueMessage> =
        sb.from("issue_messages").select(Columns.ALL) {
            filter { eq("issue_id", issueId) }
            order(column = "created_at", order = Order.ASCENDING)
        }.decodeList()

    suspend fun sendIssueMessage(issueId: String, body: String): IssueMessage {
        val cleanBody = body.trim()
        require(cleanBody.isNotEmpty()) { "Write a message first." }
        require(cleanBody.length <= 4000) { "Messages must be 4000 characters or less." }
        val senderId = sb.auth.currentUserOrNull()?.id ?: error("Your session has expired. Sign in again.")
        return sb.from("issue_messages").insert(buildJsonObject {
            put("issue_id", issueId)
            put("sender_id", senderId)
            put("body", cleanBody)
        }) { select() }.decodeSingle()
    }

    suspend fun createIssue(input: CreateIssueInput): Issue =
        sb.from("issues").insert(input) { select() }.decodeSingle()

    suspend fun updateStatus(issueId: String, status: String) {
        sb.from("issues").update(buildJsonObject { put("status", status) }) { filter { eq("id", issueId) } }
    }

    suspend fun markIssueViewedByAdmin(issueId: String) {
        sb.from("issues").update(buildJsonObject {
            put("last_admin_viewed_at", java.time.Instant.now().toString())
        }) { filter { eq("id", issueId) } }
    }

    suspend fun reopenOwnIssue(issueId: String) {
        sb.from("issues").update(buildJsonObject { put("status", "open") }) {
            filter {
                eq("id", issueId)
                eq("status", "closed")
            }
        }
    }

    suspend fun updateDeveloperNote(issueId: String, note: String) {
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
