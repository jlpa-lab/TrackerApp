package com.mobile.trackerapp.ui.rating

import android.content.Context
import com.mobile.trackerapp.pref.AppSharedPreferencesApp

object RateAppManager {
    private var shownThisSession = false
    private var connectionAdded = false
    private var gpsToolUsed = false
    private var nearbyUsed = false
    private var coreFeatureUsed = false
    private var zoneCreated = false

    fun markConnectionAdded() { connectionAdded = true }
    fun markGpsToolUsed() { gpsToolUsed = true }
    fun markNearbyUsed() { nearbyUsed = true }
    fun markCoreFeatureUsed() { coreFeatureUsed = true }
    fun markZoneCreated() { zoneCreated = true }

    fun shouldShowRateDialog(context: Context): Boolean {
        return !AppSharedPreferencesApp(context).isRate && !shownThisSession &&
            (connectionAdded || gpsToolUsed || nearbyUsed || coreFeatureUsed || zoneCreated)
    }

    fun onRateDialogShown() {
        shownThisSession = true
        clearTriggers()
    }

    fun onUserRated(context: Context) {
        AppSharedPreferencesApp(context).isRate = true
        shownThisSession = true
        clearTriggers()
    }

    fun onRateDialogDismissed() {
        shownThisSession = true
        clearTriggers()
    }

    private fun clearTriggers() {
        connectionAdded = false
        gpsToolUsed = false
        nearbyUsed = false
        coreFeatureUsed = false
        zoneCreated = false
    }
}
