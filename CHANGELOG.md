# Changelog

## 🚀 0.5 Beta — Community Tools, Profile Photos & Winlator Test Resources

**Release candidate status:** Android CI build, APK integrity, package metadata, signature, and artifact upload passed. PR #7 is still open; this candidate has not yet replaced the public 0.3.3 Beta release.

### Chat, profile photos, and issue creation
- Added profile-photo selection in Settings for users and developers; chat displays participant photos and sender photos beside messages, with an initial-based fallback if no image is set or loading fails.
- Improved chat profile loading so one missing/unavailable profile record does not hide both participants' avatars.
- Added reusable issue defaults for game, problem type, driver, emulator/version, Wine, DXVK, Proton, VKD3D, and Box64. New issue forms are prefilled, and all values remain editable.
- Included the chat presence, Sent/Delivered/Read states, typing indicator, per-issue notification preferences, important replies, and notification navigation work described in the earlier development milestone. Background push delivery still depends on the documented Firebase and server configuration.

### Winlator test tools
- Bundled the original `RamBooster_v1.2.2.exe` inside the APK. The app reconstructs it, checks the expected byte length and SHA-256, then saves the original EXE to Android Downloads without opening an external download page.
- Added a direct-download action for AIO Graphics Test (64-bit), using the upstream release asset.
- Replaced the raw WinNative JSON link with an in-app component browser. Users can filter component types and download listed Wine, Proton, DXVK, VKD3D, Box64, and related versions directly.

### Community Game Lab and Compatibility Library
- Updated the Community Game Lab and Game Compatibility Library screens, including language-aware labels and game-cover browsing.
- Added/updated the community test submission flow for game and device details, driver/emulator/translation-layer versions, FPS and outcome, notes, and screenshot/video evidence.
- Added compatibility filters and report details so users can learn from other testers' setups. Community results remain device-specific and should not be treated as universal guarantees.

### Build and release metadata
- Set Android `versionName` to `0.5-beta` and `versionCode` to `20`.
- Updated the CI package metadata check and artifact name to `GMailGPU-v0.5-beta`.
- Verified the reconstructed RAM Booster payload against the uploaded original file.
- **Release caution:** CI used a temporary debug signing key because stable signing secrets are not configured. Android may require removing an older differently-signed build before installing this candidate. Do not describe this candidate as a public release until PR #7 is merged and the release artifact is published.


## 🔔 0.3.4 Beta — Chat Alerts & Live Presence (intermediate milestone; superseded by 0.5-beta)

- Added online/offline and last-seen status for the issue owner and developer team, refreshed while the app is foregrounded.
- Added an idempotent SQL setup file for presence, private device push tokens, and automatic notification rows for each new issue-chat message.
- Added Android notification permission handling, a notification channel, duplicate suppression, and direct issue navigation from notification taps.
- Added optional Firebase Cloud Messaging token registration and an authenticated server function for remote delivery while the app is in the background.
- Chat automatically refreshes about every five seconds while an issue is open and the app is active.
- Added distinct Sent, Delivered and Read states; incoming messages are marked delivered when fetched and read when the conversation is opened.
- Added an expiring `Typing…` indicator for the issue owner/developer team while composing a reply.
- Added per-issue notification modes: all replies, important replies only, or muted. Muting never deletes chat messages.
- Added a per-message “Mark this reply as important” control; important-only preferences are enforced by the database trigger and push function.
- The additional `chat_read_receipts_typing_preferences.sql` migration must be applied after the original chat/presence SQL.
- Push delivery requires the included SQL setup, Firebase client configuration in build secrets, and a deployed server function with the private Firebase service-account secret; secrets are not stored in the repository.
- This intermediate version was superseded before public release by 0.5-beta (versionCode 20).

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