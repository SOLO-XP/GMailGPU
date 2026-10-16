# 🚀 GMailGPU — Mali Gaming Community Hub

<p align="center">
  <strong>Test smarter. Share real evidence. Make Mali gaming better.</strong>
</p>

<p align="center">
  <img alt="Version 0.3.4 Beta coming soon" src="https://img.shields.io/badge/0.3.4%20BETA-COMING%20SOON-FF9F1C?style=for-the-badge">
</p>

> 🚧 **COMING SOON — 0.3.4 Beta is not available for download yet.** The next update is being prepared with chat reply alerts, online/offline presence, read receipts, typing indicators, per-issue alert controls, and direct navigation from notifications. Version 0.3.3 Beta remains the latest published version until 0.3.4 Beta is released.

<p align="center">
  <img alt="Android" src="https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white">
  <img alt="Kotlin" src="https://img.shields.io/badge/Language-Kotlin-7F52FF?logo=kotlin&logoColor=white">
  <img alt="Current public release 0.3.3 Beta" src="https://img.shields.io/badge/CURRENT%20RELEASE-0.3.3%20Beta-0EA5E9">
  <img alt="Mali GPUs" src="https://img.shields.io/badge/Mali-G52%20%7C%20G57%20%7C%20G615%20%7C%20G720-111827">
  <img alt="Community driven" src="https://img.shields.io/badge/Community-Driven-8B5CF6">
</p>

**GMailGPU** is an Android community app for investigating Mali GPU drivers and gaming compatibility in **Winlator, GameHub, and similar environments**. Create useful bug reports, compare real test results, keep known-good settings, analyze logs, and learn from other testers.

> **Beta notice:** Compatibility results and custom drivers are experimental. A report or verified test is evidence from a particular setup—not a promise that a game or driver will work on every device.

---

## 🚧 Coming soon: 0.3.4 Beta — complete feature list

**The features listed in this section are planned for 0.3.4 Beta. They are not part of the currently published APK and are not available for download yet.**

### Chat, alerts, and presence
- 🔔 Notifications when a developer replies to a user's issue, with a tap-to-open link to that issue.
- 🟢 Online/offline and last-seen status for the user and developer team.
- 👀 Separate Sent, Delivered, and Read message indicators.
- ✍️ A short-lived “Typing…” indicator while the other participant is composing a reply.
- 🎛️ Per-issue alert settings: all replies, important replies only, or mute without deleting messages.
- ⭐ An “important reply” flag so important-only alert mode has an explicit signal to use.
- 📲 Optional Android push alerts while the app is in the background, after notification and messaging service setup is complete.

### Community Game Lab and compatibility tools
- 🧭 **Community Game Lab** — a single place for game discovery, saved settings, troubleshooting, tester profiles, statistics, and community tools.
- 🕹️ **Cover-first game gallery** — browse square game-art cards, open a game, then select a specific test to view its details, evidence, and discussion.
- 🌐 **Wider UI translation coverage** — the Game Lab and Compatibility Library screen labels, forms, filters, dialogs, and buttons use the language-aware text system.
- 🖼️ **Game cover artwork** — contributors choose a cover image separately from gameplay screenshots and video evidence.
- 🔎 **Smarter compatibility search** — filter community tests by game, Mali GPU, driver, device, emulator, outcome, and benchmark data.
- ⚖️ **System Driver vs PanVK comparisons** — compare reported FPS and graphics issues while keeping sample size and test conditions visible.
- 🔔 **Game Watchlist** — follow games and receive in-app alerts when a matching test is added.
- 🧰 **Saved configurations** — keep or share game settings, including driver, emulator, Wine, DXVK, VKD3D-Proton, Box64, resolution, and graphics preset.
- 📴 **Offline reference** — revisit the most recently cached community results when the connection is unavailable.
- 🔄 **Driver Update Tracker** — check linked projects for their latest published GitHub Releases.

### Diagnostics, evidence, and community
- 🗒️ **Local Diagnostic Log Analyzer** — spot common Vulkan, memory, crash, DXVK/VKD3D, and graphics error patterns on your device.
- 🛠️ **Guided troubleshooting** — follow a checklist for black screens, broken textures, Vulkan errors, crashes, low FPS, and memory problems, then prepare a focused bug report.
- 🧑‍⚖️ **Evidence review tools** — reviewers can examine evidence and distinguish community submissions, evidence-reviewed reports, and verified tests.
- 🖼️ **Evidence gallery and game discussions** — preview attached images/videos, discuss a specific result, and report questionable entries.
- 🧪 **Benchmark improvements** — optional test duration and device temperature, advanced sorting, duplicate warnings, and cautious regression signals.
- 👤 **Tester profiles and contribution badges** — opt in to a public profile and highlight useful, evidence-backed contributions.
- 📊 **Community statistics and PDF export** — explore test coverage by GPU and share a report summary.

### Profile photos, issue templates, and test resources

