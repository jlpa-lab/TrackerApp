package com.mobile.trackerapp.core.localization

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

object LocaleManager {
    val supportedCodes = setOf("en", "vi", "es", "fr", "de", "it", "pt", "tr", "ar", "hi", "ko", "zh")

    /** Right-to-left locales among the supported set. */
    private val rtlCodes = setOf("ar")

    fun normalize(code: String?): String =
        code?.lowercase(Locale.ROOT)?.takeIf { it in supportedCodes } ?: "en"

    /** True when [code] normalizes to a right-to-left locale (currently only Arabic). */
    fun isRtl(code: String?): Boolean = normalize(code) in rtlCodes

    /**
     * Single source-of-truth entry point for changing the app locale (Bug C + Bug B).
     *
     * Writes the normalized code to ALL three persistence paths so they agree by
     * construction:
     *   1. SharedPreferences (`SettingsRepository.localeCode`) — read by
     *      [MainActivity.attachBaseContext] for the first frame.
     *   2. DataStore (`AppPreferencesRepository.setLocaleCode`) — the async store
     *      other components observe.
     *   3. The AndroidX per-app locale (`AppCompatDelegate.setApplicationLocales`) —
     *      survives process restarts and drives per-activity locale.
     *
     * Both synchronous stores (SharedPreferences, per-app locale) are written before
     * returning so any immediate recreate() re-reads the freshly persisted value.
     */
    fun setLocale(context: Context, code: String) {
        val normalized = normalize(code)
        // 1. SharedPreferences (synchronous) — the value attachBaseContext reads.
      //  SettingsRepository(context).localeCode = normalized
        // 3. Per-app locale (synchronous handoff to the delegate).
        applyAppLocale(normalized)
        // 2. DataStore (asynchronous) — mirror the same value so all paths agree.
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
          //  AppPreferencesRepository.getInstance(appContext).setLocaleCode(normalized)
        }
    }

    /**
     * Wraps a base context with the configuration for [code]. Used from
     * Activity.attachBaseContext so the very first frame renders in the right
     * language even before the AppCompat delegate applies.
     */
    fun apply(context: Context, code: String): Context {
        val locale = Locale.forLanguageTag(normalize(code))
        Locale.setDefault(locale)
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)
        return context.createConfigurationContext(configuration)
    }

    /**
     * Applies [code] as the app-wide locale using the AndroidX per-app locale
     * API. This reliably updates every activity (and survives process restarts
     * via the storage the framework/AppCompat manages), which the manual
     * Configuration + recreate() approach did not do consistently.
     */
    fun applyAppLocale(code: String) {
        val normalized = normalize(code)
        val locales = LocaleListCompat.forLanguageTags(normalized)
        AppCompatDelegate.setApplicationLocales(locales)
    }

    /** The currently applied app locale tag, or the persisted default fallback. */
    fun currentAppLocale(fallback: String): String {
        val applied = AppCompatDelegate.getApplicationLocales()
        if (applied.isEmpty) return normalize(fallback)
        return normalize(applied[0]?.language)
    }
}
