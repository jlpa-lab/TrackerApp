package com.mobile.trackerapp.ads

import android.content.Context
import java.util.Calendar
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralized frequency, cooldown and full-screen-lock logic. All screens share
 * this single instance so counters are never duplicated or inconsistent.
 *
 * In-memory state (session count, cooldown timestamps, the
 * full-screen lock) resets per process. Persistent state (reward daily count,
 * post-splash-seen, first-launch) uses SharedPreferences, matching the app's
 * existing lightweight persistence pattern.
 */
@Singleton
class AdsFrequencyManager @Inject constructor() {

    // ---- In-memory session state ----
    private var interstitialsShownThisSession = 0
    private var lastFullScreenShownAt: Long? = null
    private val lastPlacementShownAt = mutableMapOf<String, Long>()
    private val placementTriggerCounts = mutableMapOf<String, Int>()
    private val fullScreenShowing = AtomicBoolean(false)

    // ---- Full-screen lock ----

    /** Try to acquire the single full-screen slot. Returns false if one is up. */
    fun tryAcquireFullScreen(): Boolean = fullScreenShowing.compareAndSet(false, true)

    fun releaseFullScreen() {
        fullScreenShowing.set(false)
    }

    fun isFullScreenShowing(): Boolean = fullScreenShowing.get()

    // ---- Cooldowns ----

    fun placementCooldownRemaining(placement: AdPlacement, cooldownMs: Long, nowMs: Long = System.currentTimeMillis()): Long {
        val last = lastPlacementShownAt[placement.key] ?: return 0L
        return (last + cooldownMs - nowMs).coerceAtLeast(0L)
    }

    fun globalFullScreenCooldownRemaining(cooldownMs: Long, nowMs: Long = System.currentTimeMillis()): Long {
        val last = lastFullScreenShownAt ?: return 0L
        return (last + cooldownMs - nowMs).coerceAtLeast(0L)
    }

    fun sessionLimitReached(config: AdsConfig): Boolean =
        interstitialsShownThisSession >= config.maxInterstitialsPerSession

    fun recordPlacementTrigger(placement: AdPlacement): Int {
        val count = (placementTriggerCounts[placement.key] ?: 0) + 1
        placementTriggerCounts[placement.key] = count
        return count
    }

    fun triggerReached(placement: AdPlacement, interval: Int): Boolean =
        (placementTriggerCounts[placement.key] ?: 0) >= interval

    fun resetPlacementTrigger(placement: AdPlacement) {
        placementTriggerCounts.remove(placement.key)
    }

    /** Record a full-screen impression: updates cooldowns, session count, trigger reset. */
    fun recordFullScreenShown(placement: AdPlacement, nowMs: Long = System.currentTimeMillis()) {
        val now = nowMs
        lastFullScreenShownAt = now
        lastPlacementShownAt[placement.key] = now
        if (placement == AdPlacement.INTER_BACK || placement == AdPlacement.INTER_GENERATE) {
            resetPlacementTrigger(placement)
        }
        if (placement.format == AdFormat.INTERSTITIAL) {
            interstitialsShownThisSession++
        }
    }

    // ---- Reward daily limit (persistent) ----

    fun rewardClaimsToday(context: Context): Int {
        val prefs = prefs(context)
        return if (prefs.getInt(KEY_REWARD_DAY, -1) == dayOfYear()) {
            prefs.getInt(KEY_REWARD_COUNT, 0)
        } else 0
    }

    fun canClaimReward(context: Context, config: AdsConfig): Boolean =
        rewardClaimsToday(context) < config.rewardMaxClaimsPerDay

    fun recordRewardClaim(context: Context) {
        val prefs = prefs(context)
        val today = dayOfYear()
        val count = if (prefs.getInt(KEY_REWARD_DAY, -1) == today) prefs.getInt(KEY_REWARD_COUNT, 0) else 0
        prefs.edit()
            .putInt(KEY_REWARD_DAY, today)
            .putInt(KEY_REWARD_COUNT, count + 1)
            .apply()
    }

    // ---- Post-splash / first-launch (persistent) ----

    /** inter_post_splash must never show on the absolute first launch. */
    fun isFirstLaunch(context: Context): Boolean =
        !prefs(context).getBoolean(KEY_LAUNCHED_BEFORE, false)

    fun markLaunched(context: Context) {
        prefs(context).edit().putBoolean(KEY_LAUNCHED_BEFORE, true).apply()
    }

    private fun dayOfYear(): Int {
        val cal = Calendar.getInstance()
        return cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR)
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private companion object {
        const val PREFS = "lumora_ads_frequency"
        const val KEY_REWARD_DAY = "reward_day"
        const val KEY_REWARD_COUNT = "reward_count"
        const val KEY_LAUNCHED_BEFORE = "launched_before"
    }
}
