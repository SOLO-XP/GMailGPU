package com.g57.issuehub.ui

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Process
import android.os.Environment
import android.os.StatFs
import com.g57.issuehub.BuildConfig
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

data class DeviceSnapshot(
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val soc: String,
    val gpu: String,
    val appVersion: String,
    val totalRamMb: Long?,
    val availableRamMb: Long?,
    val supportedAbis: String,
    val cpuCores: Int,
    val kernelVersion: String,
    val buildFingerprint: String,
    val securityPatch: String,
    val board: String,
    val hardware: String,
    val deviceCode: String,
    val product: String,
    val bootloader: String,
    val screenResolution: String,
    val screenDensity: String,
    val dataStorageTotalMb: Long?,
    val dataStorageFreeMb: Long?
) {
    fun asReport(): String = buildString {
        appendLine("GMailGPU Smart Diagnostic Report")
        appendLine("Generated: ${java.time.Instant.now()}")
        appendLine("App version: $appVersion")
        appendLine("Device: $manufacturer $model")
        appendLine("Android: $androidVersion")
        appendLine("SoC: $soc")
        appendLine("GPU: $gpu")
        appendLine("RAM total: ${totalRamMb?.let { "$it MB" } ?: "Not available"}")
        appendLine("RAM available: ${availableRamMb?.let { "$it MB" } ?: "Not available"}")
        appendLine("CPU cores: $cpuCores")
        appendLine("Supported ABIs: $supportedAbis")
        appendLine("Kernel: $kernelVersion")
        appendLine("Security patch: $securityPatch")
        appendLine("Build fingerprint: $buildFingerprint")
        appendLine("Board: $board")
        appendLine("Hardware: $hardware")
        appendLine("Device code: $deviceCode")
        appendLine("Product: $product")
        appendLine("Bootloader: $bootloader")
        appendLine("Screen resolution: $screenResolution")
        appendLine("Screen density: $screenDensity")
        appendLine("Data storage total: ${dataStorageTotalMb?.let { "$it MB" } ?: "Not available"}")
        appendLine("Data storage free: ${dataStorageFreeMb?.let { "$it MB" } ?: "Not available"}")
        appendLine()
        appendLine("Environment versions not exposed by Android are left for the reporter to fill in:")
        appendLine("- Emulator (Winlator/GameHub):")
        appendLine("- Emulator version:")
        appendLine("- Driver/version:")
        appendLine("- Wine:")
        appendLine("- DXVK:")
        appendLine("- VKD3D:")
        appendLine("- Box64:")
        appendLine()
        appendLine("Problem details and exact reproduction steps should be added in the issue description.")
        appendLine("Review this report before uploading it.")
    }
}

