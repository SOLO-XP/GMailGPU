# GMailGPU Chat Notifications & Online Presence — Setup

**Status: COMING SOON.** This guide is for enabling the feature on a test build. No GitHub Release is created by this change.

## 1. Apply the database setup

Run the SQL file [chat_notifications_presence.sql](chat_notifications_presence.sql) once in the existing project's SQL editor. It is safe to rerun. It creates the presence table, private device-token table, and a database trigger that stores a notification whenever a new issue-chat message is sent. It also creates the issue-chat table if a fresh installation does not already have it.

The SQL expects the core issues, profiles, and notifications tables to exist. It keeps row-level security enabled and does not grant public/anonymous users access to issue messages or online state.

## 2. Configure Firebase client values

Create a Firebase project, add an Android app with package name com.g57.issuehub, and enable Firebase Cloud Messaging. Add these GitHub Actions repository secrets under **Settings → Secrets and variables → Actions**:

- FIREBASE_API_KEY
- FIREBASE_PROJECT_ID
- FIREBASE_APPLICATION_ID
- FIREBASE_MESSAGING_SENDER_ID

For local builds, add the same values to local.properties using local.properties.example as a template, then rebuild. These are Firebase client configuration values; never put a service-account private key in the Android build configuration.

## 3. Deploy secure push delivery

Deploy supabase/functions/send-chat-push/index.ts as the Edge Function named send-chat-push. The function authenticates the requesting user, checks that the message belongs to that user, and selects recipients from the issue owner/admin team. Keep JWT verification enabled.

In the server-side function secrets, configure:

- FIREBASE_SERVICE_ACCOUNT_JSON: the entire private service-account JSON document, stored only as a server secret.

The Firebase service account must belong to the Firebase project used in the Android client values and have permission to send Firebase Cloud Messaging messages. Do not commit this JSON to GitHub, embed it in the app, or share it in chat.

The app's Android API 33+ notification permission must be granted on the device. If Firebase is not configured, chat messages and database/in-app notifications remain available, but push while the app is backgrounded will not be delivered.

## 4. Online/offline meaning

GMailGPU reports a user as online only while the app is active and has checked in recently. The status expires after a short stale window if the device loses connection or the app is force-stopped. "Offline" means no recent check-in; it does not prove the person is unavailable.
