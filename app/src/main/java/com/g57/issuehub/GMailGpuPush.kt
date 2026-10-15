package com.g57.issuehub

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.g57.issuehub.data.SupabaseProvider
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.tasks.await

internal object GMailGpuPush {
    const val EXTRA_ISSUE_ID = "gmailgpu_notification_issue_id"
    private const val CHANNEL_ID = "gmailgpu_issue_chat"
    private const val PREFS = "gmailgpu_settings"
    private const val SEEN_IDS = "gmailgpu_notified_chat_ids"

    fun ensureFirebase(context: Context): FirebaseApp? {
        val existing = FirebaseApp.getApps(context).firstOrNull()
        if (existing != null) return existing
        if (BuildConfig.FIREBASE_API_KEY.isBlank() ||
            BuildConfig.FIREBASE_PROJECT_ID.isBlank() ||
            BuildConfig.FIREBASE_APPLICATION_ID.isBlank() ||
            BuildConfig.FIREBASE_MESSAGING_SENDER_ID.isBlank()
        ) return null
        return runCatching {
            FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setApiKey(BuildConfig.FIREBASE_API_KEY)
                    .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                    .setApplicationId(BuildConfig.FIREBASE_APPLICATION_ID)
                    .setGcmSenderId(BuildConfig.FIREBASE_MESSAGING_SENDER_ID)
                    .build()
            )
        }.getOrNull()
    }

    suspend fun tokenOrNull(context: Context): String? = runCatching {
        val app = ensureFirebase(context) ?: return@runCatching null
        FirebaseMessaging.getInstance().token.await()
    }.getOrNull()

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Issue chat replies",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "New replies on your GMailGPU issues"
                    enableVibration(true)
                }
            )
        }
    }

    fun showOnce(
        context: Context,
        deliveryId: String,
        title: String,
        body: String,
        issueId: String
    ) {
        if (deliveryId.isBlank() || title.isBlank() || issueId.isBlank()) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val seen = prefs.getString(SEEN_IDS, "").orEmpty().split('|').filter { it.isNotBlank() }.toMutableSet()
        if (deliveryId in seen) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        createChannel(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_ISSUE_ID, issueId)
        }
        val pending = PendingIntent.getActivity(
            context,
            issueId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(deliveryId.hashCode(), notification)
            seen += deliveryId
            prefs.edit().putString(SEEN_IDS, seen.toList().takeLast(200).joinToString("|")).apply()
        } catch (_: SecurityException) {
            // Notifications remain in the in-app issue inbox if the Android permission is denied.
        }
    }
}

class GMailGpuMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        getSharedPreferences("gmailgpu_settings", Context.MODE_PRIVATE)
            .edit().putString("gmailgpu_pending_fcm_token", token).apply()
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val issueId = data["issue_id"].orEmpty()
        val title = data["notification_title"] ?: "New GMailGPU reply"
        val body = data["notification_body"] ?: "Open the issue to read the new message."
        val deliveryId = data["notification_id"] ?: data["message_id"] ?: "${issueId}_${title.hashCode()}"
        GMailGpuPush.showOnce(this, deliveryId, title, body, issueId)
    }
}
