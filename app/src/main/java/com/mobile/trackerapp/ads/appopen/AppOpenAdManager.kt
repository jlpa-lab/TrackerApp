package com.mobile.trackerapp.ads.appopen

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import com.mobile.trackerapp.ads.AdFormat
import com.mobile.trackerapp.ads.AdPlacement
import com.mobile.trackerapp.ads.AdRevenueTracker
import com.mobile.trackerapp.ads.AdsConfig
import com.mobile.trackerapp.ads.AdsConfigStore
import com.mobile.trackerapp.ads.AdsLogger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads and shows App Open ads. Enforces a freshness cap (AdMob ≤4h). The
 * min-interval and full-screen lock are enforced centrally by AdsEligibility /
 * AdsFrequencyManager before [show] is called.
 */
@Singleton
class AppOpenAdManager @Inject constructor(
    private val configStore: AdsConfigStore,
) {
    private var appOpenAd: AppOpenAd? = null
    private var isLoading = false
    private var loadedAt = 0L

    fun preload(context: Context) {
        val config = configStore.current
        if (!config.formatEnabled(AdFormat.APP_OPEN)) return
        if (appOpenAd != null && isFresh(config)) return
        if (isLoading) return

        isLoading = true
        val unitId = config.unitIdFor(AdPlacement.APP_OPEN) ?: run {
            AdsLogger.missingUnitId(AdPlacement.APP_OPEN)
            isLoading = false
            return
        }
        AdsLogger.loadStarted(AdPlacement.APP_OPEN, unitId, config.testMode)
        AppOpenAd.load(
            context.applicationContext,
            unitId,
            AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    ad.setOnPaidEventListener { adValue ->
                        AdRevenueTracker.trackPaidAd(context, AdPlacement.APP_OPEN, unitId, adValue)
                    }
                    AdsLogger.loadSucceeded(AdPlacement.APP_OPEN, ad.responseInfo?.mediationAdapterClassName)
                    appOpenAd = ad
                    loadedAt = System.currentTimeMillis()
                    isLoading = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    AdsLogger.loadFailed(AdPlacement.APP_OPEN, error.code, error.message)
                    appOpenAd = null
                    isLoading = false
                }
            }
        )
    }

    private fun isFresh(config: AdsConfig): Boolean =
        System.currentTimeMillis() - loadedAt < config.appOpenMaxCacheMs

    fun isReady(): Boolean = appOpenAd != null && isFresh(configStore.current)

    /**
     * Show the App Open ad. [onShown] fires when displayed; [onComplete] always
     * fires once. Returns false if no fresh ad was ready.
     */
    fun show(
        activity: Activity,
        onShown: () -> Unit,
        onComplete: () -> Unit,
    ): Boolean {
        if (!isReady()) {
            preload(activity)
            return false
        }
        val ad = appOpenAd ?: return false

        var forwarded = false
        val forwardOnce = { if (!forwarded) { forwarded = true; onComplete() } }

        AdsLogger.showAttempt(AdPlacement.APP_OPEN)
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                AdsLogger.showSuccess(AdPlacement.APP_OPEN)
                onShown()
            }

            override fun onAdDismissedFullScreenContent() {
                AdsLogger.dismissed(AdPlacement.APP_OPEN)
                appOpenAd = null
                preload(activity)
                forwardOnce()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                AdsLogger.showFailure(AdPlacement.APP_OPEN, error.message)
                appOpenAd = null
                preload(activity)
                forwardOnce()
            }
        }
        ad.show(activity)
        return true
    }
}
