package com.mobile.trackerapp.ads.banner

import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout

import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.mobile.trackerapp.ads.AdFormat
import com.mobile.trackerapp.ads.AdPlacement
import com.mobile.trackerapp.ads.AdRevenueTracker
import com.mobile.trackerapp.ads.AdsConfigStore
import com.mobile.trackerapp.ads.AdsLogger

/**
 * Owns a SINGLE, long-lived banner [AdView] for the persistent bottom banner
 * (banner_all) shown across the primary tabs.
 *
 * The AdView is created and loaded exactly once and then re-parented between
 * host containers as the user navigates — it is never recreated, re-rendered,
 * or force-refreshed on tab switches. Wrapped in a stable [FrameLayout] host so
 * it can be detached from one screen and attached to another without reload.
 */
class PersistentBannerAd(
    // Must be an Activity context: the Mobile Ads SDK needs it to render banners.
    private val activityContext: Context,
    private val configStore: AdsConfigStore,
    private val widthDp: Int,
) {
    /** null=loading, true=loaded, false=failed(collapse). */
    var loadState: Boolean? = null
        private set

    private var onStateChanged: ((Boolean?) -> Unit)? = null

    val adSize: AdSize = resolveAdSize(activityContext, widthDp)

    /** Stable host view that always contains the single AdView. */
    val host: FrameLayout = FrameLayout(activityContext).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }

    private var adView: AdView? = null

    fun heightPx(): Int = adSize.getHeightInPixels(activityContext)

    /** Create + load the AdView once. Safe to call repeatedly (no-op if built). */
    fun ensureLoaded() {
        val config = configStore.current
        if (!config.formatEnabled(AdFormat.BANNER) || !config.isPlacementEnabled(AdPlacement.BANNER_ALL)) {
            updateState(false)
            return
        }
        if (adView != null) return

        val unitId = config.unitIdFor(AdPlacement.BANNER_ALL) ?: run {
            AdsLogger.missingUnitId(AdPlacement.BANNER_ALL)
            updateState(false)
            return
        }
        val resolvedSize = this.adSize
        val view = AdView(activityContext).apply {
            setAdSize(resolvedSize)
            adUnitId = unitId
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    AdsLogger.showSuccess(AdPlacement.BANNER_ALL)
                    updateState(true)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    AdsLogger.loadFailed(AdPlacement.BANNER_ALL, error.code, error.message)
                    updateState(false)
                }
            }
            setOnPaidEventListener { adValue ->
                AdRevenueTracker.trackPaidAd(activityContext, AdPlacement.BANNER_ALL, adUnitId, adValue)
            }
        }
        adView = view
        host.addView(
            view,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ).apply { gravity = Gravity.CENTER },
        )
        AdsLogger.loadStarted(AdPlacement.BANNER_ALL, view.adUnitId, config.testMode)
        view.loadAd(AdRequest.Builder().build())
    }

    /** Observe load-state changes (loading/loaded/failed) for the UI. */
    fun observe(listener: (Boolean?) -> Unit) {
        onStateChanged = listener
        listener(loadState)
    }

    fun clearObserver() {
        onStateChanged = null
    }

    private fun updateState(state: Boolean?) {
        loadState = state
        onStateChanged?.invoke(state)
    }

    /** Destroy the single AdView (call when the whole app/session tears down). */
    fun destroy() {
        adView?.destroy()
        adView = null
        host.removeAllViews()
        onStateChanged = null
    }

    private companion object {
        fun resolveAdSize(context: Context, widthDp: Int): AdSize {
            if (widthDp <= 0) return AdSize.BANNER
            // Anchored adaptive banner: the standard, reliable bottom banner size.
            val adaptive: AdSize? =
                AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp)
            return adaptive ?: AdSize.BANNER
        }
    }
}
