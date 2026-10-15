# Changelog

## 🔔 0.3.4 Beta — Chat Alerts & Live Presence (COMING SOON)

- Added online/offline and last-seen status for the issue owner and developer team, refreshed while the app is foregrounded.
- Added an idempotent SQL setup file for presence, private device push tokens, and automatic notification rows for each new issue-chat message.
- Added Android notification permission handling, a notification channel, duplicate suppression, and direct issue navigation from notification taps.
- Added optional Firebase Cloud Messaging token registration and an authenticated server function for remote delivery while the app is in the background.
- Chat automatically refreshes about every five seconds while an issue is open and the app is active.
- Push delivery requires the included SQL setup, Firebase client configuration in build secrets, and a deployed server function with the private Firebase service-account secret; secrets are not stored in the repository.
- Planned package version: 0.3.4-beta (versionCode 19). Not published yet.

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