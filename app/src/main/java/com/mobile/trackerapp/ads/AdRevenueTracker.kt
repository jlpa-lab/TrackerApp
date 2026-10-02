package com.mobile.trackerapp.ads

import android.content.Context
import android.os.Bundle
import com.google.android.gms.ads.AdValue
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * Centralized impression-level revenue tracking for all AdMob formats.
 *
 * AdMob/Firebase may auto-log ad_impression when the app is linked correctly;
 * this explicit paid-event hook keeps revenue visible and debuggable from the
 * app side for every ad object we create.
 */
object AdRevenueTracker {
    private const val AD_SOURCE = "Google AdMob"
    private const val CUSTOM_PLACEMENT = "ad_placement"

    fun trackPaidAd(
        context: Context,
        placement: AdPlacement,
        adUnitId: String?,
        adValue: AdValue,
    ) {
        val revenue = adValue.valueMicros / 1_000_000.0
        val currency = adValue.currencyCode.ifBlank { "USD" }
        trackAdRevenue(
            context = context,
            placement = placement,
            adUnitId = adUnitId,
            revenue = revenue,
            currency = currency,
        )
    }

    fun trackAdRevenue(
        context: Context,
        placement: AdPlacement,
        adUnitId: String?,
        revenue: Double,
        currency: String,
    ) {
        val params = Bundle().apply {
            putString(FirebaseAnalytics.Param.AD_PLATFORM, "AdMob")
            putString(FirebaseAnalytics.Param.AD_SOURCE, AD_SOURCE)
            putString(FirebaseAnalytics.Param.AD_FORMAT, placement.format.analyticsName)
            putString(FirebaseAnalytics.Param.AD_UNIT_NAME, adUnitId.orEmpty())
            putString(FirebaseAnalytics.Param.CURRENCY, currency)
            putDouble(FirebaseAnalytics.Param.VALUE, revenue)
            putString(CUSTOM_PLACEMENT, placement.key)
        }
        FirebaseAnalytics.getInstance(context.applicationContext)
            .logEvent(FirebaseAnalytics.Event.AD_IMPRESSION, params)
        AdsLogger.adImpression(placement, revenue, currency, adUnitId)
    }
}

private val AdFormat.analyticsName: String
    get() = when (this) {
        AdFormat.BANNER -> "BANNER"
        AdFormat.NATIVE -> "NATIVE"
        AdFormat.INTERSTITIAL -> "INTERSTITIAL"
        AdFormat.APP_OPEN -> "APP_OPEN"
        AdFormat.REWARDED -> "REWARDED"
    }
