package com.mobile.trackerapp.ads.rewarded

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.mobile.trackerapp.ads.AdFormat
import com.mobile.trackerapp.ads.AdPlacement
import com.mobile.trackerapp.ads.AdRevenueTracker
import com.mobile.trackerapp.ads.AdsConfigStore
import com.mobile.trackerapp.ads.AdsLogger
import javax.inject.Inject
import javax.inject.Singleton

/** The reward earned from a rewarded ad's genuine SDK callback. */
data class AdReward(val type: String, val amount: Int)

/**
 * Loads and shows rewarded ads. The reward callback fires ONLY on the SDK's
 * genuine earned-reward event; a separate close callback always fires so the
 * caller can continue.
 */
@Singleton
class RewardedAdManager @Inject constructor(
    private val configStore: AdsConfigStore,
) {
    private var rewarded: RewardedAd? = null
    private var isLoading = false

    fun preload(context: Context, placement: AdPlacement = AdPlacement.REWARD_CREDITS) {
        val config = configStore.current
        if (!config.formatEnabled(AdFormat.REWARDED)) return
        if (rewarded != null || isLoading) return

        isLoading = true
        val unitId = config.unitIdFor(placement) ?: run {
            AdsLogger.missingUnitId(placement)
            isLoading = false
            return
        }
        AdsLogger.loadStarted(placement, unitId, config.testMode)
        RewardedAd.load(
            context.applicationContext,
            unitId,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    ad.setOnPaidEventListener { adValue ->
                        AdRevenueTracker.trackPaidAd(context, placement, unitId, adValue)
                    }
                    AdsLogger.loadSucceeded(placement, ad.responseInfo?.mediationAdapterClassName)
                    rewarded = ad
                    isLoading = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    AdsLogger.loadFailed(placement, error.code, error.message)
                    rewarded = null
                    isLoading = false
                }
            }
        )
    }

    fun isReady(): Boolean = rewarded != null

    /**
     * Show a rewarded ad.
     * @param onShown fires when the ad displays.
     * @param onReward fires ONLY when the user earned the reward (SDK callback).
     * @param onClosed always fires once when the flow ends.
     * @return false if no ad was ready.
     */
    fun show(
        activity: Activity,
        placement: AdPlacement,
        onShown: () -> Unit,
        onReward: (AdReward) -> Unit,
        onClosed: () -> Unit,
    ): Boolean {
        val ad = rewarded ?: run {
            preload(activity, placement)
            return false
        }

        var closed = false
        val closeOnce = {
            if (!closed) { closed = true; onClosed() }
        }

        AdsLogger.showAttempt(placement)
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                AdsLogger.showSuccess(placement)
                onShown()
            }

            override fun onAdDismissedFullScreenContent() {
                AdsLogger.dismissed(placement)
                rewarded = null
                preload(activity, placement)
                closeOnce()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                AdsLogger.showFailure(placement, error.message)
                rewarded = null
                preload(activity, placement)
                closeOnce()
            }
        }

        ad.show(activity) { rewardItem ->
            AdsLogger.reward(placement, rewardItem.amount, rewardItem.type)
            onReward(AdReward(type = rewardItem.type, amount = rewardItem.amount))
        }
        return true
    }
}
