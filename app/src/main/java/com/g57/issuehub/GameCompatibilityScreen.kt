package com.g57.issuehub

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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

@Serializable
private data class GameCompatibilityReport(
    val id: String,
    @SerialName("user_id") val userId: String,
    val username: String,
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
    var duplicateReport by remember { mutableStateOf<GameCompatibilityReport?>(null) }
    var discussionReport by remember { mutableStateOf<GameCompatibilityReport?>(null) }
    var discussions by remember { mutableStateOf(emptyList<GameDiscussion>()) }
    var discussionDraft by remember { mutableStateOf("") }
    var discussionLoading by remember { mutableStateOf(false) }
    var reportToFlag by remember { mutableStateOf<GameCompatibilityReport?>(null) }
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
    var showForm by remember { mutableStateOf(false) }

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
            runCatching {
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
                }
                client.from("game_compatibility_reports").insert(buildJsonObject {
                    put("id", id)
                    put("user_id", userId)
                    put("username", username)
                    put("game_name", game.trim())
                    put("game_version", gameVersion.ifBlank { null })
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
                game = ""; gameVersion = ""; driverVersion = ""; emulatorVersion = ""; wine = ""; dxvk = ""; vkd3d = ""; box64 = ""
                resolution = ""; preset = ""; avgFps = ""; minFps = ""; testDuration = ""; deviceTemperature = ""; graphicsIssues = ""; notes = ""; evidence = emptyList()
                showForm = false
                refresh()
            }.onFailure {
                error = "Submission failed: ${it.message ?: "unknown error"}. Check the Supabase migration and Storage policies."
                runCatching { client.storage.from("game-evidence").delete(*uploaded.toTypedArray()) }
            }
            submitting = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
            Column(Modifier.weight(1f)) {
                Text("Game Compatibility Library", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Community evidence • System Driver vs PanVK", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { refresh() }, enabled = !loading) { Icon(Icons.Default.Refresh, contentDescription = "Refresh") }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { showForm = !showForm }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text(if (showForm) "HIDE FORM" else "ADD GAME RESULT")
            }
        }
        if (notice != null) Text(notice!!, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.primary)
        if (error != null) Text(error!!, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error)
        if (showForm) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("1 • Game and device", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                OutlinedTextField(game, { game = it }, label = { Text("Game name *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(gameVersion, { gameVersion = it }, label = { Text("Game version") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Text("GPU family")
                FlowRowCompat(options = gameGpuOptions, selected = gpu, onSelect = { gpu = it })
                OutlinedTextField(device, { device = it }, label = { Text("Phone / device model") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(soc, { soc = it }, label = { Text("SoC (e.g. Dimensity 6100+)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(android, { android = it }, label = { Text("Android version") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

                HorizontalDivider()
                Text("2 • Driver and compatibility layers", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                FlowRowCompat(options = gameDriverOptions, selected = driver, onSelect = { driver = it })
                OutlinedTextField(driverVersion, { driverVersion = it }, label = { Text("Driver build / version") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(emulator, { emulator = it }, label = { Text("Winlator / GameHub / other") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(emulatorVersion, { emulatorVersion = it }, label = { Text("Emulator version") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(wine, { wine = it }, label = { Text("Wine version") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(dxvk, { dxvk = it }, label = { Text("DXVK version") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(vkd3d, { vkd3d = it }, label = { Text("VKD3D-Proton version") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(box64, { box64 = it }, label = { Text("Box64 version") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

                HorizontalDivider()
                Text("3 • Test results", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                OutlinedTextField(resolution, { resolution = it }, label = { Text("Resolution (e.g. 800×600)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(preset, { preset = it }, label = { Text("Graphics settings / preset") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(avgFps, { avgFps = it }, label = { Text("Average FPS (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(minFps, { minFps = it }, label = { Text("Minimum FPS (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(testDuration, { testDuration = it.filter(Char::isDigit).take(6) }, label = { Text("Test duration in seconds (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(deviceTemperature, { deviceTemperature = it.filter { ch -> ch.isDigit() || ch == '.' }.take(6) }, label = { Text("Device temperature °C (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                FlowRowCompat(options = gameOutcomeOptions, selected = outcome, onSelect = { outcome = it })
                OutlinedTextField(graphicsIssues, { graphicsIssues = it }, label = { Text("Graphics issues (black textures, glitches, etc.)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                OutlinedTextField(notes, { notes = it }, label = { Text("Test notes / steps to reproduce *") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("4 • Evidence *", fontWeight = FontWeight.Bold)
                        Text("Attach screenshots or gameplay video showing the result. Do not include private messages, account details, or tokens.", style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = { picker.launch(arrayOf("image/*", "video/*")) }) {
                            Icon(Icons.Default.AttachFile, null); Spacer(Modifier.width(6.dp)); Text("SELECT PHOTOS / VIDEOS")
                        }
                        evidence.forEachIndexed { index, uri ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (context.contentResolver.getType(uri).orEmpty().startsWith("video/")) Icons.Default.Videocam else Icons.Default.Image, null)
                                Text(uri.lastPathSegment ?: "Evidence ${index + 1}", Modifier.weight(1f), maxLines = 1)
                                IconButton(onClick = { evidence = evidence.toMutableList().apply { removeAt(index) } }) { Icon(Icons.Default.Close, "Remove") }
                            }
                        }
                    }
                }
                Button(onClick = { submit() }, enabled = !submitting, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    if (submitting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp)); Text(if (submitting) "UPLOADING EVIDENCE…" else "SUBMIT COMMUNITY RESULT")
                }
            }
        } else {
            OutlinedTextField(search, { search = it }, label = { Text("Search game, GPU, driver, or emulator") }, leadingIcon = { Icon(Icons.Default.Search, null) }, modifier = Modifier.fillMaxWidth().padding(16.dp), singleLine = true)
            val filtered = reports.filter { report ->
                (gpuFilter == "All GPUs" || report.gpuFamily == gpuFilter) &&
                    (driverFilter == "Any driver" || report.driverType == driverFilter) &&
                    (outcomeFilter == "Any outcome" || report.outcome == outcomeFilter) &&
                    (deviceFilter.isBlank() || report.deviceModel.orEmpty().contains(deviceFilter, ignoreCase = true)) &&
                    listOf(report.gameName, report.gpuFamily, report.driverType, report.driverVersion.orEmpty(), report.emulatorName, report.emulatorVersion.orEmpty(), report.outcome, report.deviceModel.orEmpty(), report.dxvk.orEmpty(), report.vkd3d.orEmpty())
                        .any { value -> value.contains(search, ignoreCase = true) }
            }.let { rows ->
                when (sortBy) {
                    "Highest FPS" -> rows.sortedByDescending { it.averageFps ?: -1.0 }
                    "Verified first" -> rows.sortedWith(compareByDescending<GameCompatibilityReport> { it.reviewStatus == "verified" }.thenByDescending { it.createdAt.orEmpty() })
                    else -> rows.sortedByDescending { it.createdAt.orEmpty() }
                }
            }
            val comparisons = filtered.filter { it.averageFps != null }
                .groupBy { it.gameName.lowercase() to it.gpuFamily }
                .mapNotNull { (_, rows) ->
                    val systemRuns = rows.filter { it.driverType == "System Driver" && it.averageFps != null }
                    val panvkRuns = rows.filter { it.driverType == "PanVK" && it.averageFps != null }
                    if (systemRuns.isNotEmpty() && panvkRuns.isNotEmpty()) Triple(rows.first(), systemRuns, panvkRuns) else null
                }.take(4)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (listOf("All GPUs") + gameGpuOptions.filter { it != "Other" }).forEach { option ->
                    FilterChip(selected = gpuFilter == option, onClick = { gpuFilter = option }, label = { Text(option) })
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Any driver", "System Driver", "PanVK", "Other").forEach { option ->
                    FilterChip(selected = driverFilter == option, onClick = { driverFilter = option }, label = { Text(option) })
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (listOf("Any outcome") + gameOutcomeOptions).forEach { option ->
                    FilterChip(selected = outcomeFilter == option, onClick = { outcomeFilter = option }, label = { Text(option) })
                }
            }
            OutlinedTextField(deviceFilter, { deviceFilter = it }, label = { Text("Optional device-model filter") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), singleLine = true)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Newest", "Highest FPS", "Verified first").forEach { option ->
                    FilterChip(selected = sortBy == option, onClick = { sortBy = option }, label = { Text("Sort: $option") })
                }
            }
            LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                if (filtered.isEmpty() && !loading) item { Text("No community results yet. Add the first evidence-backed game report.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp)) }
                if (filtered.isNotEmpty()) item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Community coverage", fontWeight = FontWeight.Bold)
                            Text("${filtered.size} visible test(s) • ${filtered.map { it.gameName.lowercase() }.distinct().size} game(s) • ${filtered.count { it.reviewStatus == "verified" }} verified")
                            gameGpuOptions.filter { it != "Other" }.forEach { family ->
                                val amount = filtered.count { it.gpuFamily == family }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(family, Modifier.width(94.dp), style = MaterialTheme.typography.bodySmall)
                                    LinearProgressIndicator(progress = { if (filtered.isEmpty()) 0f else amount.toFloat()/filtered.size }, modifier = Modifier.weight(1f).height(6.dp))
                                    Text(" $amount", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
                val regressions = filtered.filter { it.averageFps != null }
                    .groupBy { Triple(it.gameName.lowercase(), it.gpuFamily, it.driverType) }
                    .mapNotNull { (_, runs) ->
                        val sortedRuns = runs.sortedByDescending { it.createdAt.orEmpty() }
                        if (sortedRuns.size < 2) null else {
                            val newer = sortedRuns[0]; val older = sortedRuns[1]
                            val oldFps = older.averageFps ?: return@mapNotNull null
                            val newFps = newer.averageFps ?: return@mapNotNull null
                            if (oldFps <= 0.0 || newFps >= oldFps * 0.90) null else Triple(newer, older, ((oldFps-newFps)/oldFps)*100.0)
                        }
                    }.take(3)
                if (regressions.isNotEmpty()) item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("Possible performance regressions", fontWeight = FontWeight.Bold)
                            regressions.forEach { (latest, previous, drop) ->
                                Text("${latest.gameName} • ${latest.gpuFamily} • ${latest.driverType}: latest FPS ${latest.averageFps}, previously ${previous.averageFps} (about ${"%.0f".format(drop)}% lower).")
                            }
                            Text("This is a signal from two community records, not proof that the driver update caused the change.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                items(comparisons, key = { "compare-${it.first.id}" }) { comparison ->
                    val base = comparison.first
                    val systemRuns = comparison.second
                    val panvkRuns = comparison.third
                    val systemAverage = systemRuns.mapNotNull { it.averageFps }.average()
                    val panvkAverage = panvkRuns.mapNotNull { it.averageFps }.average()
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("FPS comparison • ${base.gameName} • ${base.gpuFamily}", fontWeight = FontWeight.Bold)
                            Text("System Driver average: ${"%.1f".format(systemAverage)} FPS (${systemRuns.size} run(s))")
                            Text("PanVK average: ${"%.1f".format(panvkAverage)} FPS (${panvkRuns.size} run(s))")
                            Text("Community-submitted results only. Devices, driver builds, resolution, and settings may differ; this is not a controlled benchmark.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                items(filtered, key = { it.id }) { report ->
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(report.gameName, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                AssistChip(onClick = {}, label = { Text(if (report.reviewStatus == "verified") "VERIFIED TEST" else if (report.evidenceReviewed) "EVIDENCE REVIEWED" else "COMMUNITY REPORT") })
                            }
                            Text("${report.gpuFamily} • ${report.driverType} • ${report.emulatorName} ${report.emulatorVersion.orEmpty()}")
                            Text(report.outcome, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                            Text("FPS: avg ${report.averageFps?.let { "%.1f".format(it) } ?: "—"} • min ${report.minimumFps?.let { "%.1f".format(it) } ?: "—"}")
                            Text("Duration: ${report.testDurationSeconds?.let { "$it s" } ?: "—"} • Temperature: ${report.deviceTemperatureC?.let { "$it °C" } ?: "—"}", style = MaterialTheme.typography.bodySmall)
                            Text("Device: ${report.deviceModel ?: "not provided"} • SoC: ${report.soc ?: "not provided"}")
                            Text("Wine ${report.wineVersion ?: "—"} • DXVK ${report.dxvkVersion ?: "—"} • VKD3D ${report.vkd3dVersion ?: "—"} • Box64 ${report.box64Version ?: "—"}", style = MaterialTheme.typography.bodySmall)
                            Text(report.notes, style = MaterialTheme.typography.bodyMedium)
                            if (!report.graphicsIssues.isNullOrBlank()) Text("Graphics: ${report.graphicsIssues}")
                            Text("${report.evidencePaths.size} evidence file(s) • ${report.username}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            report.evidencePaths.forEachIndexed { index, path ->
                                TextButton(onClick = { openEvidence(path) }) { Icon(if (path.substringBefore('?').lowercase().let { it.endsWith(".mp4") || it.endsWith(".webm") || it.endsWith(".mov") }) Icons.Default.PlayCircle else Icons.Default.Image, null); Spacer(Modifier.width(4.dp)); Text("Preview evidence ${index + 1}") }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { openDiscussion(report) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Forum, null); Spacer(Modifier.width(3.dp)); Text("DISCUSS") }
                                TextButton(onClick = { saveConfiguration(report) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.BookmarkAdd, null); Spacer(Modifier.width(3.dp)); Text("SAVE CONFIG") }
                                if (report.userId != userId) TextButton(onClick = { reportToFlag = report }) { Icon(Icons.Default.Flag, null); Text("FLAG") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FlowRowCompat(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        options.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { option ->
                    FilterChip(selected = selected == option, onClick = { onSelect(option) }, label = { Text(option) })
                }
            }
        }
    }
}
