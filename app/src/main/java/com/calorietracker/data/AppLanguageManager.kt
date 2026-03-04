package dev.kkrow.calorietracker.data

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

object AppLanguageManager {

    data class LanguageOption(val tag: String, val label: String)

    private const val PREF_NAME = "language_prefs"
    private const val KEY_INITIALIZED = "language_initialized"
    private const val KEY_SELECTED_TAG = "selected_language_tag"

    private val supportedTags = listOf(
        "en", "ru", "es", "fr", "de", "pt", "it", "tr", "pl", "ja", "ko", "zh-CN", "hi"
    )

    fun ensureLanguageInitialized(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val initialized = prefs.getBoolean(KEY_INITIALIZED, false)
        if (!initialized) {
            val system = LocaleListCompat.getAdjustedDefault()[0]?.toLanguageTag().orEmpty()
            val initialTag = normalizeTag(system)
            applyLanguageTag(initialTag)
            prefs.edit()
                .putBoolean(KEY_INITIALIZED, true)
                .putString(KEY_SELECTED_TAG, initialTag)
                .apply()
            return
        }

        val saved = prefs.getString(KEY_SELECTED_TAG, null)
        if (!saved.isNullOrBlank()) {
            applyLanguageTag(saved)
        }
    }

    fun setLanguage(context: Context, requestedTag: String) {
        val tag = normalizeTag(requestedTag)
        applyLanguageTag(tag)
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_INITIALIZED, true)
            .putString(KEY_SELECTED_TAG, tag)
            .apply()
    }

    fun getCurrentLanguageTag(context: Context): String {
        val saved = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SELECTED_TAG, null)
        if (!saved.isNullOrBlank()) {
            return saved
        }

        val appLocaleTag = AppCompatDelegate.getApplicationLocales()[0]?.toLanguageTag()
        return normalizeTag(appLocaleTag.orEmpty())
    }

    fun getSupportedLanguages(): List<LanguageOption> {
        return listOf(
            LanguageOption("en", "English"),
            LanguageOption("ru", "Русский"),
            LanguageOption("es", "Español"),
            LanguageOption("fr", "Français"),
            LanguageOption("de", "Deutsch"),
            LanguageOption("pt", "Português"),
            LanguageOption("it", "Italiano"),
            LanguageOption("tr", "Türkçe"),
            LanguageOption("pl", "Polski"),
            LanguageOption("ja", "日本語"),
            LanguageOption("ko", "한국어"),
            LanguageOption("zh-CN", "简体中文"),
            LanguageOption("hi", "हिन्दी")
        )
    }

    private fun applyLanguageTag(tag: String) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }

    private fun normalizeTag(rawTag: String): String {
        if (rawTag.isBlank()) return "en"

        val lower = rawTag.lowercase()
        if (lower.startsWith("ar")) return "en"
        if (lower.startsWith("zh")) return "zh-CN"

        val exact = supportedTags.firstOrNull { it.equals(rawTag, ignoreCase = true) }
        if (exact != null) return exact

        val langPart = lower.substringBefore('-')
        val byLang = supportedTags.firstOrNull { it.lowercase().substringBefore('-') == langPart }
        return byLang ?: "en"
    }
}
