package com.g57.issuehub.ui

import android.app.Application
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.g57.issuehub.GMailGpuPush
import com.g57.issuehub.data.*
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import java.net.HttpURLConnection
import java.net.URL

sealed interface ScreenState {
    data object Login : ScreenState
    data object UserHome : ScreenState
    data object CreateIssue : ScreenState
    data class UserIssue(val issue: Issue) : ScreenState
    data object AdminHome : ScreenState
    data object AdminSolvedUsers : ScreenState
    data object AdminUserManagement : ScreenState
    data object DriverDevelopers : ScreenState
    data object GameCompatibility : ScreenState
    data object GameLab : ScreenState
    data object WinlatorTestTools : ScreenState
    data object Help : ScreenState
    data object Settings : ScreenState
    data class AdminIssue(val issue: Issue) : ScreenState
}

data class PickedFile(
    val uri: Uri,
    val name: String,
    val mime: String,
    val type: String,
    val size: Long
)

data class ResolvedUser(
    val username: String,
    val issueCount: Int,
    val latestIssueNumber: Int,
    val latestTitle: String
)

data class UiState(
    val screen: ScreenState = ScreenState.Login,
    val themeChoice: String = "Violet",
    val appLanguage: String = "system",
    val includeDeviceDiagnostics: Boolean = true,
    val collectAppLogsEnabled: Boolean = false,
    val redactSensitiveLogs: Boolean = true,
    val defaultGame: String = "",
    val defaultProblemType: String = "Crash",
    val defaultDriver: String = "Mali 26.2",
    val defaultEmulator: String = "Winlator",
    val defaultEmulatorVersion: String = "",
    val defaultDxvk: String = "",
    val defaultWine: String = "",
    val defaultProton: String = "",
    val defaultVkd3d: String = "",
    val defaultBox64: String = "",
    val uploadProgress: String? = null,
    val attachmentPreviewName: String? = null,
    val attachmentPreviewText: String? = null,
    val attachmentPreviewUrl: String? = null,
    val downloadUrl: String? = null,
    val downloadFilename: String? = null,
    val downloadMimeType: String? = null,
    val username: String = "",
    val password: String = "",
    val loginRole: String = "user",
    val userCreateAccount: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val success: String? = null,
    val profile: Profile? = null,
    val issues: List<Issue> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
    val chatMessages: List<IssueMessage> = emptyList(),
    val chatDraft: String = "",
    val chatTypingStates: List<IssueTypingState> = emptyList(),
    val chatNotificationMode: String = "all",
    val chatMarkImportant: Boolean = false,
    val notifications: List<Notification> = emptyList(),
    val presenceRecords: List<UserPresence> = emptyList(),
    val resolvedUsers: List<ResolvedUser> = emptyList(),
    val userProfiles: List<Profile> = emptyList(),
    val selectedFiles: List<PickedFile> = emptyList(),
    val openUrl: String? = null,
    val title: String = "",
    val description: String = "",
    val problemType: String = "Crash",
    val game: String = "",
    val gpu: String = "Not detected — verify manually",
    val soc: String = "",
    val driver: String = "Mali 26.2",
    val emulator: String = "Winlator",
    val emulatorVersion: String = "",
    val gameVersion: String = "",
    val dxvk: String = "",
    val wine: String = "",
    val proton: String = "",
    val vkd3d: String = "",
    val box64: String = ""
)

