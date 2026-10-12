package com.g57.issuehub.ui

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
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
        // Query the actual OpenGL renderer through a tiny off-screen EGL context.
        // This is more reliable than guessing the GPU from the device codename.
        val gpu = detectGpuRenderer()
            ?: run {
                val gpuHints = "${Build.HARDWARE} ${Build.DEVICE} ${Build.PRODUCT}".lowercase(Locale.US)
                when {
                    "mali-g57" in gpuHints || "g57" in gpuHints -> "Mali-G57 (device identifier hint; exact renderer unavailable)"
                    "mali" in gpuHints -> "Mali GPU (device identifier hint; exact renderer unavailable)"
                    else -> "GPU renderer unavailable from Android graphics API"
                }
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

    private fun detectGpuRenderer(): String? {
        var display = EGL14.EGL_NO_DISPLAY
        var context = EGL14.EGL_NO_CONTEXT
        var surface = EGL14.EGL_NO_SURFACE
        try {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (display == EGL14.EGL_NO_DISPLAY) return null
            val version = IntArray(2)
            if (!EGL14.eglInitialize(display, version, 0, version, 1)) return null

            val configAttributes = intArrayOf(
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RENDERABLE_TYPE, 4, // EGL_OPENGL_ES2_BIT
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val configCount = IntArray(1)
            if (!EGL14.eglChooseConfig(display, configAttributes, 0, configs, 0, 1, configCount, 0) ||
                configCount[0] == 0 || configs[0] == null
            ) return null

            context = EGL14.eglCreateContext(
                display, configs[0], EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0
            )
            if (context == EGL14.EGL_NO_CONTEXT) return null
            surface = EGL14.eglCreatePbufferSurface(
                display, configs[0],
                intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0
            )
            if (surface == EGL14.EGL_NO_SURFACE ||
                !EGL14.eglMakeCurrent(display, surface, surface, context)
            ) return null

            val renderer = GLES20.glGetString(GLES20.GL_RENDERER)?.trim().orEmpty()
            val vendor = GLES20.glGetString(GLES20.GL_VENDOR)?.trim().orEmpty()
            val glVersion = GLES20.glGetString(GLES20.GL_VERSION)?.trim().orEmpty()
            if (renderer.isBlank() || renderer.equals("unknown", ignoreCase = true)) return null
            return buildList {
                add(renderer)
                if (vendor.isNotBlank()) add("Vendor: $vendor")
                if (glVersion.isNotBlank()) add("API: $glVersion")
            }.joinToString(" | ")
        } catch (_: Throwable) {
            return null
        } finally {
            runCatching {
                if (display != EGL14.EGL_NO_DISPLAY) {
                    EGL14.eglMakeCurrent(
                        display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT
                    )
                    if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
                    if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
                    EGL14.eglTerminate(display)
                }
            }
        }
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
