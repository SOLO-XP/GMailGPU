package com.g57.issuehub

import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.g57.issuehub.data.SupabaseProvider
import com.g57.issuehub.ui.DiagnosticCollector
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.UUID
import kotlin.time.Duration.Companion.minutes

private val gameLabJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

@Serializable
private data class LabReport(
    val id: String,
    @SerialName("user_id") val userId: String,
    val username: String = "community member",
    @SerialName("game_name") val gameName: String,
    @SerialName("game_version") val gameVersion: String? = null,
    @SerialName("gpu_family") val gpuFamily: String,
    @SerialName("device_model") val deviceModel: String? = null,
    val soc: String? = null,
    @SerialName("android_version") val androidVersion: String? = null,
    @SerialName("driver_type") val driverType: String,
    @SerialName("driver_version") val driverVersion: String? = null,
    @SerialName("emulator_name") val emulatorName: String,
    @SerialName("emulator_version") val emulatorVersion: String? = null,
    @SerialName("wine_version") val wineVersion: String? = null,
    @SerialName("dxvk_version") val dxvkVersion: String? = null,
    @SerialName("vkd3d_version") val vkd3dVersion: String? = null,
    @SerialName("box64_version") val box64Version: String? = null,
    val resolution: String? = null,
    @SerialName("graphics_preset") val graphicsPreset: String? = null,
    @SerialName("average_fps") val averageFps: Double? = null,
    @SerialName("minimum_fps") val minimumFps: Double? = null,
    @SerialName("test_duration_seconds") val testDurationSeconds: Int? = null,
    @SerialName("device_temperature_c") val deviceTemperatureC: Double? = null,
    val outcome: String,
    @SerialName("graphics_issues") val graphicsIssues: String? = null,
    val notes: String,
    @SerialName("evidence_paths") val evidencePaths: List<String> = emptyList(),
    @SerialName("review_status") val reviewStatus: String = "community",
    @SerialName("evidence_reviewed") val evidenceReviewed: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
private data class GameWatchItem(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("game_name") val gameName: String,
    @SerialName("gpu_family") val gpuFamily: String = "Any GPU",
    @SerialName("driver_type") val driverType: String = "Any",
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
private data class GameWatchNotification(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("report_id") val reportId: String,
    @SerialName("game_name") val gameName: String,
    val message: String,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("read_at") val readAt: String? = null
)

@Serializable
private data class GameSavedConfiguration(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("report_id") val reportId: String? = null,
    @SerialName("config_name") val configName: String,
    @SerialName("game_name") val gameName: String,
    @SerialName("gpu_family") val gpuFamily: String,
    @SerialName("driver_type") val driverType: String,
    @SerialName("driver_version") val driverVersion: String? = null,
    @SerialName("device_model") val deviceModel: String? = null,
    val soc: String? = null,
    @SerialName("emulator_name") val emulatorName: String,
    @SerialName("emulator_version") val emulatorVersion: String? = null,
    @SerialName("wine_version") val wineVersion: String? = null,
    @SerialName("dxvk_version") val dxvkVersion: String? = null,
    @SerialName("vkd3d_version") val vkd3dVersion: String? = null,
    @SerialName("box64_version") val box64Version: String? = null,
    val resolution: String? = null,
    @SerialName("graphics_preset") val graphicsPreset: String? = null,
    val notes: String? = null,
    @SerialName("is_public") val isPublic: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
private data class GameTesterProfile(
    @SerialName("user_id") val userId: String,
    @SerialName("display_name") val displayName: String,
    val bio: String = "",
    @SerialName("is_public") val isPublic: Boolean = false,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
private data class GameReportFlag(
    val id: String,
    @SerialName("report_id") val reportId: String,
    @SerialName("reporter_id") val reporterId: String,
    val reason: String,
    val details: String = "",
    val status: String = "open",
    @SerialName("created_at") val createdAt: String? = null
)

private data class DriverRelease(
    val repository: String,
    val latestVersion: String,
    val publishedAt: String,
    val url: String,
    val status: String
)

private val gameLabGpuChoices = listOf("Any GPU", "Mali-G52", "Mali-G57", "Mali-G615", "Mali-G720", "Other")
private val gameLabDriverChoices = listOf("Any", "System Driver", "PanVK", "Other")
private val gameLabRepos = listOf(
    "Noysz/panvk-g99-jm",
    "mexicanbr0auth/mesa-panvk-g57",
    "FristOneRR/FristOneRR-Panvk-Driver",
    "LukeValen/panvk-mali-g52",
    "GunaCharanTeja/panvk-kbase-android",
    "wonderkast02/panvk-g720-kbase-csf",
    "GunaCharanTeja/WinlatorMali"
)

@Composable
internal fun GameLabScreen(
    onBack: () -> Unit,
    onOpenGameLibrary: () -> Unit,
    onCreateIssue: (title: String, game: String, type: String, description: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val client = remember { SupabaseProvider.client }
    val userId = remember { client.auth.currentUserOrNull()?.id.orEmpty() }
    val userPrefs = remember { context.getSharedPreferences("gmailgpu_settings", 0) }
    var activeTab by remember { mutableStateOf("Overview") }
    var username by remember { mutableStateOf("community member") }
    var isAdmin by remember { mutableStateOf(false) }
    var reports by remember { mutableStateOf(emptyList<LabReport>()) }
    var watchlist by remember { mutableStateOf(emptyList<GameWatchItem>()) }
    var watchNotifications by remember { mutableStateOf(emptyList<GameWatchNotification>()) }
    var savedConfigs by remember { mutableStateOf(emptyList<GameSavedConfiguration>()) }
    var testerProfile by remember { mutableStateOf<GameTesterProfile?>(null) }
    var publicTesters by remember { mutableStateOf(emptyList<GameTesterProfile>()) }
    var reportFlags by remember { mutableStateOf(emptyList<GameReportFlag>()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var usingOfflineCache by remember { mutableStateOf(false) }

    var watchGame by remember { mutableStateOf("") }
    var watchGpu by remember { mutableStateOf("Any GPU") }
    var watchDriver by remember { mutableStateOf("Any") }
    var configName by remember { mutableStateOf("") }
    var configGame by remember { mutableStateOf("") }
    var configGpu by remember { mutableStateOf("Mali-G57") }
    var configDriver by remember { mutableStateOf("System Driver") }
    var configDriverVersion by remember { mutableStateOf("") }
    var configDevice by remember { mutableStateOf("") }
    var configSoc by remember { mutableStateOf("") }
    var configEmulator by remember { mutableStateOf("Winlator") }
    var configEmulatorVersion by remember { mutableStateOf("") }
    var configWine by remember { mutableStateOf("") }
    var configDxvk by remember { mutableStateOf("") }
    var configVkd3d by remember { mutableStateOf("") }
    var configBox64 by remember { mutableStateOf("") }
    var configResolution by remember { mutableStateOf("") }
    var configPreset by remember { mutableStateOf("") }
    var configNotes by remember { mutableStateOf("") }
    var configPublic by remember { mutableStateOf(false) }

    var profileName by remember { mutableStateOf("") }
    var profileBio by remember { mutableStateOf("") }
    var profilePublic by remember { mutableStateOf(false) }

    var troubleGame by remember { mutableStateOf("") }
    var troubleType by remember { mutableStateOf("Black textures / missing graphics") }
    var troubleStep by remember { mutableIntStateOf(0) }

    var logFilename by remember { mutableStateOf<String?>(null) }
    var logAnalysis by remember { mutableStateOf<String?>(null) }
    var logCategoryCounts by remember { mutableStateOf(emptyList<Pair<String, Int>>()) }
    var latestReleases by remember { mutableStateOf(emptyList<DriverRelease>()) }
    var loadingReleases by remember { mutableStateOf(false) }

    fun refreshData() {
        if (userId.isBlank()) {
            error = "Sign in to use your personal Game Lab tools."
            return
        }
        scope.launch {
            loading = true
            error = null
            var loadedCloud = false
            runCatching {
                val profile = client.from("profiles").select(Columns.ALL) {
                    filter { eq("id", userId) }
                }.decodeSingle<com.g57.issuehub.data.Profile>()
                username = profile.username
                isAdmin = profile.role.equals("admin", true)
                val freshReports = client.from("game_compatibility_reports").select(Columns.ALL) {
                    order(column = "created_at", order = Order.DESCENDING)
                }.decodeList<LabReport>()
                reports = freshReports
                userPrefs.edit().putString("gmailgpu_game_lab_cache", gameLabJson.encodeToString(freshReports)).apply()
                watchlist = client.from("game_watchlist").select(Columns.ALL) {
                    filter { eq("user_id", userId) }
                    order(column = "created_at", order = Order.DESCENDING)
                }.decodeList<GameWatchItem>()
                watchNotifications = client.from("game_watch_notifications").select(Columns.ALL) {
                    filter { eq("user_id", userId) }
                    order(column = "created_at", order = Order.DESCENDING)
                }.decodeList<GameWatchNotification>()
                savedConfigs = client.from("game_saved_configurations").select(Columns.ALL) {
                    filter { eq("user_id", userId) }
                    order(column = "created_at", order = Order.DESCENDING)
                }.decodeList<GameSavedConfiguration>()
                userPrefs.edit().putString("gmailgpu_saved_configs_cache", gameLabJson.encodeToString(savedConfigs)).apply()
                val profiles = client.from("game_tester_profiles").select(Columns.ALL) {
                    filter { eq("user_id", userId) }
                }.decodeList<GameTesterProfile>()
                publicTesters = client.from("game_tester_profiles").select(Columns.ALL) {
                    filter { eq("is_public", true) }
                }.decodeList<GameTesterProfile>().filterNot { it.userId == userId }
                if (profile.role.equals("admin", true)) {
                    reportFlags = client.from("game_report_flags").select(Columns.ALL) {
                        order(column = "created_at", order = Order.DESCENDING)
                    }.decodeList<GameReportFlag>()
                }
                testerProfile = profiles.firstOrNull()
                profileName = testerProfile?.displayName ?: profile.username
                profileBio = testerProfile?.bio.orEmpty()
                profilePublic = testerProfile?.isPublic ?: false
                loadedCloud = true
            }.onFailure { failure ->
                val cached = userPrefs.getString("gmailgpu_game_lab_cache", null)
                if (cached != null) {
                    runCatching { gameLabJson.decodeFromString<List<LabReport>>(cached) }
                        .onSuccess {
                            reports = it
                            usingOfflineCache = true
                            notice = "Offline mode: showing the most recently cached community reports."
                        }
                }
                userPrefs.getString("gmailgpu_saved_configs_cache", null)?.let { configCache ->
                    runCatching { gameLabJson.decodeFromString<List<GameSavedConfiguration>>(configCache) }
                        .onSuccess { savedConfigs = it }
                }
                error = if (cached != null) {
                    "Some cloud tools are unavailable offline. Cached game results remain available."
                } else {
                    "Game Lab setup or network request failed. Run supabase_game_lab_features.sql and check the connection. Details: ${failure.message}"
                }
            }
            if (loadedCloud) {
                usingOfflineCache = false
                notice = null
            }
            loading = false
        }
    }

    LaunchedEffect(userId) { refreshData() }

    fun markWatchNotificationRead(notification: GameWatchNotification) {
        scope.launch {
            runCatching {
                client.from("game_watch_notifications").update(buildJsonObject {
                    put("read_at", java.time.Instant.now().toString())
                }) { filter { eq("id", notification.id); eq("user_id", userId) } }
            }.onSuccess {
                watchNotifications = watchNotifications.map {
                    if (it.id == notification.id) it.copy(readAt = "read") else it
                }
            }.onFailure { error = "Could not update notification: ${it.message}" }
        }
    }

    fun addWatch() {
        if (watchGame.isBlank()) { error = "Enter a game name for your watchlist."; return }
        scope.launch {
            runCatching {
                client.from("game_watchlist").insert(buildJsonObject {
                    put("user_id", userId)
                    put("game_name", watchGame.trim())
                    put("gpu_family", watchGpu)
                    put("driver_type", watchDriver)
                })
            }.onSuccess {
                notice = "Added ${watchGame.trim()} to your watchlist. New matching community reports will create in-app alerts."
                watchGame = ""
                refreshData()
            }.onFailure { error = "Could not add watchlist item. It may already exist, or the Game Lab SQL migration is missing. ${it.message}" }
        }
    }

    fun deleteWatch(item: GameWatchItem) {
        scope.launch {
            runCatching {
                client.from("game_watchlist").delete {
                    filter { eq("id", item.id); eq("user_id", userId) }
                }
            }.onSuccess { watchlist = watchlist.filterNot { it.id == item.id } }
             .onFailure { error = "Could not remove watchlist item: ${it.message}" }
        }
    }

    fun saveConfig() {
        if (configName.isBlank() || configGame.isBlank()) { error = "Configuration name and game name are required."; return }
        scope.launch {
            runCatching {
                client.from("game_saved_configurations").insert(buildJsonObject {
                    put("user_id", userId)
                    put("config_name", configName.trim())
                    put("game_name", configGame.trim())
                    put("gpu_family", configGpu)
                    put("driver_type", configDriver)
                    put("driver_version", configDriverVersion.ifBlank { null })
                    put("device_model", configDevice.ifBlank { null })
                    put("soc", configSoc.ifBlank { null })
                    put("emulator_name", configEmulator.ifBlank { "Winlator / GameHub" })
                    put("emulator_version", configEmulatorVersion.ifBlank { null })
                    put("wine_version", configWine.ifBlank { null })
                    put("dxvk_version", configDxvk.ifBlank { null })
                    put("vkd3d_version", configVkd3d.ifBlank { null })
                    put("box64_version", configBox64.ifBlank { null })
                    put("resolution", configResolution.ifBlank { null })
                    put("graphics_preset", configPreset.ifBlank { null })
                    put("notes", configNotes.ifBlank { null })
                    put("is_public", configPublic)
                })
            }.onSuccess {
                notice = "Configuration saved."
                configName = ""; configGame = ""; configDriverVersion = ""; configDevice = ""; configSoc = ""
                configEmulatorVersion = ""; configWine = ""; configDxvk = ""; configVkd3d = ""; configBox64 = ""
                configResolution = ""; configPreset = ""; configNotes = ""; configPublic = false
                refreshData()
            }.onFailure { error = "Could not save configuration. Run the Game Lab SQL migration first. ${it.message}" }
        }
    }

    fun deleteConfig(config: GameSavedConfiguration) {
        scope.launch {
            runCatching {
                client.from("game_saved_configurations").delete {
                    filter { eq("id", config.id); eq("user_id", userId) }
                }
            }.onSuccess { savedConfigs = savedConfigs.filterNot { it.id == config.id } }
             .onFailure { error = "Could not delete configuration: ${it.message}" }
        }
    }

    fun saveTesterProfile() {
        if (profileName.isBlank()) { error = "Choose a display name for your tester profile."; return }
        scope.launch {
            runCatching {
                val payload = buildJsonObject {
                    put("user_id", userId)
                    put("display_name", profileName.trim())
                    put("bio", profileBio.take(500))
                    put("is_public", profilePublic)
                    put("updated_at", java.time.Instant.now().toString())
                }
                if (testerProfile == null) {
                    client.from("game_tester_profiles").insert(payload)
                } else {
                    client.from("game_tester_profiles").update(payload) {
                        filter { eq("user_id", userId) }
                    }
                }
            }.onSuccess {
                notice = if (profilePublic) "Public tester profile saved." else "Private tester profile saved."
                refreshData()
            }.onFailure { error = "Could not save tester profile. Run the Game Lab SQL migration first. ${it.message}" }
        }
    }

    fun flagReport(report: LabReport) {
        scope.launch {
            runCatching {
                client.from("game_report_flags").insert(buildJsonObject {
                    put("report_id", report.id)
                    put("reporter_id", userId)
                    put("reason", "Misleading result")
                    put("details", "Flagged from GMailGPU Game Lab; please review the evidence.")
                })
            }.onSuccess { notice = "Report sent to moderators for review." }
             .onFailure { error = "Could not report this entry: ${it.message}" }
        }
    }

    fun moderateFlag(flag: GameReportFlag, status: String) {
        if (!isAdmin) return
        scope.launch {
            runCatching {
                client.from("game_report_flags").update(buildJsonObject { put("status", status) }) {
                    filter { eq("id", flag.id) }
                }
            }.onSuccess {
                reportFlags = reportFlags.map { if (it.id == flag.id) it.copy(status = status) else it }
                notice = "Moderation status updated."
            }.onFailure { error = "Unable to update moderation flag: ${it.message}" }
        }
    }

    fun reviewReport(report: LabReport, action: String) {
        if (!isAdmin) return
        scope.launch {
            runCatching {
                when (action) {
                    "reviewed" -> client.from("game_compatibility_reports").update(buildJsonObject {
                        put("evidence_reviewed", true)
                        put("evidence_reviewed_at", java.time.Instant.now().toString())
                        put("evidence_reviewed_by", userId)
                    }) { filter { eq("id", report.id) } }
                    "verified" -> client.from("game_compatibility_reports").update(buildJsonObject {
                        put("review_status", "verified")
                        put("evidence_reviewed", true)
                        put("evidence_reviewed_at", java.time.Instant.now().toString())
                        put("evidence_reviewed_by", userId)
                    }) { filter { eq("id", report.id) } }
                    "rejected" -> client.from("game_compatibility_reports").update(buildJsonObject {
                        put("review_status", "rejected")
                    }) { filter { eq("id", report.id) } }
                }
            }.onSuccess {
                notice = "Review decision saved."
                refreshData()
            }.onFailure { error = "Could not update review status. Verify the admin role and migration. ${it.message}" }
        }
    }

    fun analyzeLog(uri: Uri) {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: throw IllegalStateException("Unable to read this file.")
                    require(raw.length <= 8_000_000) { "For performance, choose a log no larger than about 8 MB of text." }
                    val redacted = raw
                        .replace(Regex("(?i)(password|access_token|refresh_token|api[_-]?key|secret)(\\s*[:=]\\s*)[^\\s,;]+"), "$1$2[REDACTED]")
                        .replace(Regex("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", RegexOption.IGNORE_CASE), "[EMAIL REDACTED]")
                    val rules = listOf(
                        "Vulkan / driver initialization" to Regex("(?i)VK_ERROR_|vkCreate(Device|Instance)|Vulkan.*(fail|error|unsupported)|failed.*Vulkan"),
                        "Out of memory" to Regex("(?i)out.of.memory|VK_ERROR_OUT_OF_(DEVICE|HOST)_MEMORY|allocation failed"),
                        "Crash / device lost" to Regex("(?i)device lost|segmentation fault|fatal error|access violation|SIGSEGV|crash"),
                        "DXVK / VKD3D / Wine" to Regex("(?i)DXVK|VKD3D|wine:|wined3d|d3d11|d3d12|dxgi"),
                        "Graphics / textures" to Regex("(?i)shader|texture|render|framebuffer|pipeline|black screen|corrupt")
                    )
                    val counts = rules.map { (name, pattern) -> name to pattern.findAll(redacted).count() }
                        .filter { it.second > 0 }
                    val important = redacted.lineSequence().filter { line ->
                        Regex("(?i)error|failed|warning|fatal|VK_ERROR|out.of.memory|device lost|segmentation").containsMatchIn(line)
                    }.take(80).toList()
                    val analysis = buildString {
                        appendLine("GMailGPU Diagnostic Log Analyzer")
                        appendLine("File: ${uri.lastPathSegment ?: "selected log"}")
                        appendLine("Lines: ${redacted.lineSequence().count()}")
                        appendLine()
                        appendLine("Matches by category:")
                        if (counts.isEmpty()) appendLine("No known error signatures matched. This does not prove the log is clean.")
                        counts.forEach { appendLine("- ${it.first}: ${it.second} line match(es)") }
                        appendLine()
                        appendLine("Suggested next checks:")
                        if (counts.any { it.first.startsWith("Vulkan") }) appendLine("- Record exact driver build and the first Vulkan error before device creation.")
                        if (counts.any { it.first == "Out of memory" }) appendLine("- Reduce resolution/graphics settings and record available RAM; verify it is a real allocation failure.")
                        if (counts.any { it.first.startsWith("Crash") }) appendLine("- Capture the first fatal error plus the last 100 lines before the crash.")
                        if (counts.any { it.first.startsWith("DXVK") }) appendLine("- Include Wine, DXVK, VKD3D-Proton, Box64 and emulator versions.")
                        if (counts.any { it.first.startsWith("Graphics") }) appendLine("- Attach a screenshot or video and note whether the problem reproduces on System Driver and PanVK.")
                        appendLine()
                        appendLine("Relevant log excerpts (best effort; always inspect for secrets):")
                        important.forEach { appendLine(it.take(500)) }
                    }
                    Pair(counts, analysis)
                }
            }.onSuccess { result ->
                logFilename = uri.lastPathSegment ?: "selected log"
                logCategoryCounts = result.first
                logAnalysis = result.second
                notice = "Log analyzed locally on this device. Nothing was uploaded."
                error = null
            }.onFailure { error = "Could not analyze this file: ${it.message}" }
        }
    }

    fun fetchDriverReleases() {
        scope.launch {
            loadingReleases = true
            val list = withContext(Dispatchers.IO) {
                gameLabRepos.map { repo ->
                    runCatching {
                        val conn = (URL("https://api.github.com/repos/$repo/releases/latest").openConnection() as HttpURLConnection).apply {
                            connectTimeout = 7000
                            readTimeout = 7000
                            setRequestProperty("Accept", "application/vnd.github+json")
                            setRequestProperty("User-Agent", "GMailGPU-Android")
                        }
                        try {
                            val status = conn.responseCode
                            if (status !in 200..299) {
                                DriverRelease(repo, "No published release", "", "https://github.com/$repo/releases", "Open releases page")
                            } else {
                                val payload = conn.inputStream.bufferedReader().use { it.readText() }
                                val json = JSONObject(payload)
                                DriverRelease(
                                    repo,
                                    json.optString("tag_name", "Unknown"),
                                    json.optString("published_at", ""),
                                    json.optString("html_url", "https://github.com/$repo/releases"),
                                    "Latest release from GitHub"
                                )
                            }
                        } finally { conn.disconnect() }
                    }.getOrElse {
                        DriverRelease(repo, "Unable to check", "", "https://github.com/$repo/releases", "Check connection")
                    }
                }
            }
            latestReleases = list
            loadingReleases = false
        }
    }

    fun createDiagnosticPackage() {
        runCatching {
            val prefs = context.getSharedPreferences("gmailgpu_settings", 0)
            DiagnosticCollector.createLogBundle(
                context,
                redactSensitive = prefs.getBoolean("redact_sensitive_logs", true),
                includeAppLogcat = prefs.getBoolean("collect_app_logs", false),
                includeDeviceDetails = prefs.getBoolean("include_device_diagnostics", true)
            )
        }.onSuccess { picked ->
            runCatching {
                val file = File(picked.uri.path ?: error("Diagnostic file location unavailable"))
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "GMailGPU Diagnostic Package")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Share diagnostic package"))
                notice = "Diagnostic package prepared. Review it before sending."
            }.onFailure { error = "Unable to share diagnostic package: ${it.message}" }
        }.onFailure { error = "Unable to create diagnostic package: ${it.message}" }
    }

    fun exportPdf() {
        val snapshot = reports
        scope.launch(Dispatchers.IO) {
            runCatching {
                val file = File(context.cacheDir, "gmailgpu-community-statistics.pdf")
                val document = PdfDocument()
                val paint = Paint().apply { textSize = 11f }
                val titlePaint = Paint().apply { textSize = 17f; isFakeBoldText = true }
                val lines = buildList {
                    add("GMailGPU Community Game Lab")
                    add("Exported: ${java.time.Instant.now()}")
                    add("Community tests: ${snapshot.size}")
                    add("Distinct games: ${snapshot.map { it.gameName.lowercase() }.distinct().size}")
                    add("Verified tests: ${snapshot.count { it.reviewStatus == "verified" }}")
                    add("")
                    snapshot.take(250).forEach { r ->
                        add("${r.gameName} | ${r.gpuFamily} | ${r.driverType} | ${r.outcome}")
                        add("FPS average/min: ${r.averageFps ?: "—"} / ${r.minimumFps ?: "—"}")
                        add("Device: ${r.deviceModel ?: "not provided"} | ${r.emulatorName} ${r.emulatorVersion.orEmpty()}")
                        add("Wine ${r.wineVersion ?: "—"} | DXVK ${r.dxvkVersion ?: "—"} | VKD3D ${r.vkd3dVersion ?: "—"} | Box64 ${r.box64Version ?: "—"}")
                        add("Review: ${if (r.reviewStatus == "verified") "Verified test" else if (r.evidenceReviewed) "Evidence reviewed" else "Community report"}")
                        add("Notes: ${r.notes.take(220)}")
                        add("")
                    }
                }.flatMap { line ->
                    if (line.length <= 88) listOf(line) else line.chunked(88)
                }
                var pageNumber = 1
                var page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber).create())
                var canvas = page.canvas
                var y = 42f
                lines.forEachIndexed { index, line ->
                    if (y > 800f) {
                        document.finishPage(page)
                        pageNumber += 1
                        page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber).create())
                        canvas = page.canvas
                        y = 42f
                    }
                    canvas.drawText(line.take(100), 32f, y, if (index == 0) titlePaint else paint)
                    y += if (index == 0) 28f else 17f
                }
                document.finishPage(page)
                file.outputStream().use { document.writeTo(it) }
                document.close()
                file
            }.onSuccess { file ->
                withContext(Dispatchers.Main) {
                    runCatching {
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "application/pdf"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            putExtra(Intent.EXTRA_SUBJECT, "GMailGPU Community Game Lab")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }, "Export community report"))
                        notice = "PDF export prepared."
                    }.onFailure { error = "Could not share PDF: ${it.message}" }
                }
            }.onFailure { failure ->
                withContext(Dispatchers.Main) { error = "PDF export failed: ${failure.message}" }
            }
        }
    }

    fun shareConfiguration(config: GameSavedConfiguration) {
        val text = buildString {
            appendLine("GMailGPU Saved Game Configuration")
            appendLine("Game: ${config.gameName}")
            appendLine("GPU: ${config.gpuFamily}")
            appendLine("Driver: ${config.driverType} ${config.driverVersion.orEmpty()}")
            appendLine("Device: ${config.deviceModel ?: "not provided"}")
            appendLine("SoC: ${config.soc ?: "not provided"}")
            appendLine("Emulator: ${config.emulatorName} ${config.emulatorVersion.orEmpty()}")
            appendLine("Wine: ${config.wineVersion ?: "—"} | DXVK: ${config.dxvkVersion ?: "—"} | VKD3D: ${config.vkd3dVersion ?: "—"} | Box64: ${config.box64Version ?: "—"}")
            appendLine("Resolution: ${config.resolution ?: "—"}")
            appendLine("Graphics preset: ${config.graphicsPreset ?: "—"}")
            appendLine("Notes: ${config.notes.orEmpty()}")
            appendLine("Public share: ${config.isPublic}")
            appendLine("Versions/settings may need adaptation for other devices.")
        }
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, "GMailGPU: ${config.gameName} configuration")
        }, "Share configuration"))
    }

    val pickLog = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) analyzeLog(uri)
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
            Column(Modifier.weight(1f)) {
                Text("GMailGPU Community Game Lab", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Compatibility • diagnostics • community", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { refreshData() }, enabled = !loading) { Icon(Icons.Default.Refresh, contentDescription = "Refresh") }
        }

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("Overview", "Watchlist", "Configs", "Troubleshoot", "Logs", "Profile & Stats", "Admin Review", "Driver Updates")
                .forEach { tab ->
                    FilterChip(selected = activeTab == tab, onClick = { activeTab = tab }, label = { Text(tab) })
                }
        }
        if (notice != null) Text(notice!!, Modifier.padding(horizontal = 16.dp, vertical = 5.dp), color = MaterialTheme.colorScheme.primary)
        if (error != null) Text(error!!, Modifier.padding(horizontal = 16.dp, vertical = 5.dp), color = MaterialTheme.colorScheme.error)
        if (usingOfflineCache) AssistChip(onClick = { refreshData() }, label = { Icon(Icons.Default.CloudOff, null); Spacer(Modifier.width(5.dp)); Text("Cached offline data • Retry cloud") })
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())

        when (activeTab) {
            "Overview" -> {
                LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 14.dp, bottom = 30.dp)) {
                    item {
                        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Find the right test before changing drivers", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("Search real community evidence for Mali-G52, G57, G615 and G720. Unknown games stay “Not tested”; we never invent a compatibility verdict.")
                                Button(onClick = onOpenGameLibrary, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Default.Search, null); Spacer(Modifier.width(8.dp)); Text("OPEN GAME COMPATIBILITY FINDER")
                                }
                            }
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            MetricCard("TESTS", reports.size.toString(), Modifier.weight(1f))
                            MetricCard("GAMES", reports.map { it.gameName.lowercase() }.distinct().size.toString(), Modifier.weight(1f))
                            MetricCard("VERIFIED", reports.count { it.reviewStatus == "verified" }.toString(), Modifier.weight(1f))
                        }
                    }
                    item {
                        Text("Quick tools", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("• Save known-good versions in Configs. • Monitor games in Watchlist. • Analyze logs locally. • Share a diagnostic package only after review.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    item {
                        Text("Tests by Mali GPU", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        gameLabGpuChoices.filter { it != "Any GPU" }.forEach { gpu ->
                            val count = reports.count { it.gpuFamily == gpu }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(gpu, Modifier.width(105.dp))
                                LinearProgressIndicator(
                                    progress = { if (reports.isEmpty()) 0f else count.toFloat() / reports.size.toFloat() },
                                    modifier = Modifier.weight(1f).height(8.dp)
                                )
                                Text("  $count", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    item {
                        OutlinedButton(onClick = { exportPdf() }, modifier = Modifier.fillMaxWidth(), enabled = reports.isNotEmpty()) {
                            Icon(Icons.Default.PictureAsPdf, null); Spacer(Modifier.width(8.dp)); Text("EXPORT COMMUNITY REPORTS AS PDF")
                        }
                    }
                    item {
                        OutlinedButton(onClick = { createDiagnosticPackage() }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Inventory2, null); Spacer(Modifier.width(8.dp)); Text("CREATE ONE-CLICK DIAGNOSTIC PACKAGE")
                        }
                    }
                }
            }

            "Watchlist" -> {
                LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 14.dp, bottom = 30.dp)) {
                    item { Text("Get an in-app alert when a new matching test is submitted.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    item { OutlinedTextField(watchGame, { watchGame = it }, label = { Text("Game name") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                    item { Text("GPU"); ChoiceChips(gameLabGpuChoices, watchGpu) { watchGpu = it } }
                    item { Text("Driver"); ChoiceChips(gameLabDriverChoices, watchDriver) { watchDriver = it } }
                    item { Button(onClick = { addWatch() }, modifier = Modifier.fillMaxWidth(), enabled = !loading) { Icon(Icons.Default.NotificationsActive, null); Spacer(Modifier.width(7.dp)); Text("ADD TO WATCHLIST") } }
                    item { Text("WATCHED GAMES", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                    if (watchlist.isEmpty()) item { EmptyState("No watched games yet. Add a title above to start monitoring.") }
                    items(watchlist, key = { it.id }) { item ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(item.gameName, fontWeight = FontWeight.Bold)
                                    Text("${item.gpuFamily} • ${item.driverType}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { deleteWatch(item) }) { Icon(Icons.Default.DeleteOutline, contentDescription = "Remove") }
                            }
                        }
                    }
                    item { Text("NEW TEST ALERTS", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                    val unread = watchNotifications.count { it.readAt == null }
                    item { Text("$unread unread alert(s)", color = MaterialTheme.colorScheme.primary) }
                    if (watchNotifications.isEmpty()) item { EmptyState("New tests matching a watchlist item will appear here after the SQL migration is installed.") }
                    items(watchNotifications, key = { it.id }) { notification ->
                        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (notification.readAt == null) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(notification.gameName, fontWeight = FontWeight.Bold)
                                Text(notification.message)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(notification.createdAt.orEmpty().take(10), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                                    if (notification.readAt == null) TextButton(onClick = { markWatchNotificationRead(notification) }) { Text("MARK READ") }
                                    TextButton(onClick = onOpenGameLibrary) { Text("OPEN FINDER") }
                                }
                            }
                        }
                    }
                }
            }

            "Configs" -> {
                LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(vertical = 14.dp, bottom = 30.dp)) {
                    item { Text("Store known-good configurations with the exact driver and translation-layer versions. Public sharing is optional.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    item { Text("CONFIGURATION DETAILS", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                    item { OutlinedTextField(configName, { configName = it }, label = { Text("Configuration name *") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                    item { OutlinedTextField(configGame, { configGame = it }, label = { Text("Game name *") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                    item { Text("GPU"); ChoiceChips(gameLabGpuChoices.filter { it != "Any GPU" }, configGpu) { configGpu = it } }
                    item { Text("Driver"); ChoiceChips(gameLabDriverChoices.filter { it != "Any" }, configDriver) { configDriver = it } }
                    item { OutlinedTextField(configDriverVersion, { configDriverVersion = it }, label = { Text("Driver build / version") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                    item { OutlinedTextField(configDevice, { configDevice = it }, label = { Text("Device model") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                    item { OutlinedTextField(configSoc, { configSoc = it }, label = { Text("SoC") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                    item { OutlinedTextField(configEmulator, { configEmulator = it }, label = { Text("Winlator / GameHub / other") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                    item { OutlinedTextField(configEmulatorVersion, { configEmulatorVersion = it }, label = { Text("Emulator version") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                    item { OutlinedTextField(configWine, { configWine = it }, label = { Text("Wine version") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                    item { OutlinedTextField(configDxvk, { configDxvk = it }, label = { Text("DXVK version") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                    item { OutlinedTextField(configVkd3d, { configVkd3d = it }, label = { Text("VKD3D-Proton version") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                    item { OutlinedTextField(configBox64, { configBox64 = it }, label = { Text("Box64 version") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                    item { OutlinedTextField(configResolution, { configResolution = it }, label = { Text("Resolution") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                    item { OutlinedTextField(configPreset, { configPreset = it }, label = { Text("Graphics preset") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                    item { OutlinedTextField(configNotes, { configNotes = it }, label = { Text("Notes / required tweaks") }, modifier = Modifier.fillMaxWidth(), minLines = 2) }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Share in community", fontWeight = FontWeight.SemiBold)
                                Text("Public configs can be seen by signed-in users.", style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(checked = configPublic, onCheckedChange = { configPublic = it })
                        }
                    }
                    item { Button(onClick = { saveConfig() }, modifier = Modifier.fillMaxWidth(), enabled = !loading) { Icon(Icons.Default.BookmarkAdd, null); Spacer(Modifier.width(8.dp)); Text("SAVE CONFIGURATION") } }
                    item { HorizontalDivider(); Text("SAVED CONFIGURATIONS", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                    if (savedConfigs.isEmpty()) item { EmptyState("Your saved configurations will appear here.") }
                    items(savedConfigs, key = { it.id }) { config ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(config.configName, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                                    if (config.isPublic) AssistChip(onClick = {}, label = { Text("PUBLIC") })
                                }
                                Text("${config.gameName} • ${config.gpuFamily} • ${config.driverType}")
                                Text("${config.emulatorName} ${config.emulatorVersion.orEmpty()} • Wine ${config.wineVersion ?: "—"} • DXVK ${config.dxvkVersion ?: "—"}")
                                Text("VKD3D ${config.vkd3dVersion ?: "—"} • Box64 ${config.box64Version ?: "—"} • ${config.resolution ?: "Resolution not set"}", style = MaterialTheme.typography.bodySmall)
                                if (!config.notes.isNullOrBlank()) Text(config.notes, style = MaterialTheme.typography.bodySmall)
                                Row {
                                    TextButton(onClick = { shareConfig(config, context) }) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(4.dp)); Text("SHARE") }
                                    TextButton(onClick = { deleteConfig(config) }) { Icon(Icons.Default.DeleteOutline, null); Spacer(Modifier.width(4.dp)); Text("DELETE") }
                                }
                            }
                        }
                    }
                }
            }

            "Troubleshoot" -> {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Smart Troubleshooter & Report Wizard", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Choose the symptom; the app gives a safe checklist and can prefill a complete issue report. It won't claim the driver is the cause without evidence.")
                    OutlinedTextField(troubleGame, { troubleGame = it }, label = { Text("Game name (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    listOf(
                        "Black textures / missing graphics",
                        "Black screen",
                        "Vulkan initialization error",
                        "Crash during launch or gameplay",
                        "Low FPS / stutter",
                        "Out of memory"
                    ).forEach { option ->
                        Card(
                            onClick = { troubleType = option; troubleStep = 0 },
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = if (troubleType == option) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface),
                            border = if (troubleType == option) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = troubleType == option, onClick = { troubleType = option; troubleStep = 0 })
                                Text(option, Modifier.weight(1f))
                            }
                        }
                    }
                    Text("Suggested checks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    val checks = troubleshootingSteps(troubleType)
                    checks.forEachIndexed { i, check ->
                        Row(verticalAlignment = Alignment.Top) {
                            Checkbox(checked = i < troubleStep, onCheckedChange = { checked -> troubleStep = if (checked) maxOf(troubleStep, i + 1) else minOf(troubleStep, i) })
                            Text(check, Modifier.weight(1f).padding(top = 12.dp))
                        }
                    }
                    OutlinedButton(onClick = {
                        val desc = buildString {
                            appendLine("Symptom: $troubleType")
                            appendLine("Game: ${troubleGame.ifBlank { "not specified" }}")
                            appendLine("Troubleshooting checklist:")
                            checks.forEachIndexed { i, s -> appendLine("${if (i < troubleStep) "[done]" else "[ ]"} $s") }
                            appendLine()
                            appendLine("Device / emulator: please verify and include the exact versions.")
                            appendLine("Driver tested: specify System Driver or PanVK and attach evidence.")
                            appendLine("Attach a sanitized log or video before submitting.")
                        }
                        onCreateIssue("Game troubleshooting: $troubleType", troubleGame, when {
                            troubleType.contains("Vulkan") -> "Vulkan Error"
                            troubleType.contains("texture") || troubleType.contains("graphics") || troubleType.contains("screen") -> "Graphics"
                            troubleType.contains("FPS") -> "Performance"
                            troubleType.contains("memory") -> "Memory"
                            else -> "Crash"
                        }, desc)
                    }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Assignment, null); Spacer(Modifier.width(8.dp)); Text("CREATE PREFILLED ISSUE")
                    }
                    OutlinedButton(onClick = { createDiagnosticPackage() }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Inventory2, null); Spacer(Modifier.width(8.dp)); Text("BUILD DIAGNOSTIC PACKAGE")
                    }
                }
            }

            "Logs" -> {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Diagnostic Log Analyzer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Select a text log. Analysis runs locally, highlights common signatures and suggests what to attach to a report. This is triage, not a definitive diagnosis.")
                    Button(onClick = { pickLog.launch(arrayOf("text/*", "application/octet-stream", "application/log")) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text("SELECT LOG FILE")
                    }
                    if (logFilename != null) Text("Selected: $logFilename", color = MaterialTheme.colorScheme.primary)
                    if (logCategoryCounts.isNotEmpty()) {
                        Text("Signatures found", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        logCategoryCounts.forEach { (category, count) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(category, Modifier.weight(1f))
                                AssistChip(onClick = {}, label = { Text(count.toString()) })
                            }
                        }
                    }
                    if (logAnalysis != null) {
                        OutlinedButton(onClick = { shareText(context, "GMailGPU Log Analysis", logAnalysis!!) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Share, null); Spacer(Modifier.width(7.dp)); Text("SHARE ANALYSIS")
                        }
                        OutlinedButton(onClick = { copyText(context, logAnalysis!!) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.ContentCopy, null); Spacer(Modifier.width(7.dp)); Text("COPY ANALYSIS")
                        }
                        Card(Modifier.fillMaxWidth()) { Text(logAnalysis!!, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall) }
                    } else EmptyState("No log analyzed yet. Your selected file is not uploaded automatically.")
                    Text("Privacy: the analyzer redacts common emails and credential-like values, but review the output before sharing.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            "Profile & Stats" -> {
                LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 14.dp, bottom = 30.dp)) {
                    item { Text("Tester profile", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                    item { OutlinedTextField(profileName, { profileName = it }, label = { Text("Display name") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                    item { OutlinedTextField(profileBio, { profileBio = it.take(500) }, label = { Text("Short bio (max 500 chars)") }, modifier = Modifier.fillMaxWidth(), minLines = 3) }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Make my profile public", fontWeight = FontWeight.SemiBold)
                                Text("Off = only you can see the profile. Test counts below are based on accessible database results.", style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(checked = profilePublic, onCheckedChange = { profilePublic = it })
                        }
                    }
                    item { Button(onClick = { saveTesterProfile() }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Person, null); Spacer(Modifier.width(8.dp)); Text("SAVE TESTER PROFILE") } }
                    item { HorizontalDivider(); Text("Community statistics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            MetricCard("TESTS", reports.size.toString(), Modifier.weight(1f))
                            MetricCard("VERIFIED", reports.count { it.reviewStatus == "verified" }.toString(), Modifier.weight(1f))
                            MetricCard("REVIEWED", reports.count { it.evidenceReviewed }.toString(), Modifier.weight(1f))
                        }
                    }
                    item {
                        val mine = reports.filter { it.userId == userId }
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(profileName.ifBlank { username }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(profileBio.ifBlank { "No bio added." })
                                Text("Your accessible submissions: ${mine.size}")
                                Text("Verified submissions: ${mine.count { it.reviewStatus == "verified" }}")
                                val score = mine.count { it.evidencePaths.isNotEmpty() } + mine.count { it.reviewStatus == "verified" } * 2
                                val reward = when {
                                    mine.count { it.reviewStatus == "verified" } >= 10 -> "Gold Verified Tester"
                                    mine.count { it.reviewStatus == "verified" } >= 3 -> "Verified Tester"
                                    score >= 5 -> "Helpful Contributor"
                                    mine.isNotEmpty() -> "Community Tester"
                                    else -> "New Tester"
                                }
                                AssistChip(onClick = {}, label = { Icon(Icons.Default.EmojiEvents, null); Spacer(Modifier.width(5.dp)); Text(reward) })
                                Text("Badges reflect recorded contributions and reviewed results, not FPS ranking.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    item { HorizontalDivider(); Text("Community tester highlights", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
                    if (publicTesters.isEmpty()) item { Text("No public tester profiles yet. Users opt in from Profile & Stats.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(publicTesters, key = { it.userId }) { tester ->
                        val testerRuns = reports.filter { it.userId == tester.userId }
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(tester.displayName, fontWeight = FontWeight.Bold)
                                if (tester.bio.isNotBlank()) Text(tester.bio)
                                Text("${testerRuns.size} visible test(s) • ${testerRuns.count { it.reviewStatus == "verified" }} verified", style = MaterialTheme.typography.bodySmall)
                                val badge = when {
                                    testerRuns.count { it.reviewStatus == "verified" } >= 10 -> "Gold Verified Tester"
                                    testerRuns.count { it.reviewStatus == "verified" } >= 3 -> "Verified Tester"
                                    testerRuns.isNotEmpty() -> "Community Tester"
                                    else -> "New Tester"
                                }
                                AssistChip(onClick = {}, label = { Text(badge) })
                            }
                        }
                    }
                    item { Text("Test coverage by GPU", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
                    items(gameLabGpuChoices.filter { it != "Any GPU" }) { gpu ->
                        val count = reports.count { it.gpuFamily == gpu }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(gpu, Modifier.width(100.dp))
                            LinearProgressIndicator(
                                progress = { if (reports.isEmpty()) 0f else count.toFloat()/reports.size.toFloat() },
                                modifier = Modifier.weight(1f).height(8.dp)
                            )
                            Text("  $count")
                        }
                    }
                    item { Button(onClick = { exportPdf() }, modifier = Modifier.fillMaxWidth(), enabled = reports.isNotEmpty()) { Icon(Icons.Default.PictureAsPdf, null); Spacer(Modifier.width(7.dp)); Text("EXPORT STATS & TESTS TO PDF") } }
                    item { OutlinedButton(onClick = { shareText(context, "GMailGPU Community Stats", "Game reports: ${reports.size}\nGames: ${reports.map { it.gameName.lowercase() }.distinct().size}\nVerified: ${reports.count { it.reviewStatus == "verified" }}\nLatest cache can be used offline.") }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(7.dp)); Text("SHARE SUMMARY") } }
                }
            }

            "Admin Review" -> {
                if (!isAdmin) {
                    EmptyState("Developer tools are visible to accounts whose Supabase profile role is admin.")
                } else {
                    val queue = reports.filter { it.reviewStatus == "community" }
                    LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 14.dp, bottom = 30.dp)) {
                        item { Text("Review queue • ${queue.size} community report(s)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                        item { Text("Review the image/video evidence and test details before marking a result Verified. Verification is not a promise of compatibility on every device.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        if (queue.isEmpty()) item { EmptyState("No community reports are waiting for review.") }
                        items(queue, key = { it.id }) { report ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(report.gameName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text("${report.gpuFamily} • ${report.driverType} ${report.driverVersion.orEmpty()} • ${report.emulatorName}")
                                    Text("FPS: avg ${report.averageFps ?: "—"} • min ${report.minimumFps ?: "—"} • duration ${report.testDurationSeconds?.let { "$it s" } ?: "—"}")
                                    Text("Evidence: ${report.evidencePaths.size} file(s) • ${if (report.evidenceReviewed) "Evidence reviewed" else "Not reviewed"}")
                                    Text(report.notes)
                                    report.evidencePaths.forEachIndexed { i, path ->
                                        TextButton(onClick = { openPrivateEvidence(context, scope, client, path) { error = it } }) { Icon(Icons.Default.OpenInNew, null); Spacer(Modifier.width(4.dp)); Text("Open evidence ${i+1}") }
                                    }
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                        OutlinedButton(onClick = { reviewReport(report, "reviewed") }, modifier = Modifier.weight(1f)) { Text("EVIDENCE REVIEWED") }
                                        Button(onClick = { reviewReport(report, "verified") }, modifier = Modifier.weight(1f)) { Text("VERIFY") }
                                    }
                                    OutlinedButton(onClick = { reviewReport(report, "rejected") }, modifier = Modifier.fillMaxWidth()) { Text("REJECT REPORT") }
                                }
                            }
                        }
                        item { HorizontalDivider(); Text("Moderation flags", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                        val openFlags = reportFlags.filter { it.status == "open" || it.status == "reviewing" }
                        item { Text("${openFlags.size} open flag(s)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        if (openFlags.isEmpty()) item { EmptyState("No open moderation flags.") }
                        items(openFlags, key = { "flag-${it.id}" }) { flag ->
                            val flaggedReport = reports.firstOrNull { it.id == flag.reportId }
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text(flaggedReport?.gameName ?: "Removed / unavailable report", fontWeight = FontWeight.Bold)
                                    Text("Reason: ${flag.reason} • ${flag.createdAt.orEmpty().take(10)}")
                                    if (flag.details.isNotBlank()) Text(flag.details)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(onClick = { moderateFlag(flag, "reviewing") }, modifier = Modifier.weight(1f)) { Text("REVIEWING") }
                                        Button(onClick = { moderateFlag(flag, "resolved") }, modifier = Modifier.weight(1f)) { Text("RESOLVE") }
                                    }
                                    TextButton(onClick = { moderateFlag(flag, "dismissed") }, modifier = Modifier.align(Alignment.End)) { Text("DISMISS FLAG") }
                                }
                            }
                        }
                    }
                }
            }

            "Driver Updates" -> {
                LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 14.dp, bottom = 30.dp)) {
                    item {
                        Text("Driver Update Tracker", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Checks GitHub's latest published Release API. Repositories without a GitHub Release are marked accordingly; development commits and custom builds may exist outside Releases.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    item { Button(onClick = { fetchDriverReleases() }, enabled = !loadingReleases, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Sync, null); Spacer(Modifier.width(8.dp)); Text(if (loadingReleases) "CHECKING GITHUB…" else "CHECK RELEASES") } }
                    if (loadingReleases) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    if (latestReleases.isEmpty()) item { EmptyState("Tap Check Releases to query the driver repositories.") }
                    items(latestReleases, key = { it.repository }) { release ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(release.repository, fontWeight = FontWeight.Bold)
                                Text(release.latestVersion, color = MaterialTheme.colorScheme.primary)
                                if (release.publishedAt.isNotBlank()) Text("Published: ${release.publishedAt.take(10)}", style = MaterialTheme.typography.bodySmall)
                                Text(release.status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.url))) }) { Icon(Icons.Default.OpenInNew, null); Spacer(Modifier.width(5.dp)); Text("OPEN SOURCE / RELEASES") }
                            }
                        }
                    }
                }
            }

            else -> EmptyState("Select a Game Lab tool.")
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Info, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text(message, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ChoiceChips(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        options.chunked(3).forEach { group ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                group.forEach { option ->
                    FilterChip(
                        selected = selected == option,
                        onClick = { onSelect(option) },
                        label = { Text(option, maxLines = 1) }
                    )
                }
            }
        }
    }
}

private fun troubleshootingSteps(type: String): List<String> = when (type) {
    "Black textures / missing graphics" -> listOf(
        "Repeat the same scene with System Driver and PanVK, if both are supported.",
        "Capture a screenshot of the exact missing texture and a 20–30 second video.",
        "Record driver build, emulator, Wine, DXVK and VKD3D-Proton versions.",
        "Try one graphics setting change at a time; record which change affects the issue."
    )
    "Black screen" -> listOf(
        "Wait once for shader compilation and capture the log around first rendering.",
        "Try a known lower resolution and windowed mode if available.",
        "Compare with the System Driver without changing the emulator stack.",
        "Attach a video and the first Vulkan/DXGI errors."
    )
    "Vulkan initialization error" -> listOf(
        "Copy the exact VK_ERROR_* code from the log.",
        "Record whether vkCreateInstance, physical-device enumeration, or vkCreateDevice fails.",
        "Record the GPU family, driver build and emulator version.",
        "Attach the first error and the surrounding log lines."
    )
    "Low FPS / stutter" -> listOf(
        "Use the same save point, scene, resolution and graphics preset for both runs.",
        "Measure average FPS after warm-up and record minimum FPS and test duration.",
        "Record device temperature if it is available; watch for thermal throttling.",
        "Change one layer at a time: driver, DXVK/VKD3D, or graphics preset."
    )
    "Out of memory" -> listOf(
        "Record the exact allocation error and available memory if known.",
        "Lower resolution and texture quality for one comparison run.",
        "Close background apps and repeat with the same game scene.",
        "Do not label it a GPU driver bug unless the log supports that conclusion."
    )
    else -> listOf(
        "Record when the crash occurs: launch, loading screen, or gameplay.",
        "Capture the last 100 relevant log lines before the crash.",
        "Retry with the same versions and a reproducible test scene.",
        "Attach a short video plus the exact emulator/driver stack."
    )
}

private fun shareText(context: android.content.Context, title: String, body: String) {
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, body)
    }, title))
}

private fun copyText(context: android.content.Context, body: String) {
    val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
    clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("GMailGPU log analysis", body))
    android.widget.Toast.makeText(context, "Analysis copied.", android.widget.Toast.LENGTH_SHORT).show()
}

private fun shareConfig(config: GameSavedConfiguration, context: android.content.Context) {
    val text = buildString {
        appendLine("GMailGPU Saved Configuration")
        appendLine("Game: ${config.gameName}")
        appendLine("GPU: ${config.gpuFamily}")
        appendLine("Driver: ${config.driverType} ${config.driverVersion.orEmpty()}")
        appendLine("Device: ${config.deviceModel ?: "not provided"} • SoC: ${config.soc ?: "not provided"}")
        appendLine("Emulator: ${config.emulatorName} ${config.emulatorVersion.orEmpty()}")
        appendLine("Wine: ${config.wineVersion ?: "—"} • DXVK: ${config.dxvkVersion ?: "—"} • VKD3D: ${config.vkd3dVersion ?: "—"} • Box64: ${config.box64Version ?: "—"}")
        appendLine("Resolution: ${config.resolution ?: "—"} • Preset: ${config.graphicsPreset ?: "—"}")
        appendLine("Notes: ${config.notes.orEmpty()}")
        appendLine("Configuration may need adaptation to another device.")
    }
    shareText(context, "GMailGPU: ${config.gameName}", text)
}

private fun openPrivateEvidence(
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    client: io.github.jan.supabase.SupabaseClient,
    path: String,
    onError: (String) -> Unit
) {
    scope.launch {
        runCatching {
            withContext(Dispatchers.IO) {
                client.storage.from("game-evidence").createSignedUrl(path = path, expiresIn = 20.minutes)
            }
        }.onSuccess { signedUrl ->
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(signedUrl)))
            }.onFailure { onError("Couldn't open evidence: ${it.message}") }
        }.onFailure { onError("Couldn't create signed evidence link: ${it.message}") }
    }
}
