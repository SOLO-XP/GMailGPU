package com.g57.issuehub

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentifier
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.nl.translate.TranslateLanguage
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentHashMap
import java.util.Locale
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Original chat text is shown immediately. Language identification and translators are
 * reused across messages to avoid repeatedly creating clients and reinitializing models.
 */
internal object ChatTranslationCache {
    val identifier: LanguageIdentifier by lazy { LanguageIdentification.getClient() }
    private val translators = ConcurrentHashMap<String, Translator>()
    private val modelLocks = ConcurrentHashMap<String, Mutex>()
    private val readyModels = ConcurrentHashMap.newKeySet<String>()

    fun translator(source: String, target: String): Translator =
        translators.getOrPut("$source->$target") {
            Translation.getClient(
                TranslatorOptions.Builder()
                    .setSourceLanguage(source)
                    .setTargetLanguage(target)
                    .build()
            )
        }

    suspend fun ensureModel(source: String, target: String) {
        val key = "$source->$target"
        if (readyModels.contains(key)) return
        modelLocks.getOrPut(key) { Mutex() }.withLock {
            if (!readyModels.contains(key)) {
                translator(source, target)
                    .downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
                readyModels.add(key)
            }
        }
    }
}

internal fun resolveAppLanguage(appLanguage: String, deviceLanguage: String? = null): String {
    if (appLanguage != "system") return appLanguage
    val language = (deviceLanguage ?: Locale.getDefault().language).lowercase(Locale.ROOT)
    return when {
        language == "ar" || language.startsWith("ar-") -> "ar"
        language == "pt" || language.startsWith("pt-") -> "pt-BR"
        else -> "en"
    }
}

private fun targetTranslateCode(appLanguage: String): String? {
    val resolved = resolveAppLanguage(appLanguage)
    val tag = when (resolved) {
        "pt-BR" -> "pt"
        "ar" -> "ar"
        "en" -> "en"
        else -> Locale.getDefault().language
    }
    return TranslateLanguage.fromLanguageTag(tag)
}

@Composable
internal fun AutoTranslatedMessage(
    original: String,
    appLanguage: String = Locale.getDefault().language,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val deviceLanguage = remember(configuration) {
        configuration.locales[0]?.language?.lowercase(Locale.ROOT)
            ?: Locale.getDefault().language.lowercase(Locale.ROOT)
    }
    val resolvedLanguage = remember(appLanguage, deviceLanguage) {
        if (appLanguage == "system") {
            when (deviceLanguage) {
                "ar" -> "ar"
                "pt", "pt-br" -> "pt-BR"
                else -> "en"
            }
        } else resolveAppLanguage(appLanguage)
    }
    val targetLanguage = remember(resolvedLanguage) { targetTranslateCode(resolvedLanguage) }
    var translated by remember(original, targetLanguage) { mutableStateOf<String?>(null) }
    var translationStatus by remember(original, targetLanguage) { mutableStateOf("pending") }
    var retryKey by remember(original, targetLanguage) { mutableStateOf(0) }

    LaunchedEffect(original, targetLanguage, retryKey) {
        translated = null
        if (original.isBlank() || targetLanguage == null) {
            translationStatus = "unavailable"
            return@LaunchedEffect
        }
        translationStatus = "translating"
        try {
            val sourceLanguage = ChatTranslationCache.identifier.identifyLanguage(original).await()
            val sourceCode = if (sourceLanguage == "und") TranslateLanguage.ENGLISH
                else TranslateLanguage.fromLanguageTag(sourceLanguage)
            if (sourceCode == null || sourceCode == targetLanguage) {
                translationStatus = "same"
                return@LaunchedEffect
            }
            val translator = ChatTranslationCache.translator(sourceCode, targetLanguage)
            // Models are cached by ML Kit after first download; original remains visible meanwhile.
            ChatTranslationCache.ensureModel(sourceCode, targetLanguage)
            translated = translator.translate(original).await()
            translationStatus = "done"
        } catch (_: Exception) {
            translationStatus = "unavailable"
        }
    }

    androidx.compose.foundation.layout.Column(modifier = modifier) {
        Text(original)
        when {
            translationStatus == "done" && translated != null && translated != original -> {
                val targetLabel = when (resolvedLanguage) {
                    "ar" -> "العربية"
                    "pt-BR" -> "Português (Brasil)"
                    else -> "English"
                }
                AppText(
                    "AUTO-TRANSLATED TO $targetLabel",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(translated!!, style = MaterialTheme.typography.bodyMedium)
            }
            translationStatus == "translating" -> {
                AppText(
                    "Automatic translation to $resolvedLanguage… The first translation may take a little while while the language model downloads. Please wait; the original message remains visible.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            translationStatus == "unavailable" -> {
                androidx.compose.foundation.layout.Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    AppText(
                        "Translation unavailable · original shown.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = { retryKey++ }) {
                        AppText("RETRY")
                    }
                }
            }
        }
    }
}
