package com.mobile.trackerapp.ads.interstitial

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.mobile.trackerapp.ads.AdFormat
import com.mobile.trackerapp.ads.AdPlacement
import com.mobile.trackerapp.ads.AdRevenueTracker
import com.mobile.trackerapp.ads.AdsConfigStore
import com.mobile.trackerapp.ads.AdsLogger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads and shows interstitial ads with preloading and automatic reload.
 * Eligibility/cooldown/frequency decisions live in AdsEligibility/Frequency —
 * this manager only owns loading, the SDK show call, and reload. Screens never
 * touch it directly (they go through AdsManager).
 */
@Singleton
class InterstitialAdManager @Inject constructor(
    private val configStore: AdsConfigStore,
) {
    private val interstitials = mutableMapOf<AdPlacement, InterstitialAd>()
    private val loadedUnitIds = mutableMapOf<AdPlacement, String>()
    private val loading = mutableSetOf<AdPlacement>()

    fun preload(context: Context, placement: AdPlacement = AdPlacement.INTER_ALL) {
        val config = configStore.current
        if (!config.formatEnabled(AdFormat.INTERSTITIAL)) return
        val unitId = config.unitIdFor(placement) ?: run {
            AdsLogger.missingUnitId(placement)
            return
        }
        if (interstitials[placement] != null && loadedUnitIds[placement] == unitId) return
        if (!loading.add(placement)) return

        interstitials.remove(placement)
        loadedUnitIds.remove(placement)
        AdsLogger.loadStarted(placement, unitId, config.testMode)
        InterstitialAd.load(
            context.applicationContext,
            unitId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    ad.setOnPaidEventListener { adValue ->
                        AdRevenueTracker.trackPaidAd(context, placement, unitId, adValue)
                    }
                    AdsLogger.loadSucceeded(placement, ad.responseInfo.mediationAdapterClassName)
                    loading.remove(placement)
                    if (configStore.current.unitIdFor(placement) == unitId) {
                        interstitials[placement] = ad
                        loadedUnitIds[placement] = unitId
                    } else preload(context, placement)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    AdsLogger.loadFailed(placement, error.code, error.message)
                    interstitials.remove(placement)
                    loadedUnitIds.remove(placement)
                    loading.remove(placement)
                }
            }
        )
    }

    fun isReady(placement: AdPlacement): Boolean =
        interstitials[placement] != null && loadedUnitIds[placement] == configStore.current.unitIdFor(placement)

    /**
     * Show the (already eligibility-checked) interstitial. [onShown] fires when
     * the ad actually displayed; [onComplete] always fires exactly once so the
     * user flow proceeds. Returns false if there was no ad to show.
     */
    fun show(
        activity: Activity,
        placement: AdPlacement,
        onShown: () -> Unit,
        onAdDisplayed: () -> Unit = {},
        onComplete: () -> Unit,
    ): Boolean {
        val ad = interstitials[placement]?.takeIf { isReady(placement) } ?: run {
            preload(activity, placement)
            return false
        }

        var forwarded = false
        val forwardOnce = {
            if (!forwarded) { forwarded = true; onComplete() }
        }

        AdsLogger.showAttempt(placement)
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                AdsLogger.showSuccess(placement)
                onShown()
                onAdDisplayed()
            }

            override fun onAdDismissedFullScreenContent() {
                AdsLogger.dismissed(placement)
                interstitials.remove(placement)
                loadedUnitIds.remove(placement)
                preload(activity, placement)
                forwardOnce()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                AdsLogger.showFailure(placement, error.message)
                interstitials.remove(placement)
                loadedUnitIds.remove(placement)
                preload(activity, placement)
                forwardOnce()
            }
        }
        ad.show(activity)
        return true
    }
}