- **Profile photos:** users and developers can choose a photo in Settings; the photo appears beside their messages in issue chat.
- **Reusable issue defaults:** save your usual game, problem type, driver, Winlator version, DXVK, Wine, Proton, VKD3D, and Box64 values in Settings. New issues are prefilled but every field remains editable.
- **Winlator Test Tools:** a dedicated page for RAM cleanup guidance, the AIO Graphics Test download, and a community index for Wine/Proton, DXVK, VKD3D-Proton, Box64, and related components.
- **Supabase setup:** run [profile_avatar_and_defaults.sql](profile_avatar_and_defaults.sql) before testing profile photo uploads.

### Security, permissions, and usability
- 🛡️ **Issue-update permission improvements** — least-privilege SQL policies and signed-in session checks for issue updates.
- 🌐 **Expanded language support** — language-aware labels, forms, filters, dialogs, and buttons across the app.
- 🧪 **Safer report and test submission flow** — keep cover artwork separate from evidence and make test details easier to review.

**Release status:** 0.3.3 Beta remains the current public release. Version 0.3.4 Beta is still **COMING SOON** and will be announced only after the APK and required server setup are verified.

---

## 🎮 Explore the community tools

| Tool | What it helps you do |
| --- | --- |
| **Game Compatibility Finder** | Find tests for your game and a similar GPU/device setup. If no matching record exists, treat the game as **not tested**—not as broken. |
| **Driver Comparison** | Compare System Driver and PanVK reports, including FPS, evidence, graphics defects, and the number of test runs. |
| **Smart Troubleshooter** | Follow problem-specific checks and prepare an issue report with useful reproduction details. |
| **Log Analyzer** | Highlight known error patterns locally. It assists investigation; it does not prove the root cause. |
| **Saved Configurations** | Save or share the settings and software versions used in a test. Results may still differ on another device. |
| **Tester Profiles** | Showcase contributions with optional public visibility and badges based on useful testing—not the highest FPS. |
| **Developer Review Queue** | Review evidence, ask for more information, and verify or reject community submissions. |
| **Statistics & Export** | View aggregate coverage and export report summaries for sharing. |

---

## 🧪 Evidence and result quality

GMailGPU separates a report's **status** from the strength of its evidence:

- **Community Report** — submitted by a user; not yet reviewed.
- **Evidence Reviewed** — an authorized reviewer has inspected the submitted evidence.
- **Verified Test** — an authorized reviewer has accepted the test record after reviewing its evidence.

Verification means the submitted test was reviewed. It does **not** certify a driver or guarantee compatibility across devices.

For useful comparisons, keep the game version, device, emulator, driver build, resolution, graphics settings, and translation-layer versions as consistent as possible. Include average FPS and minimum FPS when available; test duration and device temperature are optional extra context.

### What to attach

- For a new **issue report**, attach at least one diagnostic log or video. A screenshot by itself does not satisfy this requirement.
- For a **game compatibility test**, attach at least one screenshot or video showing the result.
- Remove passwords, tokens, private conversations, and unrelated personal information before sharing any file.

---

## 📲 Install GMailGPU

