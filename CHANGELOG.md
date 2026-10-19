# Changelog

## 🚀 0.5-beta — candidate update (not yet merged)

> 🚧 The 0.5-beta implementation and verified CI artifact are on [PR #7](https://github.com/SOLO-XP/GMailGPU/pull/7). The PR currently has merge conflicts and is not merged into `main`; this is a development candidate, not a newly published public release.

### 💬 Chat and account improvements
- Added profile photo selection in Settings and avatars beside chat participants/messages, with an initial-based fallback when a photo is missing or fails to load.
- Improved chat profile loading so an unrelated profile lookup failure does not hide the participants' photos.
- Added reusable issue defaults for game, problem type, driver, emulator/version, Wine, DXVK, Proton, VKD3D, and Box64.
- Includes earlier chat collaboration work: online/last-seen presence, Sent/Delivered/Read states, typing indicators, per-issue notification preferences, important replies, and notification navigation. Background push still depends on Firebase and server-side setup.

### 🧰 Winlator test resources
- Bundled RAM Booster v1.2.2 in the candidate APK; the app verifies the reconstructed file's expected length and SHA-256 before saving it to Downloads.
- Added a direct download for AIO Graphics Test (64-bit).
- Added a searchable/filterable component browser for Wine/Proton, DXVK, VKD3D, Box64, and related components.

### 🎮 Community Game Lab and Compatibility Library
- Expanded community game compatibility browsing and test submission details.
- Supports game/device information, driver and emulator versions, FPS/outcome, notes, and screenshot/video evidence.
- Includes game-cover browsing and language-aware labels from earlier milestones. Community results remain setup-specific, not universal guarantees.

### 🧪 Build metadata and verification
- Candidate package version: 0.5-beta, version code 20.
- CI run recorded in PR #7 passed Android build, APK integrity, version metadata, signature checks, and artifact upload.
- CI used a temporary debug signing key because stable signing secrets are not configured; Android may require uninstalling an older differently signed APK before installing this candidate.
- Do not treat the candidate as the current public release until the PR is merged and a release is published.

## 🔔 0.3.4 Beta — Chat Alerts & Live Presence (intermediate milestone)

- Added online/offline and last-seen status for the issue owner and developer team, refreshed while the app is foregrounded.
- Added an idempotent SQL setup file for presence, private device push tokens, and automatic notification rows for each new issue-chat message.
- Added Android notification permission handling, a notification channel, duplicate suppression, and direct issue navigation from notification taps.
- Added optional Firebase Cloud Messaging token registration and an authenticated server function for remote delivery while the app is in the background.
- Chat automatically refreshes about every five seconds while an issue is open and the app is active.
- Added distinct Sent, Delivered and Read states; incoming messages are marked delivered when fetched and read when the conversation is opened.
- Added an expiring Typing indicator for the issue owner/developer team while composing a reply.
- Added per-issue notification modes: all replies, important replies only, or muted. Muting never deletes chat messages.
- Added a per-message Mark this reply as important control; important-only preferences are enforced by the database trigger and push function.
- Required setup: apply chat_notifications_presence.sql and chat_read_receipts_typing_preferences.sql; background push also requires Firebase configuration and a deployed server function with its private service-account secret.

## 🌐 0.3.3 Beta — UI Translation & Issue Update Hotfix

- Extended the translation-aware text component to support the full Material text API and reused it across Community Game Lab and Game Compatibility Library.
- Game Lab and Compatibility Library screen titles, cards, filters, dialogs, fields, and action labels now respond to the selected language (English, Arabic, Brazilian Portuguese, or device language).
- Added a least-privilege issue-update permission migration for the signed-in role; existing row-level policies continue to restrict update access.
- Added repository-side session checks to prevent sending issue PATCH requests with only the public API key when no signed-in user session is present.
- Updated the APK workflow and package version to 0.3.3-beta (versionCode 18).

## 🎮 0.3.2 Beta — Cover-First Game Library

- Replaced the long report feed with a searchable two-column game gallery using square cover thumbnails.
- New community test submissions require a game cover selected from the device and separate screenshot/video evidence.
- Tapping a game opens its community test list; tapping a test opens its detailed report, evidence links, test conditions, and discussion actions.
- Added an in-game System Driver vs PanVK summary using submitted FPS results, with a reminder that community tests are not controlled benchmarks.
- Added game cover cleanup when a report owner deletes their report.
- Added a one-time, rerunnable data migration for the new cover image field.

## 🚀 0.3.1 Beta — Community Game Lab Update

- Added Community Game Lab hub, advanced game finder filters, and System Driver vs PanVK comparison cards.
- Added game watchlists with database-triggered in-app alerts when new matching reports are submitted.
- Added saved configurations for driver, emulator, Wine, DXVK, VKD3D-Proton and Box64 settings, with opt-in public sharing.
- Added guided smart troubleshooting and prefilled issue report wizard.
- Added a local diagnostic log analyzer and one-click diagnostic package.
- Added game report evidence previews, per-report discussion, duplicate warnings, report flags, and owner-controlled deletion.
- Added benchmark duration and temperature fields, community regression signals, and admin evidence review / verified test workflow.
- Added tester profiles, contribution badges, public tester highlights, community statistics, and PDF export.
- Added driver GitHub Release tracker and offline cache for the latest community reports and saved configurations.
- Added optional ML Kit translation for report notes. Technical error codes and the original report remain available.

## 0.3 Beta — Complete Mali Community Toolkit

- Added Mali GPU issue tracking, developer chat, smart diagnostics and reliable attachment uploads.
- Added community driver links for Mali-G52, G57, G615 and G720.
- Added language settings, theme controls, update checking, and unattended issue handling.