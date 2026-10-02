package com.mobile.trackerapp.ads

import android.app.Activity
import android.content.Context
import com.deep.lumoraai.ads.nativead.NativeAdManager
import com.mobile.trackerapp.ads.appopen.AppOpenAdManager
import com.mobile.trackerapp.ads.interstitial.InterstitialAdManager
import com.mobile.trackerapp.ads.rewarded.AdReward
import com.mobile.trackerapp.ads.rewarded.RewardedAdManager
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single façade the app uses for all advertising. Screens call by
 * [AdPlacement] and never touch the SDK. This coordinates the decision pipeline
 * ([AdsEligibility]), frequency/cooldown/lock ([AdsFrequencyManager]) and the
 * per-format managers. Mediation, consent and Remote Config plug in here later
 * without changing any call site.
 */
@Singleton
class AdsManager @Inject constructor(
    private val configStore: AdsConfigStore,
    private val eligibility: AdsEligibility,
    private val frequency: AdsFrequencyManager,
    private val interstitialManager: InterstitialAdManager,
    private val rewardedManager: RewardedAdManager,
    private val appOpenManager: AppOpenAdManager,
    val nativeManager: NativeAdManager,
) {
    private val initialized = AtomicBoolean(false)
    private val homeStartup = HomeStartupAdState()

    fun prepareHomeStartup(context: Context) = homeStartup.prepare(frequency.isFirstLaunch(context))
    fun prepareAndConsumeHomeStartup(context: Context): Boolean {
        prepareHomeStartup(context)
        return consumeHomeStartup()
    }
    fun consumeHomeStartup(): Boolean = homeStartup.consume()

    val config: AdsConfig get() = configStore.current

    /** Initialize the Mobile Ads SDK once and warm up preloadable formats. */
    fun initialize(context: Context) {
        if (!config.adsEnabled) return
        if (!initialized.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        initializeMobileAds(appContext)
    }

    private fun initializeMobileAds(appContext: Context) {
        val config = configStore.current
        if (!config.adsEnabled) {
            AdsLogger.d("MobileAds skipped: ads disabled by Remote Config")
            return
        }
        if (config.testMode) {
            // Diagnostics/policy mode only. Ad unit IDs still come from Remote Config.
            MobileAds.setRequestConfiguration(
                RequestConfiguration.Builder()
                    .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_G)
                    .build()
            )
        }

        MobileAds.initialize(appContext) { status ->
            AdsLogger.d("MobileAds initialized: ${status.adapterStatusMap.keys}")
            interstitialManager.preload(appContext, AdPlacement.INTER_BACK)
            interstitialManager.preload(appContext, AdPlacement.INTER_GENERATE)
            rewardedManager.preload(appContext)
            appOpenManager.preload(appContext)
        }
    }

    fun isInitialized(): Boolean = initialized.get()

    fun markLaunched(context: Context) = frequency.markLaunched(context)
    fun isFirstLaunch(context: Context): Boolean = frequency.isFirstLaunch(context)

    // ---- Interstitial ----

    fun preloadInterstitial(context: Context, placement: AdPlacement = AdPlacement.INTER_ALL) =
        interstitialManager.preload(context, placement)

    /** Counts user actions independently for each click-based interstitial. */
    fun shouldShowClickBasedInterstitial(placement: AdPlacement): Boolean {
        val interval = when (placement) {
            AdPlacement.INTER_BACK -> config.interBackClickInterval
            AdPlacement.INTER_GENERATE -> config.interGenerateClickInterval
            else -> return false
        }
        frequency.recordPlacementTrigger(placement)
        return frequency.triggerReached(placement, interval)
    }

    /**
     * Show an interstitial for [placement] then run [onContinue]. Runs the full
     * decision pipeline; if any check fails the ad is skipped and [onContinue]
     * runs immediately. [onContinue] always runs exactly once.
     */
    fun showInterstitial(
        activity: Activity?,
        placement: AdPlacement,
        continueOnShown: Boolean = false,
        onContinue: () -> Unit,
    ) {
        if (activity == null) {
            AdsLogger.rejected(placement, AdRejectionReason.NO_ACTIVITY)
            onContinue(); return
        }
        val verdict = eligibility.evaluate(
            context = activity,
            placement = placement,
            adReady = { interstitialManager.isReady(placement) },
        )
        if (verdict !is AdEligibility.Allowed) {
            onContinue(); return
        }
        if (!frequency.tryAcquireFullScreen()) {
            AdsLogger.rejected(placement, AdRejectionReason.FULLSCREEN_ALREADY_SHOWING)
            onContinue(); return
        }

        var continued = false
        var released = false
        val continueOnce = {
            if (!continued) {
                continued = true
                onContinue()
            }
        }
        val releaseOnce = {
            if (!released) {
                released = true
                frequency.releaseFullScreen()
            }
        }
        val complete = {
            releaseOnce()
            continueOnce()
        }
        val shown = interstitialManager.show(
            activity = activity,
            placement = placement,
            onShown = { frequency.recordFullScreenShown(placement) },
            onAdDisplayed = { if (continueOnShown) continueOnce() },
            onComplete = complete,
        )
        if (!shown) complete()
    }

    fun isInterstitialReady(placement: AdPlacement = AdPlacement.INTER_ALL): Boolean =
        interstitialManager.isReady(placement)

    // ---- Rewarded ----

    fun preloadRewarded(context: Context) = rewardedManager.preload(context)

    /**
     * Show a rewarded ad. [onReward] fires only on the genuine SDK earned-reward
     * callback and only if the daily claim limit allows; [onClosed] always fires.
     */
    fun showRewarded(
        activity: Activity?,
        placement: AdPlacement = AdPlacement.REWARD_CREDITS,
        onReward: (AdReward) -> Unit,
        onClosed: () -> Unit = {},
    ) {
        if (activity == null) {
            AdsLogger.rejected(placement, AdRejectionReason.NO_ACTIVITY)
            onClosed(); return
        }
        val verdict = eligibility.evaluate(
            context = activity,
            placement = placement,
            adReady = { rewardedManager.isReady() },
        )
        if (verdict !is AdEligibility.Allowed) {
            onClosed(); return
        }
        if (!frequency.tryAcquireFullScreen()) {
            AdsLogger.rejected(placement, AdRejectionReason.FULLSCREEN_ALREADY_SHOWING)
            onClosed(); return
        }

        var closed = false
        val close = {
            if (!closed) {
                closed = true
                frequency.releaseFullScreen()
                onClosed()
            }
        }
        val shown = rewardedManager.show(
            activity = activity,
            placement = placement,
            onShown = { frequency.recordFullScreenShown(placement) },
            onReward = { reward ->
                // Grant ONLY on the genuine SDK callback, and record the claim
                // for the daily limit.
                frequency.recordRewardClaim(activity)
                onReward(reward)
            },
            onClosed = close,
        )
        if (!shown) close()
    }

    fun isRewardedReady(): Boolean = rewardedManager.isReady()

    fun rewardClaimsToday(context: Context): Int = frequency.rewardClaimsToday(context)

    // ---- App Open ----

    fun preloadAppOpen(context: Context) = appOpenManager.preload(context)

    /**
     * Show the App Open ad for [placement]. [onComplete] always fires once so
     * cold-start/resume flows never hang.
     */
    fun showAppOpen(
        activity: Activity?,
        placement: AdPlacement = AdPlacement.APP_OPEN,
        onComplete: () -> Unit = {},
    ) {
        if (activity == null) {
            AdsLogger.rejected(placement, AdRejectionReason.NO_ACTIVITY)
            onComplete(); return
        }
        val verdict = eligibility.evaluate(
            context = activity,
            placement = placement,
            adReady = { appOpenManager.isReady() },
        )
        if (verdict !is AdEligibility.Allowed) {
            onComplete(); return
        }
        if (!frequency.tryAcquireFullScreen()) {
            AdsLogger.rejected(placement, AdRejectionReason.FULLSCREEN_ALREADY_SHOWING)
            onComplete(); return
        }

        var completed = false
        val complete = {
            if (!completed) {
                completed = true
                frequency.releaseFullScreen()
                onComplete()
            }
        }
        val shown = appOpenManager.show(
            activity = activity,
            onShown = { frequency.recordFullScreenShown(placement) },
            onComplete = complete,
        )
        if (!shown) complete()
    }

    fun isAppOpenReady(): Boolean = appOpenManager.isReady()

    // ---- Native ----

    fun clearNativeAds() = nativeManager.destroyAll()

    private companion object {
        // Legacy single-blob key kept for reference only — no longer used.
        // const val ADS_CONFIG_JSON_KEY = "ads_config_json"
    }
}