1. Open the [GitHub Actions build page](https://github.com/SOLO-XP/GMailGPU/actions/workflows/build-apk.yml).
2. Select a successful run for the version you want, then download its APK artifact when available.
3. Read the notes associated with that build before installing.
4. Install the APK and sign in. Follow Android's prompts if installation permission is required.

**Important:** Some beta builds may use a different signing key. Android may not allow an in-place update if the package signatures do not match. Back up anything important before replacing a test build, and follow the specific build notes if a clean install is required.

A successful build confirms that the automated build checks passed; it does not mean every feature has been tested on every physical device.

## 🧭 First steps

### 1. Create an account and sign in

1. Open GMailGPU and choose **USER**.
2. Enter a username and choose **CREATE ACCOUNT**.
3. Choose a password of at least 8 characters, then sign in with your account.
4. Keep your login details private.

Choose **ADMIN** only if the project owner has explicitly provided you with administrator access. Ordinary user registration does not grant developer privileges.

### 2. Submit a useful issue

1. Tap **CREATE ISSUE**.
2. Add a specific title, the game name, and a clear description.
3. Include the exact error message, what you expected to happen, and steps to reproduce it.
4. Record the versions you know. Do not guess unknown driver or emulator versions.
5. Attach at least one **log or video**, review the selected files, and submit the report.

Useful details include phone model, Android version, SoC/GPU, game version, driver, Winlator/GameHub version, Wine, DXVK, VKD3D-Proton, Box64, resolution, FPS, and whether the issue repeats.

Write issue titles and descriptions in **English** so contributors can review reports consistently.

### 3. Follow up on your issue

- Open a report from **Your issues** to view its status and attachments.
- Use **CHAT WITH DEVELOPER** to discuss that specific report.
- Tap **REFRESH CHAT** to check for new messages; chat refresh is manual.
- Use **ADD MORE FILES** to attach additional evidence to an existing issue rather than creating a duplicate.

Individual attachments larger than **150 MB** may be rejected.

### 4. Use Community Game Lab

- Search for your game and filter by GPU, driver, device, emulator, and outcome.
- Add games to your Watchlist to follow new matching reports.
- Save a useful configuration or share it with the community when appropriate.
- Use the Troubleshooter and Log Analyzer to collect better evidence before filing a report.
- Check tester profiles and community statistics, but consider each record's sample size and review status.

### 5. Settings and diagnostics

- **Language:** choose device-language mode, English, Arabic, or Portuguese (Brazil).
- **Smart Diagnostic Report:** inspect the generated details and correct any missing or inaccurate versions.
- **Automatic Log Collector:** enable app-process log collection only when needed; Android may restrict log access.
- **Privacy & File Controls:** choose which optional diagnostics to include and review files before sharing.
- **Theme:** select a supported appearance option in Settings.
- **Sign out:** ends the current session on the device.

Translation models may need to download before translated text becomes available. The original message remains important when interpreting technical terms and error codes.

---

## 🔐 Privacy and responsible use

- **Review before upload.** Diagnostic logs can contain sensitive details. Automated redaction is best-effort and can miss information.
- **Share only what is needed.** Avoid uploading passwords, access tokens, private messages, or other people's personal information.
- **Control your visibility.** Public tester profiles and shared configurations are optional; check the visibility setting before saving.
- **Respect access permissions.** Users should only access data they are permitted to view. Authorized developer access is intended for troubleshooting and review.
- **Use reporting tools responsibly.** Flag questionable evidence or misleading results without posting private details publicly.
- **Remember that this is beta software.** Layouts, features, and compatibility data can change. Keep copies of important logs and reports.

GMailGPU is an independent community project. It is not affiliated with or endorsed by Arm, Mali, Winlator, GameHub, or the independent driver projects linked below.

---

## 🔗 Community driver projects

These repositories are independently maintained. Read each project's instructions, supported-device notes, and release warnings before installing experimental software.

### 🟣 Mali-G57
- [Noysz / panvk-g99-jm](https://github.com/Noysz/panvk-g99-jm)
- [mexicanbr0auth / mesa-panvk-g57](https://github.com/mexicanbr0auth/mesa-panvk-g57)
- [FristOneRR / FristOneRR-Panvk-Driver](https://github.com/FristOneRR/FristOneRR-Panvk-Driver)

### 🔵 Mali-G52
- [LukeValen / panvk-mali-g52](https://github.com/LukeValen/panvk-mali-g52)

### 🟢 Mali-G615
- [GunaCharanTeja / panvk-kbase-android](https://github.com/GunaCharanTeja/panvk-kbase-android)

### 🟠 Mali-G720
- [wonderkast02 / panvk-g720-kbase-csf](https://github.com/wonderkast02/panvk-g720-kbase-csf)

### 🧩 Winlator
- [GunaCharanTeja / WinlatorMali](https://github.com/GunaCharanTeja/WinlatorMali)

---

## 🧰 Technology

GMailGPU is built with **Kotlin**, **Android**, and **Jetpack Compose**, with cloud-backed accounts, issue tracking, private evidence uploads, and access controls.

## 🧯 Troubleshooting

- **Username already taken:** choose a different username.
- **Can't sign in:** check the selected access type, username, password, and connection.
- **Chat does not load:** open the correct issue and refresh the chat.
- **Issue status update fails with `42501` / permission denied:** run [`issue_update_permissions_hotfix.sql`](https://github.com/SOLO-XP/GMailGPU/blob/main/issue_update_permissions_hotfix.sql) once in your database SQL editor, then sign out and sign in again. The fix keeps row-level access controls enabled; never grant issue updates to the public `anon` role.
- **Upload fails:** check your connection, attachment size, selected files, and retry.
- **Language remains in English:** confirm the selected language in Settings, keep internet access available for the first on-device translation model download, and reopen the screen once the model is ready.
- **A game has no results:** treat it as **not tested**. Add an evidence-backed result if you have tested it.
- **A result says Verified:** remember that verification covers review of the submitted test, not every possible device or setup.

When reporting an app bug, include your app version, device model, clear reproduction steps, and sanitized logs.

## 🤝 Contributing

Community tests are most useful when they are reproducible and evidence-backed. When adding a result:

1. Use the exact driver and emulator versions you tested.
2. Record the GPU/device and the resolution and graphics preset.
3. Add screenshots or video that demonstrate the result.
4. Explain crashes, missing textures, rendering defects, and any workarounds.
5. Avoid claiming a universal winner from a single FPS number.

For feature ideas and app bugs, use the issue tracker: [Open a GitHub issue](https://github.com/SOLO-XP/GMailGPU/issues).

---

<p align="center">
  <strong>💚 GMailGPU — Better evidence. Better comparisons. Better Mali gaming.</strong>
</p>