class G57ViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = G57Repository()
    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()
    private var typingUpdateJob: Job? = null
    @Volatile private var lastChatTypingAt: Long = 0L

    init {
        val prefs = getApplication<Application>().getSharedPreferences("gmailgpu_settings", 0)
        val snapshot = DiagnosticCollector.snapshot(getApplication())
        _ui.value = _ui.value.copy(
            themeChoice = prefs.getString("theme_choice", "Violet") ?: "Violet",
            appLanguage = prefs.getString("app_language", "system") ?: "system",
            includeDeviceDiagnostics = prefs.getBoolean("include_device_diagnostics", true),
            collectAppLogsEnabled = prefs.getBoolean("collect_app_logs", false),
            redactSensitiveLogs = prefs.getBoolean("redact_sensitive_logs", true),
            defaultGame = prefs.getString("default_issue_game", "") ?: "",
            defaultProblemType = prefs.getString("default_issue_problem_type", "Crash") ?: "Crash",
            defaultDriver = prefs.getString("default_issue_driver", "Mali 26.2") ?: "Mali 26.2",
            defaultEmulator = prefs.getString("default_issue_emulator", "Winlator") ?: "Winlator",
            defaultEmulatorVersion = prefs.getString("default_issue_emulator_version", "") ?: "",
            defaultDxvk = prefs.getString("default_issue_dxvk", "") ?: "",
            defaultWine = prefs.getString("default_issue_wine", "") ?: "",
            defaultProton = prefs.getString("default_issue_proton", "") ?: "",
            defaultVkd3d = prefs.getString("default_issue_vkd3d", "") ?: "",
            defaultBox64 = prefs.getString("default_issue_box64", "") ?: "",
            gpu = snapshot.gpu,
            soc = snapshot.soc
        )
        restoreSession()
    }

    private fun restoreSession() = viewModelScope.launch {
        if (!SupabaseProvider.enabled) return@launch
        // Wait until Supabase has finished loading the saved session before checking the current user.
        runCatching {
            SupabaseProvider.client.auth.sessionStatus.first { it !is SessionStatus.Initializing }
            val user = SupabaseProvider.client.auth.currentUserOrNull() ?: return@runCatching null
            repo.profile(user.id)
        }.onSuccess { profile ->
            profile ?: return@onSuccess
            update {
                copy(
                    username = profile.username,
                    profile = profile,
                    screen = if (profile.role == "admin") ScreenState.AdminHome else ScreenState.UserHome,
                    error = null
                )
            }
            if (profile.role == "admin") loadIssues(true) else loadUserData(profile.id)
        }.onFailure {
            // Preserve the auth session. A temporary cloud/profile error must not look like a forced logout.
            update { copy(error = "Couldn't restore your session from the cloud. Check your connection and try again; you have not been signed out.") }
        }
    }

    fun setLoginRole(role: String) = update { copy(loginRole = role, password = "", userCreateAccount = false, error = null) }
    fun setUserCreateAccount(value: Boolean) = update { copy(userCreateAccount = value, password = "", error = null, success = null) }
    fun setUsername(v: String) = update { copy(username = v, error = null) }
    fun setPassword(v: String) = update { copy(password = v, error = null) }
    fun setField(field: String, v: String) = update {
        when (field) {
            "title" -> copy(title = v)
            "description" -> copy(description = v)
            "game" -> copy(game = v)
            "gpu" -> copy(gpu = v)
            "soc" -> copy(soc = v)
            "driver" -> copy(driver = v)
            "emulator" -> copy(emulator = v)
            "emulatorVersion" -> copy(emulatorVersion = v)
            "gameVersion" -> copy(gameVersion = v)
            "dxvk" -> copy(dxvk = v)
            "wine" -> copy(wine = v)
            "proton" -> copy(proton = v)
            "vkd3d" -> copy(vkd3d = v)
            "box64" -> copy(box64 = v)
            else -> this
        }
    }
    fun setProblemType(v: String) = update { copy(problemType = v) }

    fun setIssueDefault(field: String, value: String) {
        val keys = mapOf(
            "game" to "default_issue_game",
            "problemType" to "default_issue_problem_type",
            "driver" to "default_issue_driver",
            "emulator" to "default_issue_emulator",
            "emulatorVersion" to "default_issue_emulator_version",
            "dxvk" to "default_issue_dxvk",
            "wine" to "default_issue_wine",
            "proton" to "default_issue_proton",
            "vkd3d" to "default_issue_vkd3d",
            "box64" to "default_issue_box64"
        )
        val key = keys[field] ?: return
        getApplication<Application>().getSharedPreferences("gmailgpu_settings", 0).edit().putString(key, value).apply()
        update {
            when (field) {
                "game" -> copy(defaultGame = value)
                "problemType" -> copy(defaultProblemType = value)
                "driver" -> copy(defaultDriver = value)
                "emulator" -> copy(defaultEmulator = value)
                "emulatorVersion" -> copy(defaultEmulatorVersion = value)
                "dxvk" -> copy(defaultDxvk = value)
                "wine" -> copy(defaultWine = value)
                "proton" -> copy(defaultProton = value)
                "vkd3d" -> copy(defaultVkd3d = value)
                "box64" -> copy(defaultBox64 = value)
                else -> this
            }
        }
    }

    fun uploadProfileAvatar(uri: Uri) = viewModelScope.launch {
        val profile = _ui.value.profile ?: return@launch
        if (!SupabaseProvider.enabled) {
            update { copy(error = "Profile photo upload requires cloud storage.") }
            return@launch
        }
        update { copy(loading = true, error = null, success = null) }
        runCatching {
            val context = getApplication<Application>()
            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
            require(mime.startsWith("image/")) { "Choose an image file." }
            val bytes = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("Could not read the selected image.")
            }
            val ext = mime.substringAfter('/', "jpeg").substringBefore(';').take(8)
            repo.uploadProfileAvatar(profile.id, "profile-photo.$ext", mime, bytes)
        }.onSuccess { updatedProfile ->
            update { copy(loading = false, profile = updatedProfile, success = "Profile photo updated.") }
            if (updatedProfile.role == "admin") loadIssues(true) else loadUserData(updatedProfile.id)
        }.onFailure { e ->
            update { copy(loading = false, error = "Could not upload profile photo. Apply profile_avatar_and_defaults.sql in Supabase first. ${e.message.orEmpty()}") }
        }
    }

    fun setAppLanguage(language: String) {
        val supported = setOf("system", "en", "ar", "pt-BR")
        val selected = language.takeIf { it in supported } ?: "system"
        getApplication<Application>().getSharedPreferences("gmailgpu_settings", 0)
            .edit().putString("app_language", selected).apply()
        update { copy(appLanguage = selected) }
    }

    fun login() = viewModelScope.launch {
        val u = _ui.value.username.trim()
        val p = _ui.value.password
        val requestedRole = _ui.value.loginRole
        val createAccount = _ui.value.userCreateAccount

        if (u.isBlank()) {
            update { copy(error = "Enter a username.") }
            return@launch
        }
        if (p.isBlank()) {
            update { copy(error = if (requestedRole == "admin") "Enter the admin password." else "Enter your password.") }
            return@launch
        }
        if (requestedRole == "user" && createAccount && p.length < 8) {
            update { copy(error = "Choose a password with at least 8 characters.") }
            return@launch
        }
        if (!SupabaseProvider.enabled) {
            update { copy(error = "Cloud database is not configured in this build.") }
            return@launch
        }

        update { copy(loading = true, error = null, success = null) }

        runCatching {
            when {
                requestedRole == "admin" -> repo.signInAdmin(u, p)
                createAccount -> repo.registerUser(u, p)
                else -> repo.signInUser(u, p)
            }
        }.onSuccess { profile ->
            if (profile.role != requestedRole) {
                runCatching { repo.signOut() }
                update { copy(loading = false, error = "This account is not registered as $requestedRole.") }
                return@onSuccess
            }
            update {
                copy(
                    loading = false,
                    password = "",
                    profile = profile,
                    screen = if (profile.role == "admin") ScreenState.AdminHome else ScreenState.UserHome
                )
            }
            if (profile.role == "admin") loadIssues(true) else loadUserData(profile.id)
        }.onFailure {
            val friendly = if (requestedRole == "admin") {
                "Invalid username or password."
            } else if (createAccount) {
                it.message ?: "Could not create this account. The username may already be taken."
            } else {
                "Unable to sign in. Check your username and password. If this is an old username-only account, an admin must remove that legacy account before you can register it with a password."
            }
            // Keep any existing session intact when a login/profile request fails.
            update { copy(loading = false, error = friendly) }
        }
    }

    fun loadUserData(userId: String) = viewModelScope.launch {
        if (!SupabaseProvider.enabled) {
            update { copy(issues = emptyList(), notifications = emptyList()) }
            return@launch
        }
        update { copy(loading = true, error = null) }
        runCatching {
            val issues = repo.myIssues()
            val notifications = repo.notifications(userId)
            issues to notifications
        }.onSuccess { (issues, notifications) ->
            update { copy(loading = false, issues = issues, notifications = notifications) }
        }.onFailure { e -> update { copy(loading = false, error = e.message) } }
    }

    fun loadIssues(admin: Boolean) = viewModelScope.launch {
        if (!SupabaseProvider.enabled) {
            update { copy(issues = demoIssues()) }
            return@launch
        }
        update { copy(loading = true, error = null) }
        runCatching { if (admin) repo.allIssues() else repo.myIssues() }
            .onSuccess { list -> update { copy(loading = false, issues = list) } }
            .onFailure { e -> update { copy(loading = false, error = e.message) } }
    }

    fun openDriverDevelopers() = update { copy(screen = ScreenState.DriverDevelopers, error = null) }
    fun openGameCompatibility() = update { copy(screen = ScreenState.GameCompatibility, error = null, success = null) }
    fun openGameLab() = update { copy(screen = ScreenState.GameLab, error = null, success = null) }
    fun openWinlatorTestTools() = update { copy(screen = ScreenState.WinlatorTestTools, error = null, success = null) }
    fun openHelp() = update { copy(screen = ScreenState.Help, error = null) }
    fun openSettings() = update { copy(screen = ScreenState.Settings, error = null) }
    fun goHome() = update {
        copy(screen = if (profile?.role == "admin") ScreenState.AdminHome else ScreenState.UserHome, error = null)
    }
    fun setThemeChoice(value: String) {
        if (value !in setOf("Violet", "Ocean", "Emerald", "Amber", "Rose", "Light")) return
        getApplication<Application>().getSharedPreferences("gmailgpu_settings", 0).edit().putString("theme_choice", value).apply()
        update { copy(themeChoice = value) }
    }

    fun setIncludeDeviceDiagnostics(value: Boolean) {
        getApplication<Application>().getSharedPreferences("gmailgpu_settings", 0).edit().putBoolean("include_device_diagnostics", value).apply()
        update { copy(includeDeviceDiagnostics = value) }
    }

    fun setCollectAppLogsEnabled(value: Boolean) {
        getApplication<Application>().getSharedPreferences("gmailgpu_settings", 0).edit().putBoolean("collect_app_logs", value).apply()
        update { copy(collectAppLogsEnabled = value) }
    }

    fun setRedactSensitiveLogs(value: Boolean) {
        getApplication<Application>().getSharedPreferences("gmailgpu_settings", 0).edit().putBoolean("redact_sensitive_logs", value).apply()
        update { copy(redactSensitiveLogs = value) }
    }

    fun generateDiagnosticReport() = viewModelScope.launch(Dispatchers.IO) {
        runCatching {
            val file = DiagnosticCollector.createReportFile(getApplication(), _ui.value.includeDeviceDiagnostics)
            addFiles(listOf(file))
            update { copy(success = "Smart diagnostic report added. Review the attachment before submitting.", error = null) }
        }.onFailure { e ->
            update { copy(error = "Could not generate diagnostic report: ${e.message ?: "unknown error"}") }
        }
    }

    fun collectAppLogs() = viewModelScope.launch(Dispatchers.IO) {
        val state = _ui.value
        runCatching {
            val file = DiagnosticCollector.createLogBundle(
                getApplication(),
                redactSensitive = state.redactSensitiveLogs,
                includeAppLogcat = state.collectAppLogsEnabled,
                includeDeviceDetails = state.includeDeviceDiagnostics
            )
            addFiles(listOf(file))
            update { copy(success = "Log bundle created. Review it before uploading.", error = null) }
        }.onFailure { e ->
            update { copy(error = "Could not collect logs: ${e.message ?: "unknown error"}") }
        }
    }

    fun openExternalUrl(url: String) = update { copy(openUrl = url) }

    fun loadUsers() = viewModelScope.launch {
        if (!SupabaseProvider.enabled) {
            update { copy(userProfiles = emptyList()) }
            return@launch
        }
        update { copy(loading = true, error = null) }
        runCatching { repo.allProfiles() }
            .onSuccess { profiles -> update { copy(loading = false, userProfiles = profiles) } }
            .onFailure { e -> update { copy(loading = false, error = e.message ?: "Could not load users.") } }
    }

    fun openSolvedUsers() = update { copy(screen = ScreenState.AdminSolvedUsers, error = null) }
    fun openUserManagement() = update { copy(screen = ScreenState.AdminUserManagement, error = null, success = null) }

    fun loadSolvedUsers() = viewModelScope.launch {
        if (!SupabaseProvider.enabled) {
            update { copy(resolvedUsers = listOf(ResolvedUser("demo-user", 2, 481, "Black textures in Call of Duty"))) }
            return@launch
        }
        update { copy(loading = true, error = null) }
        runCatching {
            val profiles = repo.allProfiles().associateBy { it.id }
            val solved = repo.resolvedIssues()
            solved.groupBy { it.userId.orEmpty() }.mapNotNull { (userId, userIssues) ->
                val sorted = userIssues.sortedByDescending { it.createdAt.orEmpty() }
                val latest = sorted.firstOrNull() ?: return@mapNotNull null
                ResolvedUser(
                    username = profiles[userId]?.username ?: "user-${userId.take(8)}",
                    issueCount = userIssues.size,
                    latestIssueNumber = latest.issueNumber,
                    latestTitle = latest.title
                )
            }.sortedBy { it.username.lowercase() }
        }.onSuccess { list -> update { copy(loading = false, resolvedUsers = list) } }
         .onFailure { e -> update { copy(loading = false, error = e.message) } }
    }

    fun openCreate() = update {
        copy(
            screen = ScreenState.CreateIssue,
            error = null,
            success = null,
            selectedFiles = emptyList(),
            game = defaultGame,
            problemType = defaultProblemType,
            driver = defaultDriver,
            emulator = defaultEmulator,
            emulatorVersion = defaultEmulatorVersion,
            dxvk = defaultDxvk,
            wine = defaultWine,
            proton = defaultProton,
            vkd3d = defaultVkd3d,
            box64 = defaultBox64
        )
    }

    fun addFiles(files: List<PickedFile>) = update {
        val existing = selectedFiles.map { it.uri }.toSet()
        copy(selectedFiles = selectedFiles + files.filter { it.uri !in existing })
    }

    fun removeFile(index: Int) = update {
        copy(selectedFiles = selectedFiles.toMutableList().apply { removeAt(index) })
    }

    fun clearSelectedFiles() = update { copy(selectedFiles = emptyList(), success = "Selected attachments cleared.", error = null) }

    fun submitIssue() = viewModelScope.launch {
        val s = _ui.value
        if (s.title.isBlank() || s.game.isBlank() || s.description.isBlank()) {
            update { copy(error = "Title, game, and description are required.") }
            return@launch
        }
        if (s.selectedFiles.none { it.type == "log" || it.type == "video" }) {
            update { copy(error = "A diagnostic log file or a video is required. Images alone are not enough. Attach the evidence and try again.") }
            return@launch
        }
        if (!SupabaseProvider.enabled) {
            update {
                copy(
                    loading = false,
                    success = "Demo issue created locally.",
                    screen = ScreenState.UserHome,
                    selectedFiles = emptyList(),
                    issues = demoIssues()
                )
            }
            return@launch
        }
        update { copy(loading = true, error = null, success = null, uploadProgress = "Preparing diagnostic submission…") }
        val deviceSnapshot = if (s.includeDeviceDiagnostics) DiagnosticCollector.snapshot(getApplication()) else null
        runCatching {
            val automaticDeviceReport = if (s.includeDeviceDiagnostics) {
                DiagnosticCollector.createReportFile(getApplication(), includeDeviceDetails = true)
            } else null
            val filesToUpload = (s.selectedFiles + listOfNotNull(automaticDeviceReport))
                .distinctBy { it.name to it.uri }
            val issue = repo.createIssue(
                CreateIssueInput(
                    userId = s.profile?.id ?: error("Not authenticated"),
                    title = s.title,
                    description = s.description,
                    problemType = s.problemType,
                    game = s.game,
                    gameVersion = s.gameVersion.ifBlank { null },
                    driver = s.driver,
                    emulator = s.emulator,
                    emulatorVersion = s.emulatorVersion.ifBlank { null },
                    dxvk = s.dxvk.ifBlank { null },
                    wine = s.wine.ifBlank { null },
                    proton = s.proton.ifBlank { null },
                    vkd3d = s.vkd3d.ifBlank { null },
                    box64 = s.box64.ifBlank { null },
                    gpu = if (deviceSnapshot != null) s.gpu.ifBlank { deviceSnapshot.gpu } else "Not shared by user",
                    soc = if (deviceSnapshot != null) s.soc.ifBlank { deviceSnapshot.soc } else "Not shared by user",
                    androidVersion = deviceSnapshot?.androidVersion ?: "Not shared by user",
                    deviceModel = deviceSnapshot?.let { "${it.manufacturer} ${it.model}" } ?: "Not shared by user"
                )
            )
            try {
                uploadFiles(issue.id, filesToUpload)
                issue
            } catch (uploadError: Throwable) {
                runCatching { repo.deleteIssue(issue.id) }
                throw IllegalStateException("Issue was not sent because an attachment failed to upload. Please check the file and try again.", uploadError)
            }
        }.onSuccess { issue ->
            update {
                copy(
                    loading = false,
                    uploadProgress = null,
                    screen = ScreenState.UserHome,
                    success = "Issue #${issue.issueNumber} submitted.",
                    selectedFiles = emptyList(),
                    title = "",
                    description = "",
                    game = ""
                )
            }
            _ui.value.profile?.id?.let(::loadUserData)
        }.onFailure { e -> update { copy(loading = false, uploadProgress = null, error = e.message ?: "Submission failed") } }
    }

    private suspend fun uploadFiles(issueId: String, files: List<PickedFile>, onFileUploaded: ((PickedFile) -> Unit)? = null) {
        val resolver = getApplication<Application>().contentResolver
        files.forEachIndexed { index, file ->
            val bytes = withContext(Dispatchers.IO) {
                resolver.openInputStream(file.uri)?.use { input -> input.readBytes() }
            } ?: error("Unable to read ${file.name}. Please choose the file again.")
            require(bytes.size <= 150L * 1024L * 1024L) { "${file.name} exceeds the 150 MB limit." }
            var lastError: Throwable? = null
            for (attempt in 1..3) {
                update { copy(uploadProgress = "Uploading ${index + 1}/${files.size}: ${file.name} (attempt $attempt/3)") }
                try {
                    repo.uploadAttachment(issueId, file.name, file.mime, bytes, file.type)
                    onFileUploaded?.invoke(file)
                    lastError = null
                    break
                } catch (e: Throwable) {
                    lastError = e
                    if (attempt < 3) delay(700L * attempt)
                }
            }
            if (lastError != null) throw IllegalStateException("Upload failed for ${file.name} after 3 attempts: ${lastError.message ?: "network or storage error"}", lastError)
        }
        update { copy(uploadProgress = "Verifying uploaded attachments…") }
    }

    fun openUserIssue(issue: Issue) = openIssue(issue, admin = false)
    fun openAdminIssue(issue: Issue) = openIssue(issue, admin = true)

    private fun openIssue(issue: Issue, admin: Boolean) = viewModelScope.launch {
        update {
            copy(
                screen = if (admin) ScreenState.AdminIssue(issue) else ScreenState.UserIssue(issue),
                attachments = emptyList(),
                chatMessages = emptyList(),
                chatDraft = "",
                chatTypingStates = emptyList(),
                chatNotificationMode = "all",
                chatMarkImportant = false,
                selectedFiles = emptyList(),
                loading = true,
                error = null
            )
        }
        if (!SupabaseProvider.enabled) {
            update { copy(loading = false) }
            return@launch
        }
        runCatching { repo.attachments(issue.id) }
            .onSuccess { files -> update { copy(loading = false, attachments = files) } }
            .onFailure { e -> update { copy(loading = false, error = e.message) } }
        loadIssueMessages(issue.id)
    }

    fun uploadAdditionalFiles(issueId: String) = viewModelScope.launch {
        val files = _ui.value.selectedFiles
        if (files.isEmpty()) {
            update { copy(error = "Choose at least one file first.") }
            return@launch
        }
        if (!SupabaseProvider.enabled) {
            update { copy(error = "File uploads require the cloud database.") }
            return@launch
        }
        update { copy(loading = true, error = null, success = null, uploadProgress = "Preparing upload…") }
        runCatching {
            uploadFiles(issueId, files) { uploaded ->
                update { copy(selectedFiles = selectedFiles.filterNot { it.uri == uploaded.uri }) }
            }
            repo.attachments(issueId)
        }.onSuccess { refreshed ->
            update {
                copy(
                    loading = false,
                    uploadProgress = null,
                    attachments = refreshed,
                    selectedFiles = emptyList(),
                    success = "Files added to this issue."
                )
            }
        }.onFailure { e ->
            runCatching { repo.attachments(issueId) }.onSuccess { refreshed ->
                update { copy(loading = false, uploadProgress = null, attachments = refreshed, error = "Some files may have uploaded. Review attachments, then retry if needed. ${e.message ?: "Upload failed."}") }
            }.onFailure {
                update { copy(loading = false, uploadProgress = null, error = e.message ?: "Upload failed. Please try again.") }
            }
        }
    }

    fun setChatDraft(value: String) {
        val clean = value.take(4000)
        update { copy(chatDraft = clean) }
        val issueId = when (val current = _ui.value.screen) {
            is ScreenState.UserIssue -> current.issue.id
            is ScreenState.AdminIssue -> current.issue.id
            else -> null
        } ?: return

        if (clean.isNotBlank()) lastChatTypingAt = System.currentTimeMillis()
        typingUpdateJob?.cancel()
        typingUpdateJob = viewModelScope.launch {
            if (clean.isBlank()) {
                runCatching { repo.updateTypingState(issueId, false) }
                update { copy(chatTypingStates = chatTypingStates.filterNot { it.userId == _ui.value.profile?.id }) }
            } else {
                delay(350)
                if (System.currentTimeMillis() - lastChatTypingAt < 8_000L) {
                    runCatching { repo.updateTypingState(issueId, true) }
                }
            }
        }
    }

    fun setChatMarkImportant(important: Boolean) = update { copy(chatMarkImportant = important) }

    fun setChatNotificationMode(issueId: String, mode: String) = viewModelScope.launch {
        if (mode !in setOf("all", "important", "muted")) return@launch
        runCatching { repo.setIssueNotificationMode(issueId, mode) }
            .onSuccess {
                update {
                    copy(
                        chatNotificationMode = mode,
                        success = when (mode) {
                            "important" -> "Only important replies will alert you for this issue."
                            "muted" -> "Notifications muted for this issue. Messages are still saved."
                            else -> "All reply notifications enabled for this issue."
                        },
                        error = null
                    )
                }
            }
            .onFailure {
                update {
                    copy(error = "Notification settings need chat_read_receipts_typing_preferences.sql applied in Supabase.")
                }
            }
    }

    fun markIssueMessagesRead(issueId: String) = viewModelScope.launch {
        val readerId = _ui.value.profile?.id ?: return@launch
        if (!SupabaseProvider.enabled) return@launch
        runCatching {
            repo.markIssueMessagesRead(issueId, readerId)
            repo.issueMessages(issueId)
        }.onSuccess { messages ->
            update { copy(chatMessages = messages) }
        }.onFailure {
            update {
                copy(error = "Read receipts need chat_read_receipts_typing_preferences.sql applied in Supabase.")
            }
        }
    }

    fun refreshIssueMessages(issueId: String) = loadIssueMessages(issueId)

    /**
     * Called while the activity is in the foreground. It refreshes both presence and the
     * in-app notification feed. A persistent cursor means replies received while the app
     * was closed can still be surfaced when the user next opens it.
     */
    fun refreshChatSignals() = viewModelScope.launch {
        val profile = _ui.value.profile ?: return@launch
        if (!SupabaseProvider.enabled) return@launch
        val prefs = getApplication<Application>().getSharedPreferences("gmailgpu_settings", 0)
        val cursorKey = "gmailgpu_notification_cursor_${profile.id}"
        val cursor = prefs.getString(cursorKey, null)

        val notifications = runCatching { repo.notifications(profile.id) }.getOrNull()
        val presence = runCatching { repo.visiblePresence() }.getOrNull()
        if (presence != null) update { copy(presenceRecords = presence) }
        if (notifications != null) {
            val latest = notifications.mapNotNull { it.createdAt }.maxOrNull()
            if (cursor == null) {
                // First run: establish a baseline without firing old historical alerts.
                if (latest != null) prefs.edit().putString(cursorKey, latest).apply()
            } else {
                notifications
                    .filter { notification ->
                        notification.createdAt != null &&
                            notification.createdAt > cursor &&
                            notification.title.contains("reply", ignoreCase = true) &&
                            !notification.issueId.isNullOrBlank()
                    }
                    .sortedBy { it.createdAt.orEmpty() }
                    .forEach { notification ->
                        GMailGpuPush.showOnce(
                            getApplication(),
                            notification.id,
                            notification.title,
                            notification.message,
                            notification.issueId.orEmpty()
                        )
                    }
                if (latest != null && latest > cursor) prefs.edit().putString(cursorKey, latest).apply()
            }
            update { copy(notifications = notifications) }
        }
    }

    fun setPresenceOnline(online: Boolean) = viewModelScope.launch {
        if (_ui.value.profile == null || !SupabaseProvider.enabled) return@launch
        runCatching {
            repo.updatePresence(online)
            repo.visiblePresence()
        }.onSuccess { records ->
            update { copy(presenceRecords = records) }
        }
    }

    fun registerPushToken(token: String) = viewModelScope.launch {
        if (_ui.value.profile == null || !SupabaseProvider.enabled || token.isBlank()) return@launch
        runCatching { repo.registerPushToken(token) }
        // Keep token registration failures non-blocking: issue chat must still work.
    }

    fun openIssueFromNotification(issueId: String) = viewModelScope.launch {
        val profile = _ui.value.profile ?: return@launch
        if (!SupabaseProvider.enabled || issueId.isBlank()) return@launch
        runCatching { repo.issueById(issueId) }
            .onSuccess { issue -> openIssue(issue, admin = profile.role == "admin") }
            .onFailure { failure ->
                update { copy(error = "Could not open the issue from this notification. Refresh your issue list and try again. ${failure.message.orEmpty()}") }
                if (profile.role == "admin") loadIssues(true) else loadUserData(profile.id)
            }
    }

    private fun loadIssueMessages(issueId: String) = viewModelScope.launch {
        if (!SupabaseProvider.enabled) return@launch
        val readerId = _ui.value.profile?.id
        runCatching {
            if (readerId != null) {
                val typingActive = _ui.value.chatDraft.isNotBlank() &&
                    System.currentTimeMillis() - lastChatTypingAt < 8_000L
                repo.updateTypingState(issueId, typingActive)
            }
            val messages = repo.issueMessages(issueId)
            val profiles = runCatching { repo.allProfiles() }.getOrDefault(_ui.value.userProfiles)
            if (readerId != null) runCatching { repo.markMessagesDelivered(messages, readerId) }
            val refreshed = runCatching { repo.issueMessages(issueId) }.getOrDefault(messages)
            val now = java.time.Instant.now()
            val typing = runCatching { repo.issueTypingStates(issueId) }
                .getOrDefault(emptyList())
                .filter { state ->
                    state.userId != readerId &&
                        runCatching { java.time.Instant.parse(state.expiresAt).isAfter(now) }.getOrDefault(false)
                }
            val mode = repo.issueNotificationMode(issueId)
            listOf(refreshed, typing, mode, profiles)
        }.onSuccess { result ->
            @Suppress("UNCHECKED_CAST")
            val messages = result[0] as List<IssueMessage>
            @Suppress("UNCHECKED_CAST")
            val typing = result[1] as List<IssueTypingState>
            val mode = result[2] as String
            @Suppress("UNCHECKED_CAST")
            val profiles = result[3] as List<Profile>
            update {
                copy(
                    chatMessages = messages,
                    chatTypingStates = typing,
                    chatNotificationMode = mode,
                    userProfiles = profiles
                )
            }
        }.onFailure {
            update { copy(error = "Couldn't load issue chat. Apply supabase_issue_chat.sql in Supabase SQL Editor.") }
        }
    }

    fun sendIssueMessage(issueId: String) = viewModelScope.launch {
        val state = _ui.value
        val body = state.chatDraft.trim()
        val important = state.chatMarkImportant
        if (body.isBlank()) {
            update { copy(error = "Write a message first.") }
            return@launch
        }
        if (!SupabaseProvider.enabled) {
            update { copy(error = "Issue chat requires the Supabase cloud database.") }
            return@launch
        }
        update { copy(loading = true, error = null, success = null) }
        runCatching {
            repo.sendIssueMessage(issueId, body, important)
            runCatching { repo.updateTypingState(issueId, false) }
            repo.issueMessages(issueId)
        }.onSuccess { messages ->
            lastChatTypingAt = 0L
            update {
                copy(
                    loading = false,
                    chatMessages = messages,
                    chatDraft = "",
                    chatMarkImportant = false,
                    success = "Message sent."
                )
            }
        }.onFailure {
            update { copy(loading = false, error = "Message could not be sent. Apply chat_read_receipts_typing_preferences.sql if chat upgrades are not active.") }
        }
    }

    fun updateStatus(issueId: String, status: String) = viewModelScope.launch {
        if (!SupabaseProvider.enabled) {
            updateIssueInMemory(issueId, status)
            return@launch
        }
        update { copy(loading = true, error = null) }
        runCatching { repo.updateStatus(issueId, status) }
            .onSuccess {
                updateIssueInMemory(issueId, status)
                update { copy(loading = false, success = "Status updated") }
                loadIssues(true)
            }
            .onFailure { e -> update { copy(loading = false, error = e.message) } }
    }

    fun saveDeveloperNote(issueId: String, note: String) = viewModelScope.launch {
        if (!SupabaseProvider.enabled) {
            update { copy(success = "Note saved in demo mode") }
            return@launch
        }
        runCatching { repo.updateDeveloperNote(issueId, note) }
            .onSuccess {
                update { copy(success = "Developer note saved") }
            }
            .onFailure { e -> update { copy(error = e.message) } }
    }

    fun deleteIssue(issue: Issue, admin: Boolean) = viewModelScope.launch {
        if (!SupabaseProvider.enabled) {
            update { copy(issues = issues.filterNot { it.id == issue.id }, screen = if (admin) ScreenState.AdminHome else ScreenState.UserHome, success = "Issue deleted") }
            return@launch
        }
        update { copy(loading = true, error = null) }
        runCatching { repo.deleteIssue(issue.id) }
            .onSuccess {
                update {
                    copy(
                        loading = false,
                        issues = issues.filterNot { it.id == issue.id },
                        screen = if (admin) ScreenState.AdminHome else ScreenState.UserHome,
                        success = "Issue #${issue.issueNumber} deleted."
                    )
                }
            }
            .onFailure { e -> update { copy(loading = false, error = e.message ?: "Delete failed") } }
    }

    fun openAttachment(attachment: Attachment) = viewModelScope.launch {
        if (!SupabaseProvider.enabled) return@launch
        update { copy(loading = true, error = null, attachmentPreviewName = null, attachmentPreviewText = null, attachmentPreviewUrl = null) }
        runCatching {
            val url = repo.signedUrl(attachment.storagePath)
            val lowerName = attachment.filename.lowercase(Locale.ROOT)
            val isReadableLog = attachment.type == "log" &&
                (lowerName.endsWith(".log") || lowerName.endsWith(".txt") ||
                 lowerName.endsWith(".json") || lowerName.endsWith(".xml") ||
                 lowerName.endsWith(".csv") || attachment.mimeType.orEmpty().startsWith("text/"))
            if (!isReadableLog) return@runCatching url to null
            val preview = withContext(Dispatchers.IO) {
                val connection = URL(url).openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = 15000
                    connection.readTimeout = 20000
                    connection.inputStream.use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        var remaining = 300_000
                        while (remaining > 0) {
                            val count = input.read(buffer, 0, minOf(buffer.size, remaining))
                            if (count <= 0) break
                            output.write(buffer, 0, count)
                            remaining -= count
                        }
                        output.toString(Charsets.UTF_8.name())
                    }
                } finally {
                    connection.disconnect()
                }
            }
            url to preview
        }.onSuccess { (url, preview) ->
            update {
                copy(
                    loading = false,
                    openUrl = if (preview == null) url else null,
                    attachmentPreviewName = if (preview == null) null else attachment.filename,
                    attachmentPreviewUrl = if (preview == null) null else url,
                    attachmentPreviewText = preview?.let { if (it.length > 12000) it.take(12000) + "\n\n[Preview shortened. Open the file externally for the rest.]" else it },
                    error = null
                )
            }
        }.onFailure { e ->
            update {
                copy(
                    loading = false,
                    error = "Could not open attachment. Confirm the admin role and run supabase_fix_attachment_access.sql in Supabase SQL Editor. Details: ${e.message ?: "storage access denied"}"
                )
            }
        }
    }

    fun downloadAttachment(attachment: Attachment) = viewModelScope.launch {
        if (!SupabaseProvider.enabled) return@launch
        update { copy(loading = true, error = null, downloadUrl = null, downloadFilename = null, downloadMimeType = null) }
        runCatching { repo.signedUrl(attachment.storagePath) }
            .onSuccess { url ->
                update {
                    copy(
                        loading = false,
                        downloadUrl = url,
                        downloadFilename = attachment.filename,
                        downloadMimeType = attachment.mimeType ?: "application/octet-stream"
                    )
                }
            }
            .onFailure { e ->
                update {
                    copy(
                        loading = false,
                        error = "Download failed. Check the Supabase attachment read policy and the admin role. Details: ${e.message ?: "storage access denied"}"
                    )
                }
            }
    }

    fun clearDownloadRequest() = update { copy(downloadUrl = null, downloadFilename = null, downloadMimeType = null) }

    fun closeAttachmentPreview() = update { copy(attachmentPreviewName = null, attachmentPreviewText = null, attachmentPreviewUrl = null) }

    fun openAttachmentExternally() = update {
        copy(openUrl = attachmentPreviewUrl, attachmentPreviewName = null, attachmentPreviewText = null, attachmentPreviewUrl = null)
    }
    fun clearOpenUrl() = update { copy(openUrl = null) }

    fun markNotificationRead(notification: Notification) = viewModelScope.launch {
        if (!SupabaseProvider.enabled || notification.readAt != null) return@launch
        runCatching { repo.markNotificationRead(notification.id) }
            .onSuccess { update { copy(notifications = notifications.map { if (it.id == notification.id) it.copy(readAt = "read") else it }) } }
    }

    fun back() {
        val current = _ui.value.screen
        val issueId = when (current) {
            is ScreenState.UserIssue -> current.issue.id
            is ScreenState.AdminIssue -> current.issue.id
            else -> null
        }
        typingUpdateJob?.cancel()
        if (issueId != null) viewModelScope.launch { runCatching { repo.updateTypingState(issueId, false) } }
        lastChatTypingAt = 0L
        update {
            copy(
                screen = when (profile?.role) {
                    "admin" -> ScreenState.AdminHome
                    else -> ScreenState.UserHome
                },
                attachments = emptyList(),
                chatDraft = "",
                chatTypingStates = emptyList(),
                chatMarkImportant = false,
                error = null
            )
        }
    }

    fun logout() = viewModelScope.launch {
        val prefs = getApplication<Application>().getSharedPreferences("gmailgpu_settings", 0)
        if (SupabaseProvider.enabled) {
            val token = prefs.getString("gmailgpu_pending_fcm_token", null)
            runCatching { repo.updatePresence(false) }
            if (!token.isNullOrBlank()) runCatching { repo.deletePushToken(token) }
            runCatching { repo.signOut() }
        }
        val savedTheme = prefs.getString("theme_choice", "Violet") ?: "Violet"
        val savedLanguage = prefs.getString("app_language", "system") ?: "system"
        _ui.value = UiState(
            themeChoice = savedTheme,
            appLanguage = savedLanguage,
            includeDeviceDiagnostics = prefs.getBoolean("include_device_diagnostics", true),
            collectAppLogsEnabled = prefs.getBoolean("collect_app_logs", false),
            redactSensitiveLogs = prefs.getBoolean("redact_sensitive_logs", true)
        )
    }

    private fun updateIssueInMemory(issueId: String, status: String) {
        update {
            val updatedIssues = issues.map { if (it.id == issueId) it.copy(status = status) else it }
            val updatedScreen = when (val current = screen) {
                is ScreenState.AdminIssue -> if (current.issue.id == issueId) ScreenState.AdminIssue(current.issue.copy(status = status)) else current
                is ScreenState.UserIssue -> if (current.issue.id == issueId) ScreenState.UserIssue(current.issue.copy(status = status)) else current
                else -> current
            }
            copy(issues = updatedIssues, screen = updatedScreen)
        }
    }

    private fun update(block: UiState.() -> UiState) { _ui.value = _ui.value.block() }

    private fun demoIssues(): List<Issue> = listOf(
        Issue("demo-482", 482, "demo-user", null, "GTA IV crashes after entering gameplay", "Game launches successfully, then crashes after entering gameplay.", "Crash", "open", "GTA IV", driver = "Mali 26.2", emulator = "Winlator 10.1", dxvk = "2.4.1", wine = "9.2", gpu = "Mali-G57 MC2", soc = "Dimensity 6100+", androidVersion = "Android 15"),
        Issue("demo-481", 481, "demo-user2", null, "Black textures in Call of Duty", "Textures become black while the game remains responsive.", "Graphics", "investigating", "Call of Duty Black Ops", driver = "Mali 26.2", emulator = "GameHub", dxvk = "2.4.1", wine = "9.2", gpu = "Mali-G57 MC2", soc = "Helio G99", androidVersion = "Android 14"),
        Issue("demo-480", 480, "demo-user3", null, "Vulkan device lost during loading", "The emulator reports a Vulkan device loss during map loading.", "Vulkan Error", "testing", "Skyrim", driver = "Mali 26.2", emulator = "Winlator", dxvk = "1.10.3", wine = "9.2", gpu = "Mali-G57 MC2", androidVersion = "Android 15")
    )
}