object DiagnosticCollector {
    fun snapshot(context: Context): DeviceSnapshot {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memory = ActivityManager.MemoryInfo()
        runCatching { activityManager?.getMemoryInfo(memory) }
        val totalMb = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) memory.totalMem / (1024L * 1024L) else null
        val availableMb = if (activityManager != null) memory.availMem / (1024L * 1024L) else null
        val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL.takeIf { it.isNotBlank() && it != "unknown" }
            else null
        val gpuHints = "${Build.HARDWARE} ${Build.DEVICE} ${Build.PRODUCT}".lowercase(Locale.US)
        val gpu = when {
            "mali-g57" in gpuHints || "g57" in gpuHints -> "Mali-G57 (detected from device identifiers; verify exact model)"
            "mali" in gpuHints -> "Mali GPU (exact model not exposed; verify manually)"
            else -> "Not reliably exposed by Android; please enter the GPU model manually"
        }
        val version = runCatching {
            val info = if (Build.VERSION.SDK_INT >= 33) context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
                else context.packageManager.getPackageInfo(context.packageName, 0)
            val versionCode = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
            "${info.versionName ?: "unknown"} ($versionCode)"
        }.getOrDefault("unknown")
        val display = context.resources.displayMetrics
        val storage = runCatching { StatFs(Environment.getDataDirectory().absolutePath) }.getOrNull()
        val storageTotalMb = storage?.let { (it.blockCountLong * it.blockSizeLong) / (1024L * 1024L) }
        val storageFreeMb = storage?.let { (it.availableBlocksLong * it.blockSizeLong) / (1024L * 1024L) }
        val kernel = runCatching { System.getProperty("os.version") }.getOrNull().orEmpty().ifBlank { "Not available" }
        return DeviceSnapshot(
            manufacturer = Build.MANUFACTURER.ifBlank { "Unknown" },
            model = Build.MODEL.ifBlank { Build.DEVICE.ifBlank { "Unknown" } },
            androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            soc = soc ?: Build.HARDWARE.takeIf { it.isNotBlank() && it != "unknown" } ?: "Not exposed by Android",
            gpu = gpu,
            appVersion = version,
            totalRamMb = totalMb,
            availableRamMb = availableMb,
            supportedAbis = Build.SUPPORTED_ABIS?.joinToString(", ") ?: "Not exposed",
            cpuCores = Runtime.getRuntime().availableProcessors(),
            kernelVersion = kernel,
            buildFingerprint = Build.FINGERPRINT.ifBlank { "Not exposed" },
            securityPatch = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Build.VERSION.SECURITY_PATCH else "Not exposed",
            board = Build.BOARD.ifBlank { "Not exposed" },
            hardware = Build.HARDWARE.ifBlank { "Not exposed" },
            deviceCode = Build.DEVICE.ifBlank { "Not exposed" },
            product = Build.PRODUCT.ifBlank { "Not exposed" },
            bootloader = Build.BOOTLOADER.ifBlank { "Not exposed" },
            screenResolution = "${display.widthPixels}x${display.heightPixels}",
            screenDensity = "${display.densityDpi} dpi (scale ${display.density})",
            dataStorageTotalMb = storageTotalMb,
            dataStorageFreeMb = storageFreeMb
        )
    }

    fun createReportFile(context: Context, includeDeviceDetails: Boolean): PickedFile {
        val snapshot = snapshot(context)
        val report = if (includeDeviceDetails) snapshot.asReport() else
            "GMailGPU Smart Diagnostic Report\nGenerated: ${java.time.Instant.now()}\nDevice diagnostic details were disabled in Privacy & File Controls.\n\nFill in emulator/driver versions and add the problem details in the issue description.\n"
        return writeFile(context, "gmailgpu-diagnostic-report.txt", report, "text/plain", "log")
    }

    fun createLogBundle(context: Context, redactSensitive: Boolean, includeAppLogcat: Boolean, includeDeviceDetails: Boolean): PickedFile {
        val snapshot = snapshot(context)
        val processLog = if (!includeAppLogcat) {
            "App-process logcat collection is disabled in Privacy & File Controls.\n"
        } else runCatching {
            val process = ProcessBuilder("logcat", "-d", "-t", "500", "--pid=${Process.myPid()}")
                .redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor(4, TimeUnit.SECONDS)
            output.takeLast(180_000)
        }.getOrElse { "App logcat is unavailable on this Android build.\nReason: ${it.javaClass.simpleName}\n" }
        val safeLog = if (redactSensitive) redact(processLog) else processLog
        val contents = buildString {
            appendLine("GMailGPU Automatic Log Collector")
            appendLine("Generated: ${java.time.Instant.now()}")
            appendLine("App version: ${snapshot.appVersion}")
            if (includeDeviceDetails) {
                appendLine("Device: ${snapshot.manufacturer} ${snapshot.model}")
                appendLine("Android: ${snapshot.androidVersion}")
                appendLine("SoC: ${snapshot.soc}")
                appendLine("GPU: ${snapshot.gpu}")
            } else {
                appendLine("Device details: omitted by Privacy & File Controls.")
            }
            appendLine("Log scope: this app process only; Android may restrict log access.")
            appendLine("App logcat collection enabled: $includeAppLogcat")
            appendLine("Sensitive-data redaction: ${if (redactSensitive) "enabled" else "disabled"}")
            appendLine()
            appendLine("----- APP LOGCAT (best effort) -----")
            appendLine(safeLog.ifBlank { "No app log lines were available." })
        }
        return writeFile(context, "gmailgpu-app-logs.txt", contents, "text/plain", "log")
    }

    private fun writeFile(context: Context, name: String, contents: String, mime: String, type: String): PickedFile {
        val dir = File(context.cacheDir, "diagnostics").apply { mkdirs() }
        val file = File(dir, name)
        file.writeText(contents, Charsets.UTF_8)
        return PickedFile(Uri.fromFile(file), name, mime, type, file.length())
    }

    private fun redact(input: String): String {
        var value = input
        val patterns = listOf(
            Regex("(?i)(authorization\\s*[:=]\\s*bearer\\s+)[^\\s,;]+") to "$1[REDACTED]",
            Regex("(?i)(bearer\\s+)[A-Za-z0-9._~+/-]+=*") to "$1[REDACTED]",
            Regex("(?i)(password|passwd|access_token|refresh_token|api[_-]?key|secret)(\\s*[:=]\\s*)[^\\s,;]+") to "$1$2[REDACTED]",
            Regex("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", RegexOption.IGNORE_CASE) to "[EMAIL REDACTED]"
        )
        patterns.forEach { (pattern, replacement) -> value = pattern.replace(value, replacement) }
        return value
    }
}
