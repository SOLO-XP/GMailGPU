package com.g57.issuehub

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import com.google.mlkit.nl.translate.TranslateLanguage
import java.util.concurrent.ConcurrentHashMap

internal val LocalAppLanguage = compositionLocalOf { "en" }

internal object UiTextTranslationCache {
    private val translations = ConcurrentHashMap<String, String>()
    private fun key(language: String, text: String) = "$language|$text"
    fun get(language: String, text: String): String? = translations[key(language, text)]
    fun put(language: String, text: String, translated: String) {
        if (translated.isNotBlank()) translations[key(language, text)] = translated
    }
}
