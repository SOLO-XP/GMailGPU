package com.g57.issuehub

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.viewinterop.AndroidView
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.g57.issuehub.data.SupabaseProvider
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
import java.util.UUID
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.tasks.await
import java.util.Locale

@Serializable
private data class GameCompatibilityReport(
    val id: String,
    @SerialName("user_id") val userId: String,
    val username: String,
    @SerialName("game_name") val gameName: String,
    @SerialName("game_version") val gameVersion: String? = null,
    @SerialName("game_image_path") val gameImagePath: String? = null,
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
    @SerialName("evidence_reviewed") val evidenceReviewed: Boolean = false,
    val outcome: String,
    @SerialName("graphics_issues") val graphicsIssues: String? = null,
    val notes: String,
    @SerialName("evidence_paths") val evidencePaths: List<String> = emptyList(),
    @SerialName("review_status") val reviewStatus: String = "community",
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
private data class GameDiscussion(
    val id: String,
    @SerialName("report_id") val reportId: String,
    @SerialName("user_id") val userId: String,
    val username: String = "community member",
    val body: String,
    @SerialName("created_at") val createdAt: String? = null
)

private val gameCompatibilityJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
private val gameGpuOptions = listOf("Mali-G52", "Mali-G57", "Mali-G615", "Mali-G720", "Other")
private val gameDriverOptions = listOf("System Driver", "PanVK", "Other")
private val gameOutcomeOptions = listOf("Playable", "Playable with issues", "Crashes / does not launch", "Not tested")

@Composable
internal fun GameCompatibilityScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val client = remember { SupabaseProvider.client }
    val userId = remember { client.auth.currentUserOrNull()?.id.orEmpty() }
    var username by remember { mutableStateOf("community member") }
    var reports by remember { mutableStateOf(emptyList<GameCompatibilityReport>()) }
    var loading by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }
    var gpuFilter by remember { mutableStateOf("All GPUs") }
    var driverFilter by remember { mutableStateOf("Any driver") }
    var outcomeFilter by remember { mutableStateOf("Any outcome") }
    var deviceFilter by remember { mutableStateOf("") }
    var sortBy by remember { mutableStateOf("Newest") }
    var isAdmin by remember { mutableStateOf(false) }
    var previewUrl by remember { mutableStateOf<String?>(null) }
    var previewVideo by remember { mutableStateOf(false) }
    var coverUrls by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var selectedGame by remember { mutableStateOf<String?>(null) }
    var selectedReport by remember { mutableStateOf<GameCompatibilityReport?>(null) }
    var duplicateReport by remember { mutableStateOf<GameCompatibilityReport?>(null) }
    var discussionReport by remember { mutableStateOf<GameCompatibilityReport?>(null) }
    var discussions by remember { mutableStateOf(emptyList<GameDiscussion>()) }
    var discussionDraft by remember { mutableStateOf("") }
    var discussionLoading by remember { mutableStateOf(false) }
    var reportToFlag by remember { mutableStateOf<GameCompatibilityReport?>(null) }
    var deleteReportTarget by remember { mutableStateOf<GameCompatibilityReport?>(null) }
    var translateReportId by remember { mutableStateOf<String?>(null) }
    var game by remember { mutableStateOf("") }
    var gameVersion by remember { mutableStateOf("") }
    var gpu by remember { mutableStateOf("Mali-G57") }
    var device by remember { mutableStateOf("") }
    var soc by remember { mutableStateOf("") }
    var android by remember { mutableStateOf("") }
    var driver by remember { mutableStateOf("System Driver") }
    var driverVersion by remember { mutableStateOf("") }
    var emulator by remember { mutableStateOf("Winlator") }
    var emulatorVersion by remember { mutableStateOf("") }
    var wine by remember { mutableStateOf("") }
    var dxvk by remember { mutableStateOf("") }
    var vkd3d by remember { mutableStateOf("") }
    var box64 by remember { mutableStateOf("") }
    var resolution by remember { mutableStateOf("") }
    var preset by remember { mutableStateOf("") }
    var avgFps by remember { mutableStateOf("") }
    var minFps by remember { mutableStateOf("") }
    var testDuration by remember { mutableStateOf("") }
    var deviceTemperature by remember { mutableStateOf("") }
    var outcome by remember { mutableStateOf("Playable with issues") }
    var graphicsIssues by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var evidence by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var gameCover by remember { mutableStateOf<Uri?>(null) }
    var showForm by remember { mutableStateOf(false) }

    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        gameCover = uri
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        evidence = (evidence + uris).distinct().take(6)
        error = if (uris.size + evidence.size > 6) "A maximum of 6 evidence files is allowed." else null
    }

    fun refresh() {
        scope.launch {
            loading = true
            error = null
            runCatching {
                client.from("game_compatibility_reports").select(Columns.ALL) {
                    order(column = "created_at", order = Order.DESCENDING)
                }.decodeList<GameCompatibilityReport>()
            }.onSuccess {
                reports = it
                context.getSharedPreferences("gmailgpu_settings", 0).edit()
                    .putString("gmailgpu_game_compatibility_cache", gameCompatibilityJson.encodeToString(it))
                    .apply()
            }.onFailure { failure ->
                val cached = context.getSharedPreferences("gmailgpu_settings", 0)
                    .getString("gmailgpu_game_compatibility_cache", null)
                val restored = cached?.let { runCatching { gameCompatibilityJson.decodeFromString<List<GameCompatibilityReport>>(it) }.getOrNull() }
                if (restored != null) {
                    reports = restored
                    notice = "Offline mode: showing cached community test results."
                }
                error = "Couldn't refresh the cloud library. ${if (restored != null) "Showing cached data; " else ""}check connection and Supabase migrations. Details: ${failure.message}"
            }
            loading = false
        }
    }

    LaunchedEffect(userId) {
        runCatching {
            val profile = client.from("profiles").select(Columns.ALL) {
                filter { eq("id", userId) }
            }.decodeSingle<com.g57.issuehub.data.Profile>()
            username = profile.username
            isAdmin = profile.role.equals("admin", true)
        }
        refresh()
    }

    LaunchedEffect(reports) {
        val coverPaths = reports
            .filter { !it.gameImagePath.isNullOrBlank() }
            .groupBy { it.gameName.trim().lowercase(Locale.ROOT) }
            .mapNotNull { (key, gameReports) -> gameReports.firstNotNullOfOrNull { it.gameImagePath }?.let { key to it } }
        val links = mutableMapOf<String, String>()
        coverPaths.forEach { (key, path) ->
            runCatching { client.storage.from("game-evidence").createSignedUrl(path = path, expiresIn = 20.minutes) }
                .onSuccess { links[key] = it }
        }
        coverUrls = links
    }

    fun openEvidence(path: String) {
        scope.launch {
            runCatching {
                client.storage.from("game-evidence").createSignedUrl(path = path, expiresIn = 20.minutes)
            }.onSuccess { url ->
                previewUrl = url
                previewVideo = path.substringBefore('?').lowercase().let {
                    it.endsWith(".mp4") || it.endsWith(".webm") || it.endsWith(".mov") || it.endsWith(".m4v")
                }
            }.onFailure { error = "Couldn't create an evidence link: ${it.message}" }
        }
    }

    fun openDiscussion(report: GameCompatibilityReport) {
        discussionReport = report
        discussions = emptyList()
        discussionDraft = ""
        discussionLoading = true
        scope.launch {
            runCatching {
                client.from("game_report_discussions").select(Columns.ALL) {
                    filter { eq("report_id", report.id) }
                    order(column = "created_at", order = Order.ASCENDING)
                }.decodeList<GameDiscussion>()
            }.onSuccess { discussions = it }
             .onFailure { error = "Discussion is unavailable. Run supabase_game_lab_features.sql. Details: ${it.message}" }
            discussionLoading = false
        }
    }

    fun postDiscussion() {
        val target = discussionReport ?: return
        val body = discussionDraft.trim()
        if (body.isBlank()) return
        if (body.length > 2000) { error = "Discussion comments must be 2,000 characters or fewer."; return }
        scope.launch {
            runCatching {
                client.from("game_report_discussions").insert(buildJsonObject {
                    put("report_id", target.id)
                    put("user_id", userId)
                    put("username", username)
                    put("body", body)
                })
            }.onSuccess {
                discussionDraft = ""
                openDiscussion(target)
            }.onFailure { error = "Couldn't post comment. Run the Game Lab SQL migration. Details: ${it.message}" }
        }
    }

    fun saveConfiguration(report: GameCompatibilityReport) {
        if (userId.isBlank()) { error = "Sign in to save a configuration."; return }
        scope.launch {
            runCatching {
                client.from("game_saved_configurations").insert(buildJsonObject {
                    put("user_id", userId)
                    put("report_id", report.id)
                    put("config_name", "${report.gameName} — ${report.driverType}")
                    put("game_name", report.gameName)
                    put("gpu_family", report.gpuFamily)
                    put("driver_type", report.driverType)
                    put("driver_version", report.driverVersion)
                    put("device_model", report.deviceModel)
                    put("soc", report.soc)
                    put("emulator_name", report.emulatorName)
                    put("emulator_version", report.emulatorVersion)
                    put("wine_version", report.wineVersion)
                    put("dxvk_version", report.dxvkVersion)
                    put("vkd3d_version", report.vkd3dVersion)
                    put("box64_version", report.box64Version)
                    put("resolution", report.resolution)
                    put("graphics_preset", report.graphicsPreset)
                    put("notes", report.notes)
                    put("is_public", false)
                })
            }.onSuccess { notice = "Configuration saved. Open Community Game Lab → Configs to share or manage it." }
             .onFailure { error = "Couldn't save configuration. Run supabase_game_lab_features.sql. Details: ${it.message}" }
        }
    }

    fun flagReport(report: GameCompatibilityReport) {
        scope.launch {
            runCatching {
                client.from("game_report_flags").insert(buildJsonObject {
                    put("report_id", report.id)
                    put("reporter_id", userId)
                    put("reason", "Misleading result")
                    put("details", "User requested moderator review from the Game Compatibility Library.")
                })
            }.onSuccess { notice = "Report sent to moderators for review." }
             .onFailure { error = "Couldn't flag report. Run the Game Lab SQL migration. Details: ${it.message}" }
        }
    }

    fun deleteOwnReport(report: GameCompatibilityReport) {
        if (report.userId != userId) { error = "You can delete only your own test reports."; return }
        scope.launch {
            runCatching {
                val filesToDelete = report.evidencePaths + listOfNotNull(report.gameImagePath)
                if (filesToDelete.isNotEmpty()) {
                    client.storage.from("game-evidence").delete(*filesToDelete.toTypedArray())
                }
                client.from("game_compatibility_reports").delete {
                    filter { eq("id", report.id); eq("user_id", userId) }
                }
            }.onSuccess {
                reports = reports.filterNot { it.id == report.id }
                deleteReportTarget = null
                notice = "Your report was deleted. Related discussion and watch alerts are removed by the database."
                refresh()
            }.onFailure { error = "Could not delete your report. Run supabase_game_lab_features.sql and check your evidence ownership. Details: ${it.message}" }
        }
    }

    fun submit(forceDuplicate: Boolean = false) {
        if (userId.isBlank()) { error = "Sign in before submitting a community result."; return }
        if (game.isBlank() || notes.isBlank()) { error = "Game name and test notes are required."; return }
        if (evidence.isEmpty()) { error = "Attach at least one screenshot or video as evidence."; return }
        val parsedAvg = avgFps.toDoubleOrNull()
        val parsedMin = minFps.toDoubleOrNull()
        val parsedDuration = testDuration.toIntOrNull()
        val parsedTemperature = deviceTemperature.toDoubleOrNull()
        if ((avgFps.isNotBlank() && (parsedAvg == null || parsedAvg < 0)) ||
            (minFps.isNotBlank() && (parsedMin == null || parsedMin < 0))) {
            error = "FPS must be a non-negative number."; return
        }
        if ((testDuration.isNotBlank() && (parsedDuration == null || parsedDuration < 0)) ||
            (deviceTemperature.isNotBlank() && (parsedTemperature == null || parsedTemperature !in 0.0..150.0))) {
            error = "Test duration must be a non-negative whole number and temperature must be between 0 and 150 °C."; return
        }
        val possibleDuplicate = reports.firstOrNull {
            it.gameName.equals(game.trim(), ignoreCase = true) &&
                it.gpuFamily == gpu &&
                it.driverType == driver &&
                it.deviceModel.orEmpty().equals(device.trim(), ignoreCase = true)
        }
        if (possibleDuplicate != null && !forceDuplicate) {
            duplicateReport = possibleDuplicate
            return
        }
        duplicateReport = null
        submitting = true
        error = null
        notice = null
        scope.launch {
            val id = UUID.randomUUID().toString()
            val uploaded = mutableListOf<String>()
            val uploadedPathsToCleanup = mutableListOf<String>()
            var uploadedCoverPath: String? = null
            runCatching {
                gameCover?.let { coverUri ->
                    val coverMime = context.contentResolver.getType(coverUri).orEmpty()
                    require(coverMime in listOf("image/jpeg", "image/png", "image/webp", "image/gif")) { "Game cover must be JPG, PNG, WEBP, or GIF." }
                    val coverBytes = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(coverUri)?.use { it.readBytes() }
                            ?: throw IllegalStateException("Could not read the selected game cover.")
                    }
                    require(coverBytes.size <= 20L * 1024L * 1024L) { "Game cover must be 20 MB or smaller." }
                    val coverExtension = when (coverMime) { "image/png" -> "png"; "image/webp" -> "webp"; "image/gif" -> "gif"; else -> "jpg" }
                    val coverPath = "$userId/$id/game_cover.$coverExtension"
                    client.storage.from("game-evidence").upload(coverPath, coverBytes) { upsert = false }
                    uploadedCoverPath = coverPath
                    uploadedPathsToCleanup += coverPath
                }
                evidence.forEachIndexed { index, uri ->
                    val mime = context.contentResolver.getType(uri).orEmpty()
                    require(mime.startsWith("image/") || mime.startsWith("video/")) { "Only image and video evidence is supported." }
                    val bytes = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            ?: throw IllegalStateException("Couldn't read a selected file.")
                    }
                    require(bytes.size <= 150L * 1024L * 1024L) { "Each evidence file must be 150 MB or smaller." }
                    val displayName = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    } ?: "evidence_$index"
                    val safeName = displayName.replace(Regex("[^A-Za-z0-9._-]"), "_").take(100)
                    val path = "$userId/$id/${System.currentTimeMillis()}_$safeName"
                    client.storage.from("game-evidence").upload(path, bytes) { upsert = false }
                    uploaded += path
                    uploadedPathsToCleanup += path
                }
                client.from("game_compatibility_reports").insert(buildJsonObject {
                    put("id", id)
                    put("user_id", userId)
                    put("username", username)
                    put("game_name", game.trim())
                    put("game_version", gameVersion.ifBlank { null })
                    put("game_image_path", uploadedCoverPath)
                    put("gpu_family", gpu)
                    put("device_model", device.ifBlank { null })
                    put("soc", soc.ifBlank { null })
                    put("android_version", android.ifBlank { null })
                    put("driver_type", driver)
                    put("driver_version", driverVersion.ifBlank { null })
                    put("emulator_name", emulator)
                    put("emulator_version", emulatorVersion.ifBlank { null })
                    put("wine_version", wine.ifBlank { null })
                    put("dxvk_version", dxvk.ifBlank { null })
                    put("vkd3d_version", vkd3d.ifBlank { null })
                    put("box64_version", box64.ifBlank { null })
                    put("resolution", resolution.ifBlank { null })
                    put("graphics_preset", preset.ifBlank { null })
                    if (parsedAvg != null) put("average_fps", parsedAvg)
                    if (parsedMin != null) put("minimum_fps", parsedMin)
                    if (parsedDuration != null) put("test_duration_seconds", parsedDuration)
                    if (parsedTemperature != null) put("device_temperature_c", parsedTemperature)
                    put("outcome", outcome)
                    put("graphics_issues", graphicsIssues.ifBlank { null })
                    put("notes", notes.trim())
                    put("evidence_paths", kotlinx.serialization.json.buildJsonArray { uploaded.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
                })
            }.onSuccess {
                notice = "Result submitted. It is labeled as a community report until verified."
                game = ""; gameVersion = ""; gameCover = null; driverVersion = ""; emulatorVersion = ""; wine = ""; dxvk = ""; vkd3d = ""; box64 = ""
                resolution = ""; preset = ""; avgFps = ""; minFps = ""; testDuration = ""; deviceTemperature = ""; graphicsIssues = ""; notes = ""; evidence = emptyList()
                showForm = false
                refresh()
            }.onFailure {
                error = "Submission failed: ${it.message ?: "unknown error"}. Check the Supabase migration and Storage policies."
                runCatching { client.storage.from("game-evidence").delete(*uploadedPathsToCleanup.toTypedArray()) }
            }
            submitting = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
            Column(Modifier.weight(1f)) {
                AppText("Game Compatibility Library", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                AppText("Community evidence • System Driver vs PanVK", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { refresh() }, enabled = !loading) { Icon(Icons.Default.Refresh, contentDescription = "Refresh") }
        }
        AppText(
            "Browse real community tests or share your own Winlator/GameHub result. Enter your device, GPU, driver and settings, then attach at least one screenshot or gameplay clip so others can learn from your experience.",
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { showForm = !showForm }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); AppText(if (showForm) "HIDE FORM" else "ADD GAME RESULT")
            }
        }
        if (notice != null) AppText(notice!!, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.primary)
        if (error != null) AppText(error!!, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error)
        if (showForm) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AppText("1 • Game and device", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                OutlinedTextField(game, { game = it }, label = { AppText("Game name *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                AppText("Game cover art (optional)", fontWeight = FontWeight.SemiBold)
                AppText("Optional: choose a square cover image to make the game easier to recognize. You can submit your test without one.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (gameCover != null) {
                        AsyncImage(model = gameCover, contentDescription = "Selected game cover", contentScale = ContentScale.Crop, modifier = Modifier.size(104.dp).clip(RoundedCornerShape(14.dp)))
                    } else {
                        Box(Modifier.size(104.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.SportsEsports, contentDescription = null, modifier = Modifier.size(38.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = { coverPicker.launch("image/*") }) {
                            Icon(Icons.Default.Image, null); Spacer(Modifier.width(6.dp)); AppText(if (gameCover == null) "CHOOSE COVER" else "CHANGE COVER")
                        }
                        if (gameCover != null) TextButton(onClick = { gameCover = null }) { AppText("Remove image") }
                    }
                }
                OutlinedTextField(gameVersion, { gameVersion = it }, label = { AppText("Game version") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                AppText("GPU family")
                FlowRowCompat(options = gameGpuOptions, selected = gpu, onSelect = { gpu = it })
                OutlinedTextField(device, { device = it }, label = { AppText("Phone / device model") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(soc, { soc = it }, label = { AppText("SoC (e.g. Dimensity 6100+)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(android, { android = it }, label = { AppText("Android version") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

                HorizontalDivider()
                AppText("2 • Driver and compatibility layers", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                FlowRowCompat(options = gameDriverOptions, selected = driver, onSelect = { driver = it })
                OutlinedTextField(driverVersion, { driverVersion = it }, label = { AppText("Driver build / version") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(emulator, { emulator = it }, label = { AppText("Winlator / GameHub / other") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(emulatorVersion, { emulatorVersion = it }, label = { AppText("Emulator version") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(wine, { wine = it }, label = { AppText("Wine version") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(dxvk, { dxvk = it }, label = { AppText("DXVK version") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(vkd3d, { vkd3d = it }, label = { AppText("VKD3D-Proton version") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(box64, { box64 = it }, label = { AppText("Box64 version") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

                HorizontalDivider()
                AppText("3 • Test results", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                OutlinedTextField(resolution, { resolution = it }, label = { AppText("Resolution (e.g. 800×600)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(preset, { preset = it }, label = { AppText("Graphics settings / preset") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(avgFps, { avgFps = it }, label = { AppText("Average FPS (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(minFps, { minFps = it }, label = { AppText("Minimum FPS (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(testDuration, { testDuration = it.filter(Char::isDigit).take(6) }, label = { AppText("Test duration in seconds (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(deviceTemperature, { deviceTemperature = it.filter { ch -> ch.isDigit() || ch == '.' }.take(6) }, label = { AppText("Device temperature °C (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                FlowRowCompat(options = gameOutcomeOptions, selected = outcome, onSelect = { outcome = it })
                OutlinedTextField(graphicsIssues, { graphicsIssues = it }, label = { AppText("Graphics issues (black textures, glitches, etc.)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                OutlinedTextField(notes, { notes = it }, label = { AppText("Test notes / steps to reproduce *") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        AppText("4 • Evidence *", fontWeight = FontWeight.Bold)
                        AppText("Attach screenshots or gameplay video showing the result. Do not include private messages, account details, or tokens.", style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = { picker.launch(arrayOf("image/*", "video/*")) }) {
                            Icon(Icons.Default.AttachFile, null); Spacer(Modifier.width(6.dp)); AppText("SELECT PHOTOS / VIDEOS")
                        }
                        evidence.forEachIndexed { index, uri ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (context.contentResolver.getType(uri).orEmpty().startsWith("video/")) Icons.Default.Videocam else Icons.Default.Image, null)
                                AppText(uri.lastPathSegment ?: "Evidence ${index + 1}", Modifier.weight(1f), maxLines = 1)
                                IconButton(onClick = { evidence = evidence.toMutableList().apply { removeAt(index) } }) { Icon(Icons.Default.Close, "Remove") }
                            }
                        }
                    }
                }
                Button(onClick = { submit() }, enabled = !submitting, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    if (submitting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp)); AppText(if (submitting) "UPLOADING EVIDENCE…" else "SUBMIT COMMUNITY RESULT")
                }
            }
        } else {
            OutlinedTextField(search, { search = it }, label = { AppText("Search game, GPU, driver, or emulator") }, leadingIcon = { Icon(Icons.Default.Search, null) }, modifier = Modifier.fillMaxWidth().padding(16.dp), singleLine = true)
            val filtered = reports.filter { report ->
                (gpuFilter == "All GPUs" || report.gpuFamily == gpuFilter) &&
                    (driverFilter == "Any driver" || report.driverType == driverFilter) &&
                    (outcomeFilter == "Any outcome" || report.outcome == outcomeFilter) &&
                    (deviceFilter.isBlank() || report.deviceModel.orEmpty().contains(deviceFilter, ignoreCase = true)) &&
                    listOf(report.gameName, report.gpuFamily, report.driverType, report.driverVersion.orEmpty(), report.emulatorName, report.emulatorVersion.orEmpty(), report.outcome, report.deviceModel.orEmpty(), report.dxvkVersion.orEmpty(), report.vkd3dVersion.orEmpty())
                        .any { value -> value.contains(search, ignoreCase = true) }
            }.let { rows ->
                when (sortBy) {
                    "Highest FPS" -> rows.sortedByDescending { it.averageFps ?: -1.0 }
                    "Verified first" -> rows.sortedWith(compareByDescending<GameCompatibilityReport> { it.reviewStatus == "verified" }.thenByDescending { it.createdAt.orEmpty() })
                    else -> rows.sortedByDescending { it.createdAt.orEmpty() }
                }
            }
            val gameGroups = filtered
                .groupBy { it.gameName.trim().lowercase(Locale.ROOT) }
                .values
                .map { group -> group.sortedByDescending { it.createdAt.orEmpty() } }
                .sortedWith(
                    when (sortBy) {
                        "Highest FPS" -> compareByDescending<List<GameCompatibilityReport>> { group ->
                            group.mapNotNull { it.averageFps }.maxOrNull() ?: -1.0
                        }
                        "Verified first" -> compareByDescending<List<GameCompatibilityReport>> { group ->
                            group.any { it.reviewStatus == "verified" }
                        }.thenByDescending { group -> group.maxOfOrNull { it.createdAt.orEmpty() }.orEmpty() }
                        else -> compareByDescending<List<GameCompatibilityReport>> { group ->
                            group.maxOfOrNull { it.createdAt.orEmpty() }.orEmpty()
                        }
                    }
                )

            if (selectedGame == null) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        AppText("Game library", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        AppText("${gameGroups.size} games • ${filtered.size} test reports", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (filtered.count { it.reviewStatus == "verified" } > 0) {
                        AssistChip(onClick = { sortBy = "Verified first" }, label = { Icon(Icons.Default.CheckCircle, null); Spacer(Modifier.width(4.dp)); AppText("${filtered.count { it.reviewStatus == "verified" }} verified") })
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp, top = 4.dp)
                ) {
                    if (loading) {
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    }
                    if (gameGroups.isEmpty() && !loading) {
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                shape = RoundedCornerShape(18.dp)
                            ) {
                                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.SportsEsports, contentDescription = null, modifier = Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
                                    AppText("No community tests yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    AppText("Be the first to share a game result. Add the game, your GPU/driver and emulator settings, and at least one screenshot or gameplay clip. Your report will be visible to other users as a community result.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Button(onClick = { showForm = true }, modifier = Modifier.fillMaxWidth()) {
                                        Icon(Icons.Default.Add, null)
                                        Spacer(Modifier.width(6.dp))
                                        AppText("SHARE THE FIRST TEST")
                                    }
                                }
                            }
                        }
                    }
                    gridItems(gameGroups, key = { it.first().gameName.lowercase(Locale.ROOT) }) { gameReports ->
                        val gameReport = gameReports.first()
                        val gameKey = gameReport.gameName.trim().lowercase(Locale.ROOT)
                        val coverUrl = coverUrls[gameKey]
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { selectedGame = gameReport.gameName },
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                        ) {
                            Column {
                                Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                                    if (coverUrl != null) {
                                        AsyncImage(
                                            model = coverUrl,
                                            contentDescription = "${gameReport.gameName} cover",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Box(
                                            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.SportsEsports, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    Surface(
                                        modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
                                        shape = RoundedCornerShape(20.dp),
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
                                    ) {
                                        AppText(
                                            if (gameReports.any { it.reviewStatus == "verified" }) "VERIFIED" else "COMMUNITY",
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    AppText(gameReport.gameName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 2)
                                    AppText("${gameReports.size} test(s) • ${gameReports.map { it.gpuFamily }.distinct().size} GPU family", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    val bestFps = gameReports.mapNotNull { it.averageFps }.maxOrNull()
                                    if (bestFps != null) AppText("Best reported: ${"%.1f".format(Locale.US, bestFps)} FPS", style = MaterialTheme.typography.labelMedium)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(4.dp))
                                        AppText("OPEN GAME", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                val gameReports = filtered.filter { it.gameName.equals(selectedGame, ignoreCase = true) }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { selectedGame = null }) { Icon(Icons.Default.ArrowBack, contentDescription = "Back to game library") }
                    Column(Modifier.weight(1f)) {
                        AppText(selectedGame.orEmpty(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        AppText("${gameReports.size} matching community test(s)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                val coverUrl = coverUrls[selectedGame.orEmpty().trim().lowercase(Locale.ROOT)]
                if (coverUrl != null) {
                    AsyncImage(model = coverUrl, contentDescription = "${selectedGame} cover", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(150.dp).padding(horizontal = 16.dp).clip(RoundedCornerShape(18.dp)))
                }
                val systemFps = gameReports.filter { it.driverType == "System Driver" }.mapNotNull { it.averageFps }
                val panvkFps = gameReports.filter { it.driverType == "PanVK" }.mapNotNull { it.averageFps }
                if (systemFps.isNotEmpty() && panvkFps.isNotEmpty()) {
                    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            AppText("Driver comparison snapshot", fontWeight = FontWeight.Bold)
                            AppText("System Driver average: ${"%.1f".format(Locale.US, systemFps.average())} FPS")
                            AppText("PanVK average: ${"%.1f".format(Locale.US, panvkFps.average())} FPS")
                            AppText("Community data only. Compare the device, driver build, resolution and settings before drawing conclusions.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                LazyColumn(
                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(top = 6.dp, bottom = 24.dp)
                ) {
                    if (gameReports.isEmpty()) item { AppText("No test matches the selected filters. Go back and clear or change a filter.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(gameReports, key = { it.id }) { report ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { selectedReport = report },
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = when (report.outcome) {
                                        "Playable" -> MaterialTheme.colorScheme.primaryContainer
                                        "Crashes / does not launch" -> MaterialTheme.colorScheme.errorContainer
                                        else -> MaterialTheme.colorScheme.secondaryContainer
                                    },
                                    modifier = Modifier.size(52.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            when (report.outcome) {
                                                "Playable" -> Icons.Default.CheckCircle
                                                "Crashes / does not launch" -> Icons.Default.ErrorOutline
                                                else -> Icons.Default.Tune
                                            },
                                            contentDescription = null
                                        )
                                    }
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    AppText("${report.gpuFamily} • ${report.driverType}", fontWeight = FontWeight.Bold)
                                    AppText("${report.emulatorName} ${report.emulatorVersion.orEmpty()} • ${report.outcome}", style = MaterialTheme.typography.bodySmall)
                                    AppText("Avg FPS: ${report.averageFps?.let { "%.1f".format(Locale.US, it) } ?: "—"} • ${if (report.reviewStatus == "verified") "Verified" else "Community"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(Icons.Default.ChevronRight, contentDescription = "Open test details")
                            }
                        }
                    }
                }
            }
        }
    }

    selectedReport?.let { report ->
        Dialog(onDismissRequest = { selectedReport = null }) {
            Surface(
                modifier = Modifier.fillMaxWidth().heightIn(max = 680.dp),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            AppText("Test report details", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            AppText(report.gameName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { selectedReport = null }) { Icon(Icons.Default.Close, contentDescription = "Close report") }
                    }
                    val coverUrl = coverUrls[report.gameName.trim().lowercase(Locale.ROOT)]
                    if (coverUrl != null) {
                        AsyncImage(model = coverUrl, contentDescription = "${report.gameName} cover", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(135.dp).clip(RoundedCornerShape(14.dp)))
                    }
                    AssistChip(
                        onClick = {},
                        label = { AppText(if (report.reviewStatus == "verified") "VERIFIED TEST" else if (report.evidenceReviewed) "EVIDENCE REVIEWED" else "COMMUNITY REPORT") }
                    )
                    AppText("${report.gpuFamily} • ${report.driverType} • ${report.emulatorName} ${report.emulatorVersion.orEmpty()}", fontWeight = FontWeight.SemiBold)
                    AppText("Result: ${report.outcome}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    AppText("Device: ${report.deviceModel ?: "not provided"} • SoC: ${report.soc ?: "not provided"}")
                    AppText("Driver build: ${report.driverVersion ?: "—"} • Android: ${report.androidVersion ?: "—"}")
                    AppText("Game version: ${report.gameVersion ?: "—"} • Resolution: ${report.resolution ?: "—"} • Preset: ${report.graphicsPreset ?: "—"}")
                    AppText("FPS: avg ${report.averageFps?.let { "%.1f".format(Locale.US, it) } ?: "—"} • min ${report.minimumFps?.let { "%.1f".format(Locale.US, it) } ?: "—"}")
                    AppText("Duration: ${report.testDurationSeconds?.let { "$it s" } ?: "—"} • Temperature: ${report.deviceTemperatureC?.let { "$it °C" } ?: "—"}")
                    AppText("Wine ${report.wineVersion ?: "—"} • DXVK ${report.dxvkVersion ?: "—"} • VKD3D ${report.vkd3dVersion ?: "—"} • Box64 ${report.box64Version ?: "—"}", style = MaterialTheme.typography.bodySmall)
                    if (!report.graphicsIssues.isNullOrBlank()) AppText("Graphics issues: ${report.graphicsIssues}")
                    AppText("Test notes", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    AppText(report.notes)
                    AppText("${report.evidencePaths.size} evidence file(s) • ${report.username}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    report.evidencePaths.forEachIndexed { index, path ->
                        OutlinedButton(onClick = { openEvidence(path) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(if (path.substringBefore('?').lowercase().let { it.endsWith(".mp4") || it.endsWith(".webm") || it.endsWith(".mov") || it.endsWith(".m4v") }) Icons.Default.PlayCircle else Icons.Default.Image, null)
                            Spacer(Modifier.width(6.dp))
                            AppText("OPEN EVIDENCE ${index + 1}")
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { selectedReport = null; openDiscussion(report) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Forum, null); Spacer(Modifier.width(3.dp)); AppText("DISCUSS") }
                        TextButton(onClick = { saveConfiguration(report); selectedReport = null }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.BookmarkAdd, null); Spacer(Modifier.width(3.dp)); AppText("SAVE CONFIG") }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { translateReportId = if (translateReportId == report.id) null else report.id }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Translate, null); Spacer(Modifier.width(3.dp)); AppText("TRANSLATE") }
                        if (report.userId != userId) TextButton(onClick = { reportToFlag = report; selectedReport = null }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Flag, null); Spacer(Modifier.width(3.dp)); AppText("REPORT") }
                        if (report.userId == userId) TextButton(onClick = { deleteReportTarget = report; selectedReport = null }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.DeleteOutline, null); Spacer(Modifier.width(3.dp)); AppText("DELETE") }
                    }
                    if (translateReportId == report.id) {
                        TranslateReportText(
                            original = report.notes,
                            appLanguage = context.getSharedPreferences("gmailgpu_settings", 0).getString("app_language", "system") ?: "system"
                        )
                    }
                    AppText("Review the device and graphics settings before comparing FPS. Community results are not controlled benchmarks.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    previewUrl?.let { url ->
        val html = if (previewVideo) {
            "<html><body style='margin:0;background:#080a0f;display:flex;align-items:center;justify-content:center;height:100vh'><video controls playsinline style='width:100%;max-height:100vh' src='$url'></video></body></html>"
        } else {
            "<html><body style='margin:0;background:#080a0f;display:flex;align-items:center;justify-content:center;height:100vh'><img src='$url' style='max-width:100%;max-height:100vh;object-fit:contain'/></body></html>"
        }
        Dialog(onDismissRequest = { previewUrl = null }) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppText(if (previewVideo) "Gameplay evidence" else "Screenshot evidence", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        IconButton(onClick = { previewUrl = null }) { Icon(Icons.Default.Close, contentDescription = "Close preview") }
                    }
                    AndroidView(
                        factory = { viewContext ->
                            WebView(viewContext).apply {
                                webViewClient = WebViewClient()
                                settings.javaScriptEnabled = false
                                settings.domStorageEnabled = false
                                loadDataWithBaseURL(url, html, "text/html", "UTF-8", null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(430.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = {
                            runCatching { uriHandler.openUri(url) }
                                .onFailure { error = "Could not open evidence externally: ${it.message}" }
                        }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.OpenInNew, null); Spacer(Modifier.width(4.dp)); AppText("OPEN EXTERNALLY") }
                        Button(onClick = { previewUrl = null }, modifier = Modifier.weight(1f)) { AppText("DONE") }
                    }
                    AppText("Evidence is from a community submission. Review the test conditions before drawing conclusions.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    duplicateReport?.let { duplicate ->
        AlertDialog(
            onDismissRequest = { duplicateReport = null },
            icon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
            title = { AppText("Similar test already exists") },
            text = {
                AppText("A report for ${duplicate.gameName} • ${duplicate.gpuFamily} • ${duplicate.driverType} on ${duplicate.deviceModel ?: "an unspecified device"} already exists. Open the existing report or submit this run as a new result if the test conditions/version are different.")
            },
            confirmButton = {
                TextButton(onClick = {
                    search = duplicate.gameName
                    gpuFilter = duplicate.gpuFamily
                    driverFilter = duplicate.driverType
                    duplicateReport = null
                    showForm = false
                }) { AppText("FIND EXISTING") }
            },
            dismissButton = {
                TextButton(onClick = { duplicateReport = null; submit(forceDuplicate = true) }) { AppText("SUBMIT NEW TEST") }
            }
        )
    }

    discussionReport?.let { report ->
        AlertDialog(
            onDismissRequest = { discussionReport = null },
            title = { AppText("Discussion • ${report.gameName}") },
            text = {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppText("Ask about the test setup or share a reproducible finding. Keep comments respectful and do not post passwords or tokens.", style = MaterialTheme.typography.bodySmall)
                    if (discussionLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (discussions.isEmpty() && !discussionLoading) AppText("No comments yet. Start the discussion.")
                    discussions.forEach { comment ->
                        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    AppText(comment.username, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                                    AppText(comment.createdAt.orEmpty().take(10), style = MaterialTheme.typography.labelSmall)
                                }
                                AppText(comment.body)
                            }
                        }
                    }
                    OutlinedTextField(
                        value = discussionDraft,
                        onValueChange = { discussionDraft = it.take(2000) },
                        label = { AppText("Add a comment") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { postDiscussion() }, enabled = discussionDraft.isNotBlank() && !discussionLoading) { AppText("POST COMMENT") }
            },
            dismissButton = {
                TextButton(onClick = { discussionReport = null }) { AppText("CLOSE") }
            }
        )
    }

    reportToFlag?.let { report ->
        AlertDialog(
            onDismissRequest = { reportToFlag = null },
            icon = { Icon(Icons.Default.Flag, contentDescription = null) },
            title = { AppText("Report a community result") },
            text = { AppText("Send this result to moderators for review because the result may be misleading. Please use report flags responsibly; do not include private information.") },
            confirmButton = {
                TextButton(onClick = { reportToFlag = null; flagReport(report) }) { AppText("SEND TO MODERATORS") }
            },
            dismissButton = {
                TextButton(onClick = { reportToFlag = null }) { AppText("CANCEL") }
            }
        )
    }

    deleteReportTarget?.let { report ->
        AlertDialog(
            onDismissRequest = { deleteReportTarget = null },
            icon = { Icon(Icons.Default.DeleteOutline, contentDescription = null) },
            title = { AppText("Delete your test report?") },
            text = { AppText("This removes your community test and its evidence from the compatibility database. Related comments and watch alerts are removed by database relationships; saved configurations keep their copied settings.") },
            confirmButton = {
                TextButton(onClick = { deleteOwnReport(report) }) { AppText("DELETE REPORT") }
            },
            dismissButton = {
                TextButton(onClick = { deleteReportTarget = null }) { AppText("CANCEL") }
            }
        )
    }
}

@Composable
private fun TranslateReportText(original: String, appLanguage: String) {
    val configuration = LocalConfiguration.current
    val deviceLanguage = remember(configuration) {
        configuration.locales[0]?.language?.lowercase(Locale.ROOT) ?: Locale.getDefault().language
    }
    val resolved = remember(appLanguage, deviceLanguage) { resolveAppLanguage(appLanguage, deviceLanguage) }
    val target = when (resolved) {
        "ar" -> com.google.mlkit.nl.translate.TranslateLanguage.ARABIC
        "pt-BR" -> com.google.mlkit.nl.translate.TranslateLanguage.PORTUGUESE
        else -> null
    }
    var translated by remember(original, resolved) { mutableStateOf<String?>(null) }
    var failed by remember(original, resolved) { mutableStateOf(false) }

    LaunchedEffect(original, resolved) {
        translated = null
        failed = false
        if (target == null) return@LaunchedEffect
        runCatching {
            val source = com.google.mlkit.nl.translate.TranslateLanguage.ENGLISH
            ChatTranslationCache.ensureModel(source, target)
            ChatTranslationCache.translator(source, target).translate(original).await()
        }.onSuccess { translated = it }
         .onFailure { failed = true }
    }

    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            AppText("Translated notes • ${when (resolved) { "ar" -> "Arabic"; "pt-BR" -> "Portuguese (Brazil)"; else -> "English / device default" }}", fontWeight = FontWeight.SemiBold)
            when {
                target == null -> AppText("Choose Arabic or Portuguese in Settings to translate report notes.", style = MaterialTheme.typography.bodySmall)
                translated != null -> AppText(translated!!, style = MaterialTheme.typography.bodyMedium)
                failed -> AppText("Translation could not be downloaded. The original report remains available.", style = MaterialTheme.typography.bodySmall)
                else -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    AppText("Preparing offline translation model…", style = MaterialTheme.typography.bodySmall)
                }
            }
            AppText("Machine translation may alter technical nuance; preserve exact error codes and version strings.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FlowRowCompat(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        options.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { option ->
                    FilterChip(selected = selected == option, onClick = { onSelect(option) }, label = { AppText(option) })
                }
            }
        }
    }
}
