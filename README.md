# 🚀 GMailGPU 0.3.1 Beta — Community Game Lab Update

<p align="center">
  <strong>🛠️ Better bug reports. Clearer diagnostics. Stronger Mali gaming.</strong>
</p>

<p align="center">
  <img alt="Platform" src="https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white">
  <img alt="Language" src="https://img.shields.io/badge/Language-Kotlin-7F52FF?logo=kotlin&logoColor=white">
  <img alt="Backend" src="https://img.shields.io/badge/Backend-Supabase-3ECF8E?logo=supabase&logoColor=white">
  <img alt="GPU families" src="https://img.shields.io/badge/Mali-G57%20%7C%20G52%20%7C%20G615%20%7C%20G720-111827">
  <img alt="Version" src="https://img.shields.io/badge/Version-0.3.1%20Beta-00B8D9">
</p>

**GMailGPU** is an early-beta Android community app for reporting Mali GPU driver and Winlator/GameHub compatibility issues. It helps users submit reproducible bug reports, attach diagnostic evidence, communicate with developers, and find community driver projects for **Mali-G57, G52, G615, and G720**.

> ⚠️ GPU categories and repository links are community references, not a guarantee that a custom driver works on every device. GMailGPU does not certify drivers or promise that a report will result in a fix.

## ✨ Core features

- 🐛 **Structured issue reports** for crashes, Vulkan errors, black screens, broken textures, rendering problems, and performance issues.
- 📎 **Evidence required for new reports:** attach at least one log file or a video. Screenshots alone do not satisfy this requirement.
- 📂 **Add files later** to an existing issue without creating a duplicate report.
- 💬 **Issue-specific developer chat** with message history and manual refresh.
- 🔄 **Issue tracking** with statuses managed by authorized developers.
- 🧑‍💻 **Role-based views:** users see their own reports; authorized admins/developers can review reports and help troubleshoot.
- 🧠 **Smart Diagnostic Report** with available device details and editable fields for emulator, driver, Wine, DXVK, VKD3D, and Box64 versions.
- 🧾 **Automatic Log Collector** for app-process logs, with collection controlled in Settings and subject to Android restrictions.
- ☁️ **Reliable Upload Center** with upload progress and retries for failed uploads.
- 🛡️ **Privacy & File Controls** for optional device details, log collection, and best-effort redaction of common sensitive values.
- 🌐 **Language options** for device-language automatic mode, English, Arabic, and Portuguese (Brazil). Automatic mode maps supported device languages to the app's available language options. Translation models may need to download over the network; if translation is unavailable, some text may remain in English.
- 🎨 **Updated dashboard layout** with more compact admin actions and issue lists that scroll naturally.
- 🔄 **In-app update checker** — checks the latest published GitHub Release, downloads an attached APK when a newer version is detected, and opens Android’s installer. You must approve installation; Android may require permission to install unknown apps. Updates require a compatible package signature.
- 🔔 **Issue status notifications** — in-app notification when an issue status changes to Fixed/Closed.
- 🕶️ **Sensitive admin screen protection** — Android screenshot/screen-capture protection is enabled on the admin user-management and resolved-users screens to reduce accidental exposure of usernames. This is not a guarantee against every form of capture or disclosure.
- 🔗 **Mali Driver Developers** menu with links to community driver repositories.
- ⚖️ **Evidence-first driver guide** — no claimed universal winner between System Driver and PanVK; includes per-GPU visual references for G52/G57/G615/G720.
- 🧪 **Local Driver Test Lab** — save average/minimum FPS, visual issues, crashes and notes for each GPU/game/driver; compare saved System Driver and PanVK entries and share the results. Records stay on the device until shared.
- 🧭 **Community Game Lab** — an in-app hub for the game finder, a report wizard, guided troubleshooting, private saved configurations, game watchlists, in-app alerts for new matching tests, tester profiles/badges, aggregate statistics, and offline cached browsing.
- 🧪 **Advanced benchmark records** — optional test duration and device temperature fields, plus GPU/driver/outcome/device filters and newest/highest-FPS/verified-first sorting. A possible performance regression is highlighted only when two community records show a drop; it is not treated as proof of causation.
- 🖼️ **Evidence gallery and game discussions** — open signed screenshot/video previews in the app, discuss an individual test, report potentially misleading entries, and save a report’s configuration. Review labels distinguish community entries, evidence-reviewed entries, and verified tests.
- 🗒️ **Local Diagnostic Log Analyzer** — select a log for on-device pattern analysis of Vulkan initialization, out-of-memory, crash, DXVK/VKD3D and graphics signatures. Output is not uploaded automatically and is explicitly a best-effort aid.
- 🧑‍⚖️ **Developer review queue** — admins can mark evidence reviewed, verify a test after reviewing its evidence, or reject a submission. These controls require an admin role in Supabase.
- 🧾 **Export, privacy and offline support** — share a PDF report, share or remove saved configurations, cache the last successful community report list for offline reference, and flag questionable reports for moderators.
- 🔄 **Driver Update Tracker** — checks each linked repository’s latest published GitHub Release when available; it does not assume every commit is a release or every release is compatible with every device.
- 🎮 **Game Compatibility Library** — browse community results by game/GPU/driver and submit structured evidence with screenshots/videos, game and device details, System Driver vs PanVK, Winlator/GameHub, Wine, DXVK, VKD3D-Proton, Box64, resolution, graphics settings, average/minimum FPS, crashes, and rendering defects. Results are labeled as community-submitted until verified; FPS comparisons are only meaningful when test conditions are similar.
- 🧭 **Quick Troubleshooter** for black textures, Vulkan startup errors, low FPS/stutter, and crashes, with a practical checklist and direct bug-report link.
- 🟢 **Driver choice guide** for Mali-G57/G52/G615/G720: compare the system driver with experimental PanVK and learn when to try each option. Results depend on the device, driver build, and game.
- 🖼️ **In-app image preview** for issue attachments, with an option to open the signed image link externally.
- 💡 **Feature suggestions and bug reports** shortcut in Settings that opens a new GitHub issue. Sanitize logs before sharing.
- ⏱️ **14-day unattended issue handling** — issues without an admin review for 14 days move to Closed with an explanation and notification; users can reopen them. See the database migration below.

