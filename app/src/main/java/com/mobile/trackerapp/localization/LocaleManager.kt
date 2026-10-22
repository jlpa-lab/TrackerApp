package com.location.and.phone.tracker.locationnphonetracker.localization

import android.content.Context
import com.location.and.phone.tracker.locationnphonetracker.pref.AppSharedPreferencesApp

/** Single source of truth for the app's selected locale. */
object LocaleManager {
    const val DEFAULT_LANGUAGE = "en"

    fun currentLanguage(context: Context): String =
        AppSharedPreferencesApp(context.applicationContext).languageCode.ifBlank { DEFAULT_LANGUAGE }

    fun apply(context: Context, languageTag: String) {
        val normalized = languageTag.ifBlank { DEFAULT_LANGUAGE }
        AppSharedPreferencesApp(context.applicationContext).languageCode = normalized
        updateResources(context, normalized)
    }

    fun initialize(context: Context) {
        updateResources(context, currentLanguage(context))
    }

    /** Applies the persisted app language before an activity inflates its layout. */
    fun applyPersisted(context: Context) {
        updateResources(context, currentLanguage(context))
    }

    private fun updateResources(context: Context, languageTag: String) {
        val locale = java.util.Locale.forLanguageTag(languageTag)
        java.util.Locale.setDefault(locale)

        // Keep both the activity and application resource contexts aligned.
        // This deliberately uses the saved in-app language, not the device
        // locale, so every activity starts with the user's selection.
        val targets = linkedSetOf(context, context.applicationContext)
        targets.forEach { target ->
            val configuration = android.content.res.Configuration(target.resources.configuration)
            configuration.setLocale(locale)
            @Suppress("DEPRECATION")
            target.resources.updateConfiguration(configuration, target.resources.displayMetrics)
        }
    }
}
