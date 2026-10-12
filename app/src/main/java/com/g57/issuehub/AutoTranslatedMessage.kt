package com.g57.issuehub

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.nl.translate.TranslateLanguage
import kotlinx.coroutines.tasks.await
import java.util.Locale

/**
 * Shows the sender's original message and, when possible, an on-device translation
 * into the language configured on this device. The stored chat message is never changed.
 */
@Composable
internal fun AutoTranslatedMessage(original: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val targetLanguage = remember { TranslateLanguage.fromLanguageTag(Locale.getDefault().language) }
    var translated by remember(original, targetLanguage) { mutableStateOf<String?>(null) }
    var translationStatus by remember(original, targetLanguage) { mutableStateOf("pending") }

    LaunchedEffect(original, targetLanguage) {
        translated = null
        if (original.isBlank() || targetLanguage == null) {
            translationStatus = "unavailable"
            return@LaunchedEffect
        }
        translationStatus = "translating"
        var translator: com.google.mlkit.nl.translate.Translator? = null
        try {
            val identifier = LanguageIdentification.getClient()
            val sourceLanguage = identifier.identifyLanguage(original).await()
            identifier.close()
            val sourceCode = if (sourceLanguage == "und") null
                else TranslateLanguage.fromLanguageTag(sourceLanguage)
            if (sourceCode == null || sourceCode == targetLanguage) {
                translationStatus = "same"
                return@LaunchedEffect
            }
            translator = Translation.getClient(
                TranslatorOptions.Builder()
                    .setSourceLanguage(sourceCode)
                    .setTargetLanguage(targetLanguage)
                    .build()
            )
            translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
            translated = translator.translate(original).await()
            translationStatus = "done"
        } catch (_: Exception) {
            // Unsupported language, unavailable model, or network/model-download failure:
            // preserve the original message without blocking the chat.
            translationStatus = "unavailable"
        } finally {
            translator?.close()
        }
    }

    androidx.compose.foundation.layout.Column(modifier = modifier) {
        Text(original)
        when {
            translationStatus == "done" && translated != null && translated != original -> {
                Text(
                    "Translation · ${Locale.getDefault().displayLanguage}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(translated!!, style = MaterialTheme.typography.bodyMedium)
            }
            translationStatus == "translating" -> {
                Text(
                    "Checking translation…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            translationStatus == "unavailable" -> {
                Text(
                    "Translation unavailable · original shown",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
