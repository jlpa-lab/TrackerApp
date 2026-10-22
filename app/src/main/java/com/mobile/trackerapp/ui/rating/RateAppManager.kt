package com.location.and.phone.tracker.locationnphonetracker.ui.rating

import android.content.Context
import com.location.and.phone.tracker.locationnphonetracker.pref.AppSharedPreferencesApp

/** Coordinates the one-per-session rate prompt and its automatic entry triggers. */
object RateAppManager {
    enum class EntryPoint { AUTOMATIC, SETTINGS }

    private var shownThisSession = false
    private var automaticTriggerPending = false

    fun markConnectionAdded() {
        automaticTriggerPending = true
    }

    fun markGpsToolUsed() {
        automaticTriggerPending = true
    }

    fun markNearbyUsed() {
        automaticTriggerPending = true
    }

    fun shouldShowAutomaticRateDialog(context: Context): Boolean =
        !AppSharedPreferencesApp(context).isRate && !shownThisSession && automaticTriggerPending

    fun canOpenSettingsRateDialog(context: Context): Boolean =
        !AppSharedPreferencesApp(context).isRate && !shownThisSession

    fun hasShownThisSession(): Boolean = shownThisSession

    /** Atomically claims the dialog so duplicate activity resumes cannot show it twice. */
    fun beginRateDialog(context: Context, entryPoint: EntryPoint): Boolean {
        if (AppSharedPreferencesApp(context).isRate || shownThisSession) return false
        if (entryPoint == EntryPoint.AUTOMATIC && !automaticTriggerPending) return false

        shownThisSession = true
        automaticTriggerPending = false
        return true
    }

    fun onUserRated(context: Context) {
        AppSharedPreferencesApp(context).isRate = true
        shownThisSession = true
        automaticTriggerPending = false
    }

    fun onRateDialogDismissed() {
        // Dismissal blocks the prompt for this session, but is not persisted as Rated.
        shownThisSession = true
        automaticTriggerPending = false
    }

    /** Consume only the trigger that was followed by an interstitial. */
    fun skipAutomaticTriggerForInterstitial() {
        automaticTriggerPending = false
    }
}
