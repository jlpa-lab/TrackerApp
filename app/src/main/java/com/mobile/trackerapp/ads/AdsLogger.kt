package com.mobile.trackerapp.ads

import android.util.Log

/** Structured reasons an ad request/show can be rejected before reaching AdMob. */
enum class AdRejectionReason {
    ADS_GLOBALLY_DISABLED,
    FORMAT_DISABLED,
    PLACEMENT_DISABLED,
    POLICY_BLOCKED,
    COOLDOWN,
    SESSION_LIMIT,
    REWARD_DAILY_LIMIT,
    FULLSCREEN_ALREADY_SHOWING,
    NO_ACTIVITY,
    AD_NOT_READY,
    NO_NETWORK,
}

/**
 * Single logging entry point for the whole ads layer. Never logs user PII or
 * secrets — only placement/format/config/diagnostic data.
 */
object AdsLogger {
    private const val TAG = "LumoraAds"

    /** Disable to silence ad logs (e.g. in release). */
    var enabled: Boolean = true

    fun d(message: String) {
        if (enabled) Log.d(TAG, message)
    }

    fun w(message: String, error: Throwable? = null) {
        if (enabled) Log.w(TAG, message, error)
    }

    fun e(message: String, error: Throwable? = null) {
        if (enabled) Log.e(TAG, message, error)
    }

    fun loadStarted(placement: AdPlacement, unitId: String, testMode: Boolean) {
        d("LOAD start | ${placement.key} | ${placement.format} | test=$testMode | unit=$unitId")
    }

    fun loadSucceeded(placement: AdPlacement, adapter: String? = null) {
        d("LOAD ok    | ${placement.key} | ${placement.format}${adapter?.let { " | adapter=$it" } ?: ""}")
    }

    fun loadFailed(placement: AdPlacement, code: Int?, message: String?) {
        w("LOAD fail  | ${placement.key} | ${placement.format} | code=$code | $message")
    }

    fun missingUnitId(placement: AdPlacement) {
        w("LOAD skip  | ${placement.key} | ${placement.format} | missing Remote Config ad unit id")
    }

    fun showAttempt(placement: AdPlacement) = d("SHOW try   | ${placement.key} | ${placement.format}")
    fun showSuccess(placement: AdPlacement) = d("SHOW ok    | ${placement.key} | ${placement.format}")
    fun showFailure(placement: AdPlacement, message: String?) =
        w("SHOW fail  | ${placement.key} | ${placement.format} | $message")

    fun dismissed(placement: AdPlacement) = d("DISMISS    | ${placement.key} | ${placement.format}")
    fun reward(placement: AdPlacement, amount: Int, type: String) =
        d("REWARD     | ${placement.key} | $amount $type")

    fun adImpression(placement: AdPlacement, revenue: Double, currency: String, unitId: String?) =
        d("IMPRESSION | ${placement.key} | ${placement.format} | value=$revenue $currency${unitId?.let { " | unit=$it" } ?: ""}")

    fun rejected(placement: AdPlacement, reason: AdRejectionReason, detail: String? = null) {
        d("REJECT     | ${placement.key} | ${placement.format} | $reason${detail?.let { " | $it" } ?: ""}")
    }
}
