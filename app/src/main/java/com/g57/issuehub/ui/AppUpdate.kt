package com.g57.issuehub.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.g57.issuehub.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

private data class GMailGPURelease(val tag: String, val apkUrl: String)

@Composable
fun AppUpdateSection(
    accent: Color,
    muted: Color,
    surface: Color,
    border: Color
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var working by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Check GitHub for the latest GMailGPU release. Updates install over the existing app when the package signature matches.") }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = surface,
        shape = MaterialTheme.shapes.medium,
        border = androidx.compose.foundation.BorderStroke(1.dp, border)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("APP UPDATES", color = accent, style = MaterialTheme.typography.titleMedium)
            Text(status, color = muted, style = MaterialTheme.typography.bodySmall)
            Button(
                onClick = {
                    if (working) return@Button
                    working = true
                    status = "Checking the latest release…"
                    scope.launch {
                        try {
                            val release = withContext(Dispatchers.IO) { fetchLatestRelease() }
                            val current = BuildConfig.VERSION_NAME
                            if (!isReleaseNewer(release.tag, current)) {
                                status = "You're up to date. Installed version: $current"
                            } else {
                                status = "Update ${release.tag} found. Downloading APK…"
                                val apk = withContext(Dispatchers.IO) {
                                    downloadApk(release.apkUrl, context.cacheDir)
                                }
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                                    !context.packageManager.canRequestPackageInstalls()
                                ) {
                                    status = "Update downloaded. Allow GMailGPU to install apps on the next screen, return here, then tap CHECK FOR UPDATES again."
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                            Uri.parse("package:${context.packageName}")
                                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                } else {
                                    val apkUri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        apk
                                    )
                                    val installIntent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(apkUri, "application/vnd.android.package-archive")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(installIntent)
                                    status = "Installer opened for ${release.tag}. Confirm Update to keep your app data."
                                }
                            }
                        } catch (e: Exception) {
                            status = "Update check failed: ${e.message ?: "network or release error"}. Please try again."
                        } finally {
                            working = false
                        }
                    }
                },
                enabled = !working,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (working) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    androidx.compose.material3.Icon(Icons.Default.SystemUpdate, contentDescription = null)
                    Text("  CHECK FOR UPDATES")
                }
            }
        }
    }
}

private fun fetchLatestRelease(): GMailGPURelease {
    val connection = URL("https://api.github.com/repos/SOLO-XP/GMailGPU/releases/latest")
        .openConnection() as HttpURLConnection
    connection.connectTimeout = 15000
    connection.readTimeout = 20000
    connection.requestMethod = "GET"
    connection.setRequestProperty("Accept", "application/vnd.github+json")
    connection.setRequestProperty("User-Agent", "GMailGPU-Android-Updater")
    try {
        if (connection.responseCode !in 200..299) {
            error("GitHub returned HTTP ${connection.responseCode}")
        }
        val json = connection.inputStream.bufferedReader().use { it.readText() }.let(::JSONObject)
        val tag = json.optString("tag_name").takeIf { it.isNotBlank() }
            ?: error("The latest release has no version tag")
        val assets = json.optJSONArray("assets") ?: error("The release has no APK assets")
        var apkUrl: String? = null
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val name = asset.optString("name")
            val url = asset.optString("browser_download_url")
            if (name.endsWith(".apk", ignoreCase = true) && url.startsWith("https://github.com/")) {
                apkUrl = url
                break
            }
        }
        return GMailGPURelease(tag, apkUrl ?: error("No APK was attached to the latest release"))
    } finally {
        connection.disconnect()
    }
}

private fun downloadApk(url: String, cacheDir: File): File {
    require(url.startsWith("https://github.com/")) { "Untrusted APK download URL" }
    val directory = File(cacheDir, "updates").apply { mkdirs() }
    val temporary = File(directory, "GMailGPU-update.apk.part")
    val destination = File(directory, "GMailGPU-update.apk")
    val connection = URL(url).openConnection() as HttpURLConnection
    connection.connectTimeout = 20000
    connection.readTimeout = 60000
    connection.setRequestProperty("User-Agent", "GMailGPU-Android-Updater")
    try {
        if (connection.responseCode !in 200..299) error("APK download returned HTTP ${connection.responseCode}")
        connection.inputStream.use { input ->
            temporary.outputStream().use { output -> input.copyTo(output) }
        }
        if (temporary.length() < 1024L * 1024L) error("Downloaded APK is unexpectedly small")
        if (destination.exists()) destination.delete()
        if (!temporary.renameTo(destination)) error("Could not prepare the downloaded APK")
        return destination
    } finally {
        connection.disconnect()
        if (temporary.exists()) temporary.delete()
    }
}

private fun isReleaseNewer(remoteTag: String, currentVersion: String): Boolean {
    fun numbers(value: String): List<Int> =
        Regex("""\d+""").findAll(value).take(3).map { it.value.toIntOrNull() ?: 0 }.toList()
            .let { it + List((3 - it.size).coerceAtLeast(0)) { 0 } }

    val remote = numbers(remoteTag)
    val current = numbers(currentVersion)
    for (i in 0..2) {
        if (remote[i] != current[i]) return remote[i] > current[i]
    }
    val remoteLower = remoteTag.lowercase(Locale.ROOT)
    val currentLower = currentVersion.lowercase(Locale.ROOT)
    if (remoteLower == currentLower || remoteLower.removePrefix("v") == currentLower.removePrefix("v")) return false
    val remoteHotfix = listOf("hotfix", "patch", "fix").any { it in remoteLower }
    val currentHotfix = listOf("hotfix", "patch", "fix").any { it in currentLower }
    return remoteHotfix && !currentHotfix
}