## 📲 Install and update

1. Open the [GitHub Actions build page](https://github.com/SOLO-XP/GMailGPU/actions/workflows/build-apk.yml) and choose a successful build to access its artifacts, if available.
2. Read the notes for the build you intend to install.
3. **For a hotfix build, uninstall the previous app version before installing the new APK** if the release notes specify a clean install. Back up any information you need first.
4. Install the APK and sign in. Beta builds may be debug-signed and are intended for testing.

A successful workflow run means the build job completed; it does not by itself prove that every feature has been tested on a physical device.

## ⏱️ Automatic closure after 14 days

Issues with no admin/developer review for 14 days are automatically moved to **Closed**. The issue receives a clear closure note and an in-app notification. The owner can reopen a closed issue from its details page.

For an existing Supabase database, run `supabase_14_day_auto_close.sql` once in the Supabase SQL Editor. Make sure the Supabase Cron (`pg_cron`) extension is enabled; the SQL schema schedules the check hourly. New database setups can use the updated `supabase_schema.sql`.

For the **Game Compatibility Library**, run `supabase_game_compatibility.sql` once in the Supabase SQL Editor. This creates the report table, row-level security rules, and private `game-evidence` storage bucket. Reports require at least one image/video and are initially marked `community`, not verified. Admins can review evidence and set `review_status` to `verified` or `rejected` in the Supabase table editor; do not label a result verified without checking the evidence.

For the extended **Community Game Lab**, run `supabase_game_lab_features.sql` after the compatibility migration. It adds watchlists and notifications, saved configurations, opt-in tester profiles, discussion threads, moderation flags, benchmark duration/temperature fields, and admin-only evidence review metadata. The app displays a setup error if these extra tables are not available. Public profiles are opt-in; report/config sharing should not include passwords, tokens, or private device information.

## 📘 How to use GMailGPU

### 1. Create an account

1. Open the app and choose **USER** on the access screen.
2. Enter a username and choose **CREATE ACCOUNT**.
3. Set a password of at least 8 characters.
4. Keep your login details private. If the username is already taken, choose another one.

Choose **ADMIN** only if you have credentials supplied by the project owner. Public user registration does not grant admin privileges.

### 2. Sign in

1. Select **USER**.
2. Enter your registered username and password.
3. Tap **SIGN IN**.

### 3. Create a useful issue report

1. Tap **CREATE ISSUE**.
2. Add a clear title, the game name, and a description of what happened.
3. Include the exact error, expected behavior, reproduction steps, and any relevant driver/emulator versions you know. Do not guess unknown versions.
4. Write the issue title and description in **English** so contributors can review reports consistently.
5. Attach at least one **diagnostic log or video**. Screenshots by themselves are not enough to submit a new issue.
6. Review the report and selected attachments, then submit it.

Helpful details include your phone model, Android version, SoC/GPU, game version, driver, Winlator/GameHub version, Wine, DXVK, VKD3D, Box64, FPS, and whether the problem is reproducible.

### 4. Track an issue and contact a developer

1. Open a report from **Your issues**.
2. Check its status and uploaded files.
3. Use **CHAT WITH DEVELOPER** to send a message in that report.
4. Tap **REFRESH CHAT** to check for new messages; chat refresh is manual, not live.

### 5. Add evidence to an existing issue

1. Open the issue.
2. Choose **ADD MORE FILES**.
3. Select images, video, or logs.
4. Review the selection and remove anything you do not want to upload.
5. Tap **UPLOAD FILES TO THIS ISSUE**.

Individual files over **150 MB** are rejected. Uploads require a working connection and valid backend/storage configuration.

### 6. Settings and diagnostics

- **Language:** select automatic/device language, English, Arabic, or Portuguese (Brazil). Translation may require an internet connection to download the language model.
- **Smart Diagnostic Report:** review the generated report and fill in any missing versions before sharing it.
- **Automatic Log Collector:** enable app-process logcat capture only when needed. Android may restrict which logs can be read.
- **Privacy & File Controls:** choose which optional diagnostics to include and review attachments before submission.
- **Theme and appearance:** choose a supported accent/theme option in Settings.
- **Sign out:** ends the current session on the device.

Device APIs do not always expose the exact GPU model or emulator/driver versions. Check and correct diagnostic details manually before sharing.

## 🔐 Privacy, security, and usage policy

- **Use your own account.** Do not share passwords, session tokens, or administrator credentials.
- **Share only relevant evidence.** Inspect logs, screenshots, videos, and diagnostic reports before upload. Remove passwords, access tokens, personal messages, and unrelated private information.
- **Collect logs intentionally.** App-process log collection is optional and can be disabled in Settings. Android may limit access to system or other apps' logs.
- **Access is role-based.** Users should only be able to access their own reports; administrator/developer access depends on configured backend permissions and security policies.
- **No guarantee of anonymity.** Usernames, issue content, attachments, and technical details may be visible to people authorized to review the relevant issue. Do not upload information you are not comfortable sharing with those reviewers.
- **Best-effort redaction is not a security guarantee.** Always inspect files yourself; automatic filters can miss sensitive content.
- **Respect other contributors.** Do not upload malware, illegal content, private information belonging to others, or unrelated files. Do not use the app to harass or impersonate another person.
- **Beta software warning.** Features, layouts, backend behavior, and upload availability may change. Keep copies of important diagnostic files.
- **Report security concerns responsibly.** Avoid publishing real users' private data in public issues or screenshots. Contact the project maintainers with enough detail to investigate safely.

GMailGPU is a community troubleshooting tool. It is not affiliated with or endorsed by Arm, Mali, Winlator, GameHub, or the independent driver projects linked below.

## 🎮 Community driver repositories

These repositories are independently maintained. Check each project's own instructions, compatibility notes, and release warnings before installing a driver.

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

## 🧰 Technology

- 🤖 Android + Kotlin
- 🎨 Jetpack Compose
- 🔑 Supabase Auth
- 🐘 PostgreSQL and Row Level Security
- ☁️ Supabase Storage for issue attachments

## 🧯 Troubleshooting

- **Username already taken:** choose another username.
- **Can't sign in:** verify the access type, username, password, and connection.
- **Chat does not load:** open the correct issue and refresh the chat. The required Supabase database migration must be applied.
- **Upload fails:** check connectivity, file size, backend/storage configuration, and selected files; then retry.
- **Language remains in English:** automatic mode supports Arabic and Portuguese (Brazil) mapping; translation may need network access to download its model. If it still fails, report the app version and device language with logs, but do not include secrets.
- **Cloud/database error:** a working Supabase configuration and correct database policies are required; seeing a screen locally does not prove that an issue was successfully saved to the cloud.

## 🧑‍💻 Project status

GMailGPU is an **early beta**. Please include clear reproduction steps and sanitized logs when reporting an app bug. Build artifacts are provided through [GitHub Actions](https://github.com/SOLO-XP/GMailGPU/actions/workflows/build-apk.yml); use the release notes to identify the intended build.

---

<p align="center"><strong>💚 GMailGPU — G57 • G52 • G615 • G720</strong></p>
