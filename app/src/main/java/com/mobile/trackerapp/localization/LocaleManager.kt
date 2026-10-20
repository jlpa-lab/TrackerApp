package com.mobile.trackerapp.localization

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.mobile.trackerapp.pref.AppSharedPreferencesApp

/** Single source of truth for the app's selected locale. */
object LocaleManager {
    const val DEFAULT_LANGUAGE = "en"

    fun currentLanguage(context: Context): String =
        AppSharedPreferencesApp(context.applicationContext).languageCode.ifBlank { DEFAULT_LANGUAGE }

    fun apply(context: Context, languageTag: String) {
        val normalized = languageTag.ifBlank { DEFAULT_LANGUAGE }
        AppSharedPreferencesApp(context.applicationContext).languageCode = normalized
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(normalized))
    }

    fun initialize(context: Context) {
        val languageTag = currentLanguage(context)
        val applicationLocales = AppCompatDelegate.getApplicationLocales()
        if (applicationLocales.isEmpty || applicationLocales.toLanguageTags() != languageTag) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageTag))
        }
    }
}
