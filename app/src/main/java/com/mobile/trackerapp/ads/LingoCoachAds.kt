package com.mobile.trackerapp.ads

import android.animation.ObjectAnimator
import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.os.SystemClock
import android.util.Log
import android.view.animation.LinearInterpolator
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mobile.trackerapp.R
import com.mobile.trackerapp.config.AppConfig
import java.time.LocalDate

object LingoCoachAds {
    private const val TAG = "LingoCoachAds"
    private const val PREFS = "LingoCoachAdPrefs"
    private const val KEY_LAUNCH_COUNT = "launch_count"
    private const val KEY_HOME_REACHED = "home_reached"
    private const val KEY_REWARD_DATE = "reward_date"
    private const val KEY_REWARD_COUNT = "reward_count"

    private var initialized = false
    private var appContext: Context? = null
    private var isLoadingInterstitial = false
    private var interstitialAd: InterstitialAd? = null
    private var interstitialAdUnitId: String = ""
    private var isLoadingRewarded = false
    private var rewardedAd: RewardedAd? = null
    private var isLoadingAppOpen = false
    private var appOpenAd: AppOpenAd? = null
    private var isFullScreenShowing = false
    private var sessionStartedAt = SystemClock.elapsedRealtime()
    private var lastShownAt = 0L
    private var shownThisSession = 0
    private var fullScreenActionCount = 0
    private var pendingInterstitialShow: PendingInterstitialShow? = null
    private val firstLaunchInterstitialPlacements = setOf(
        "inter_assessment"
    )
    private val immediateInterstitialPlacements = setOf(
        "inter_splash",
        "inter_language_complete",
        "inter_time_duel",
        "inter_vocab",
        "inter_assessment",
        "inter_learning_path"
    )
    private val placementAttempts = mutableMapOf<String, Int>()
    private val placementLastShownAt = mutableMapOf<String, Long>()
    private val bannerViewCache = mutableMapOf<String, View>()
    private val nativeSlotViewCache = mutableMapOf<String, FrameLayout>()
    private val nativeAdCache = mutableMapOf<String, NativeAd>()
    private val nativeLoadsInFlight = mutableSetOf<String>()
    private val nativeWaitingContainers = mutableMapOf<String, FrameLayout>()

    private data class PendingInterstitialShow(
        val activity: Activity,
        val placement: String,
        val onComplete: () -> Unit
    )

    fun initialize(context: Context) {
        if (initialized || !AppConfig.adsEnabled) return
        initialized = true
        appContext = context.applicationContext
        recordLaunch(context.applicationContext)
        MobileAds.initialize(context.applicationContext) {
            Log.d(TAG, "Mobile Ads initialized")
        }
    }

    fun recordHomeReached(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_HOME_REACHED, true)
            .apply()
    }

    fun createBannerView(context: Context, placement: String): View? {
        if (!canShowBanner(placement)) {
            bannerViewCache.remove(placement)
            return null
        }
        val adUnitId = AppConfig.placementAdUnitId(placement)
        if (adUnitId.isBlank()) {
            bannerViewCache.remove(placement)
            return null
        }

        bannerViewCache[placement]?.let { cached ->
            detachFromParent(cached)
            return cached
        }

        val root = LayoutInflater.from(context).inflate(R.layout.ad_banner_container, null, false)
        val container = root.findViewById<FrameLayout>(R.id.ad_banner_container)
        val shimmer = createBannerShimmer(context)
        val shimmerAnimator = ObjectAnimator.ofFloat(shimmer, View.ALPHA, 0.42f, 1f, 0.42f).apply {
            duration = 950L
            repeatCount = ObjectAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
        container.addView(
            shimmer,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        val adView = AdView(context).apply {
            setAdSize(edgeToEdgeBannerSize(context))
            this.adUnitId = adUnitId
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    shimmerAnimator.cancel()
                    shimmer.visibility = View.GONE
                    markPlacementShown(placement)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    shimmerAnimator.cancel()
                    bannerViewCache.remove(placement)
                    root.visibility = View.GONE
                    Log.w(TAG, "Banner failed placement=$placement error=${error.message}")
                }
            }
        }
        container.addView(
            adView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        adView.loadAd(AdRequest.Builder().build())
        bannerViewCache[placement] = root
        return root
    }

    fun createNativeSlotView(context: Context, placement: String, slotKey: String = placement): FrameLayout {
        val cacheKey = "$placement:$slotKey"
        if (!canShowNative(placement)) {
            nativeSlotViewCache.remove(cacheKey)
            nativeWaitingContainers.remove(cacheKey)
            return FrameLayout(context).apply { visibility = View.GONE }
        }

        nativeSlotViewCache[cacheKey]?.let { cached ->
            detachFromParent(cached)
            return cached
        }

        return FrameLayout(context).apply {
            visibility = View.GONE
            nativeSlotViewCache[cacheKey] = this
            loadNativeInto(this, placement, slotKey)
        }
    }

    private fun detachFromParent(view: View) {
        (view.parent as? ViewGroup)?.removeView(view)
    }

    private fun createBannerShimmer(context: Context): View {
        val density = context.resources.displayMetrics.density
        return View(context).apply {
            minimumHeight = (50 * density).toInt()
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(
                    Color.argb(38, 255, 255, 255),
                    Color.argb(92, 255, 255, 255),
                    Color.argb(38, 255, 255, 255)
                )
            )
        }
    }

    private fun edgeToEdgeBannerSize(context: Context): AdSize {
        val displayMetrics = context.resources.displayMetrics
        val widthPixels = if (context is Activity && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.windowManager.currentWindowMetrics.bounds.width()
        } else {
            displayMetrics.widthPixels
        }
        val adWidth = (widthPixels / displayMetrics.density).toInt().coerceAtLeast(320)
        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, adWidth)
    }

    fun loadNativeInto(container: FrameLayout, placement: String, slotKey: String = placement) {
        val cacheKey = "$placement:$slotKey"
        nativeAdCache[cacheKey]?.let { nativeAd ->
            renderNativeAdInto(container, placement, nativeAd)
            return
        }

        if (nativeLoadsInFlight.contains(cacheKey)) {
            nativeWaitingContainers[cacheKey] = container
            showNativeLoading(container, placement)
            return
        }

        if (!canShowNative(placement)) {
            container.visibility = View.GONE
            return
        }

        val context = container.context
        val adUnitId = AppConfig.placementAdUnitId(placement)
        if (adUnitId.isBlank()) {
            container.visibility = View.GONE
            return
        }

        nativeLoadsInFlight.add(cacheKey)
        nativeWaitingContainers[cacheKey] = container
        showNativeLoading(container, placement)
        AdLoader.Builder(context, adUnitId)
            .forNativeAd { nativeAd ->
                nativeLoadsInFlight.remove(cacheKey)
                nativeAdCache[cacheKey]?.destroy()
                nativeAdCache[cacheKey] = nativeAd
                nativeWaitingContainers.remove(cacheKey)?.let { waitingContainer ->
                    renderNativeAdInto(waitingContainer, placement, nativeAd)
                }
                markPlacementShown(placement)
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    nativeLoadsInFlight.remove(cacheKey)
                    nativeWaitingContainers.remove(cacheKey)?.let { waitingContainer ->
                        cancelNativeLoading(waitingContainer)
                        waitingContainer.visibility = View.GONE
                    }
                    Log.w(TAG, "Native failed placement=$placement error=${error.message}")
                }
            })
            .withNativeAdOptions(NativeAdOptions.Builder().build())
            .build()
            .loadAd(AdRequest.Builder().build())
    }

    private fun showNativeLoading(container: FrameLayout, placement: String) {
        cancelNativeLoading(container)
        container.visibility = View.VISIBLE
        container.removeAllViews()
        val shimmer = createNativeShimmer(container.context, placement)
        val shimmerAnimator = ObjectAnimator.ofFloat(shimmer, View.ALPHA, 0.48f, 1f, 0.48f).apply {
            duration = 950L
            repeatCount = ObjectAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
        shimmer.tag = shimmerAnimator
        container.addView(shimmer)
    }

    private fun cancelNativeLoading(container: FrameLayout) {
        for (index in 0 until container.childCount) {
            (container.getChildAt(index).tag as? ObjectAnimator)?.cancel()
        }
    }

    private fun renderNativeAdInto(container: FrameLayout, placement: String, nativeAd: NativeAd) {
        cancelNativeLoading(container)
        val nativeView = LayoutInflater.from(container.context)
            .inflate(nativeAdLayoutFor(placement), container, false) as NativeAdView
        applyNativeAdTheme(nativeView, placement)
        bindNativeAd(nativeAd, nativeView)
        container.removeAllViews()
        container.addView(nativeView)
        container.visibility = View.VISIBLE
    }

    private fun nativeAdLayoutFor(placement: String): Int =
        if (placement == "native_vocab") {
            R.layout.ad_native_vocab
        } else {
            R.layout.ad_native_medium
        }

    private fun createNativeShimmer(context: Context, placement: String): View {
        val heightDp = if (placement == "native_vocab") 72 else 196
        val colors = nativeAdColors(context)
        return View(context).apply {
            minimumHeight = dp(context, heightDp)
            background = roundedRect(
                fill = colors.surface,
                stroke = colors.border,
                radius = dp(context, if (placement == "native_vocab") 18 else 20)
            )
            foreground = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(colors.shimmerEdge, colors.shimmerCenter, colors.shimmerEdge)
            )
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(context, heightDp)
            )
        }
    }

    private fun applyNativeAdTheme(adView: NativeAdView, placement: String) {
        val context = adView.context
        val colors = nativeAdColors(context)
        val radius = dp(context, if (placement == "native_vocab") 18 else 20)
        adView.background = roundedRect(colors.surface, colors.border, radius)

        adView.findViewById<TextView?>(R.id.ad_badge)?.apply {
            setTextColor(colors.badgeText)
            background = roundedRect(colors.badgeSurface, Color.TRANSPARENT, dp(context, 6))
        }
        adView.findViewById<TextView?>(R.id.ad_headline)?.setTextColor(colors.headline)
        adView.findViewById<TextView?>(R.id.ad_body)?.setTextColor(colors.body)
        adView.findViewById<Button?>(R.id.ad_call_to_action)?.apply {
            setTextColor(Color.WHITE)
            backgroundTintList = ColorStateList.valueOf(colors.cta)
        }
        adView.findViewById<View?>(R.id.ad_icon_frame)?.background =
            roundedRect(colors.iconSurface, Color.TRANSPARENT, dp(context, 12))
    }

    private data class NativeAdColors(
        val surface: Int,
        val border: Int,
        val headline: Int,
        val body: Int,
        val badgeSurface: Int,
        val badgeText: Int,
        val iconSurface: Int,
        val cta: Int,
        val shimmerEdge: Int,
        val shimmerCenter: Int
    )

    private fun nativeAdColors(context: Context): NativeAdColors {
        val isDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        return if (isDark) {
            NativeAdColors(
                surface = Color.rgb(24, 24, 30),
                border = Color.argb(130, 255, 255, 255),
                headline = Color.rgb(245, 245, 248),
                body = Color.rgb(185, 186, 196),
                badgeSurface = Color.rgb(64, 56, 24),
                badgeText = Color.rgb(255, 215, 104),
                iconSurface = Color.rgb(42, 42, 52),
                cta = Color.rgb(106, 92, 255),
                shimmerEdge = Color.argb(30, 255, 255, 255),
                shimmerCenter = Color.argb(80, 255, 255, 255)
            )
        } else {
            NativeAdColors(
                surface = Color.WHITE,
                border = Color.rgb(226, 224, 235),
                headline = Color.rgb(18, 18, 22),
                body = Color.rgb(92, 92, 100),
                badgeSurface = Color.rgb(255, 244, 216),
                badgeText = Color.rgb(106, 75, 0),
                iconSurface = Color.rgb(244, 241, 255),
                cta = Color.rgb(66, 133, 244),
                shimmerEdge = Color.argb(36, 106, 92, 255),
                shimmerCenter = Color.argb(84, 106, 92, 255)
            )
        }
    }

    private fun roundedRect(fill: Int, stroke: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius.toFloat()
            setColor(fill)
            if (stroke != Color.TRANSPARENT) {
                setStroke(1, stroke)
            }
        }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density + 0.5f).toInt()

    fun preloadInterstitial(context: Context, placement: String = "inter_all") {
        if (!canRequestInterstitial() || isLoadingInterstitial || interstitialAd != null) return

        val adUnitId = AppConfig.placementAdUnitId(placement)
        if (adUnitId.isBlank()) return

        isLoadingInterstitial = true
        interstitialAdUnitId = adUnitId
        InterstitialAd.load(
            context.applicationContext,
            adUnitId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    isLoadingInterstitial = false
                    interstitialAd = ad
                    Log.d(TAG, "Interstitial loaded")
                    val pending = pendingInterstitialShow
                    if (pending != null) {
                        if (!isActivityReadyForFullScreen(pending.activity)) {
                            pendingInterstitialShow = null
                            interstitialAd = null
                            pending.onComplete()
                            return
                        }
                        val pendingAdUnitId = AppConfig.placementAdUnitId(pending.placement)
                        if (pendingAdUnitId == interstitialAdUnitId) {
                            pendingInterstitialShow = null
                            showInterstitialIfReady(pending.activity, pending.placement, pending.onComplete)
                        } else {
                            pendingInterstitialShow = null
                            interstitialAd = null
                            showInterstitial(pending.activity, pending.placement, onComplete = pending.onComplete)
                        }
                    }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    isLoadingInterstitial = false
                    interstitialAd = null
                    Log.w(TAG, "Interstitial failed to load: ${error.message}")
                    pendingInterstitialShow?.let { pending ->
                        pendingInterstitialShow = null
                        pending.onComplete()
                    }
                }
            }
        )
    }

    fun showHomeNavigationInterstitial(
        activity: Activity,
        placement: String,
        onComplete: () -> Unit
    ) {
        showInterstitial(activity, "inter_all", placementFrequency = 1, onComplete = onComplete)
    }

    fun showInterstitial(
        activity: Activity,
        placement: String,
        placementFrequency: Int = AppConfig.placementFrequency(placement),
        onComplete: () -> Unit
    ) {
        if (!isActivityReadyForFullScreen(activity) || isFullScreenShowing) {
            onComplete()
            return
        }

        fullScreenActionCount += 1
        val attempts = (placementAttempts[placement] ?: 0) + 1
        placementAttempts[placement] = attempts

        if (attempts % placementFrequency != 0) {
            onComplete()
            return
        }

        val neededAdUnitId = AppConfig.placementAdUnitId(placement)
        if (interstitialAd == null || interstitialAdUnitId != neededAdUnitId) {
            if (!shouldShowInterstitial(placement) || !shouldWaitForInterstitialLoad(placement)) {
                interstitialAd = null
                onComplete()
                return
            }
            interstitialAd = null
            pendingInterstitialShow = PendingInterstitialShow(activity, placement, onComplete)
            preloadInterstitial(activity, placement)
            return
        }

        showInterstitialIfReady(activity, placement, onComplete)
    }

    fun showRewarded(
        activity: Activity,
        placement: String,
        onReward: (RewardItem) -> Unit,
        onComplete: () -> Unit
    ) {
        if (!canShowRewarded(activity, placement)) {
            onComplete()
            return
        }
        val ad = rewardedAd
        if (ad == null) {
            preloadRewarded(activity)
            onComplete()
            return
        }
        rewardedAd = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                isFullScreenShowing = false
                onComplete()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                isFullScreenShowing = false
                FirebaseCrashlytics.getInstance().recordException(
                    IllegalStateException("Rewarded failed placement=$placement: ${error.message}")
                )
                onComplete()
            }
        }
        isFullScreenShowing = true
        ad.show(activity) { reward ->
            incrementRewardCount(activity)
            onReward(reward)
        }
    }

    fun showAppOpenIfAvailable(activity: Activity) {
        val placement = "app_open_start"
        if (!isActivityReadyForFullScreen(activity) || !canShowAppOpen(placement) || hasActiveInterstitial()) {
            return
        }
        val ad = appOpenAd
        if (ad == null) {
            preloadAppOpen(activity)
            return
        }
        appOpenAd = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                isFullScreenShowing = false
                markPlacementShown(placement)
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                isFullScreenShowing = false
            }

            override fun onAdShowedFullScreenContent() {
                isFullScreenShowing = true
            }
        }
        ad.show(activity)
    }

    private fun showInterstitialIfReady(
        activity: Activity,
        placement: String,
        onComplete: () -> Unit
    ) {
        val ad = interstitialAd
        if (!shouldShowInterstitial(placement) || ad == null) {
            onComplete()
            return
        }

        interstitialAd = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                isFullScreenShowing = false
                onComplete()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                isFullScreenShowing = false
                Log.w(TAG, "Interstitial failed to show: ${error.message}")
                FirebaseCrashlytics.getInstance().recordException(
                    IllegalStateException("Interstitial failed to show: ${error.message}")
                )
                onComplete()
            }

            override fun onAdShowedFullScreenContent() {
                isFullScreenShowing = true
                lastShownAt = SystemClock.elapsedRealtime()
                shownThisSession += 1
                markPlacementShown(placement)
                Log.d(TAG, "Interstitial shown at placement=$placement")
            }
        }

        ad.show(activity)
    }

    private fun canRequestInterstitial(): Boolean {
        return AppConfig.adsEnabled &&
            AppConfig.interstitialAdsEnabled &&
            AppConfig.interstitialSessionCap > 0
    }

    private fun shouldShowInterstitial(placement: String): Boolean {
        if (!canRequestInterstitial()) return false
        if (!AppConfig.placementEnabled(placement)) return false
        if (!AppConfig.interstitialEnabledPlacements.contains(placement)) return false
        if (shownThisSession >= AppConfig.interstitialSessionCap) return false
        if (!firstLaunchInterstitialPlacements.contains(placement) && !canShowInterstitialBeforeHome()) return false
        if (
            placement != "inter_splash" &&
            placement != "inter_language_complete" &&
            placement != "inter_all" &&
            placement != "inter_time_duel" &&
            placement != "inter_vocab" &&
            placement != "inter_assessment" &&
            fullScreenActionCount < AppConfig.interstitialActionGap
        ) return false

        val now = SystemClock.elapsedRealtime()
        if (now - sessionStartedAt < AppConfig.interstitialFirstDelayMillis) return false
        if (lastShownAt > 0L && now - lastShownAt < AppConfig.interstitialMinIntervalMillis) return false
        val placementCooldownMillis = if (
            placement == "inter_all" ||
            placement == "inter_time_duel" ||
            placement == "inter_vocab" ||
            placement == "inter_assessment"
        ) {
            AppConfig.interstitialMinIntervalMillis
        } else {
            AppConfig.placementCooldownMillis(placement)
        }
        if (now - (placementLastShownAt[placement] ?: 0L) < placementCooldownMillis) return false

        return true
    }

    private fun shouldWaitForInterstitialLoad(placement: String): Boolean =
        immediateInterstitialPlacements.contains(placement)

    private fun canShowBanner(placement: String): Boolean =
        AppConfig.canShowBannerPlacement(placement) &&
            canShowPlacementByFrequencyAndCooldown(placement)

    private fun canShowNative(placement: String): Boolean =
        AppConfig.canShowNativePlacement(placement) &&
            canShowPlacementByFrequencyAndCooldown(placement)

    private fun canShowRewarded(context: Context, placement: String): Boolean =
        AppConfig.adsEnabled &&
            AppConfig.rewardedAdsEnabled &&
            AppConfig.placementEnabled(placement) &&
            AppConfig.rewardedDailyLimit > 0 &&
            rewardCountToday(context) < AppConfig.rewardedDailyLimit

    private fun canShowAppOpen(placement: String): Boolean =
        AppConfig.adsEnabled &&
            AppConfig.appOpenAdsEnabled &&
            AppConfig.placementEnabled(placement) &&
            canShowInterstitialBeforeHome() &&
            (SystemClock.elapsedRealtime() - (placementLastShownAt[placement] ?: 0L) >= AppConfig.placementCooldownMillis(placement))

    private fun hasActiveInterstitial(): Boolean =
        isFullScreenShowing || pendingInterstitialShow != null

    private fun isActivityReadyForFullScreen(activity: Activity): Boolean {
        if (activity.isFinishing) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && activity.isDestroyed) return false
        val lifecycleOwner = activity as? LifecycleOwner
        return lifecycleOwner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) ?: true
    }

    private fun canShowPlacementByFrequencyAndCooldown(placement: String): Boolean {
        val attempts = (placementAttempts[placement] ?: 0) + 1
        placementAttempts[placement] = attempts
        if (attempts % AppConfig.placementFrequency(placement) != 0) return false
        val now = SystemClock.elapsedRealtime()
        if (now - (placementLastShownAt[placement] ?: 0L) < AppConfig.placementCooldownMillis(placement)) return false
        return true
    }

    private fun bindNativeAd(nativeAd: NativeAd, adView: NativeAdView) {
        adView.headlineView = adView.findViewById(R.id.ad_headline)
        adView.bodyView = adView.findViewById(R.id.ad_body)
        adView.callToActionView = adView.findViewById(R.id.ad_call_to_action)
        adView.iconView = adView.findViewById(R.id.ad_app_icon)
        adView.mediaView = adView.findViewById(R.id.ad_media)

        (adView.headlineView as TextView).text = nativeAd.headline
        (adView.bodyView as TextView).apply {
            text = nativeAd.body.orEmpty()
            visibility = if (nativeAd.body.isNullOrBlank()) View.GONE else View.VISIBLE
        }
        (adView.callToActionView as Button).apply {
            text = nativeAd.callToAction.orEmpty()
            visibility = if (nativeAd.callToAction.isNullOrBlank()) View.GONE else View.VISIBLE
        }
        (adView.iconView as ImageView).apply {
            val icon = nativeAd.icon
            visibility = if (icon == null) View.GONE else View.VISIBLE
            setImageDrawable(icon?.drawable)
        }
        adView.setNativeAd(nativeAd)
    }

    private fun preloadRewarded(context: Context) {
        if (!AppConfig.adsEnabled || !AppConfig.rewardedAdsEnabled || isLoadingRewarded || rewardedAd != null) return
        val adUnitId = AppConfig.placementAdUnitId("reward_ai")
        if (adUnitId.isBlank()) return
        isLoadingRewarded = true
        RewardedAd.load(
            context.applicationContext,
            adUnitId,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    isLoadingRewarded = false
                    rewardedAd = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    isLoadingRewarded = false
                    rewardedAd = null
                    Log.w(TAG, "Rewarded failed to load: ${error.message}")
                }
            }
        )
    }

    private fun preloadAppOpen(context: Context) {
        if (!AppConfig.adsEnabled || !AppConfig.appOpenAdsEnabled || isLoadingAppOpen || appOpenAd != null) return
        val adUnitId = AppConfig.placementAdUnitId("app_open_start")
        if (adUnitId.isBlank()) return
        isLoadingAppOpen = true
        AppOpenAd.load(
            context.applicationContext,
            adUnitId,
            AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    isLoadingAppOpen = false
                    appOpenAd = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    isLoadingAppOpen = false
                    appOpenAd = null
                    Log.w(TAG, "App open failed to load: ${error.message}")
                }
            }
        )
    }

    private fun recordLaunch(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(KEY_LAUNCH_COUNT, prefs.getInt(KEY_LAUNCH_COUNT, 0) + 1)
            .apply()
    }

    private fun canShowInterstitialBeforeHome(): Boolean {
        val prefs = appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE) ?: return false
        val launchCount = prefs.getInt(KEY_LAUNCH_COUNT, 1)
        val homeReached = prefs.getBoolean(KEY_HOME_REACHED, false)
        return homeReached || launchCount >= 2
    }

    private fun markPlacementShown(placement: String) {
        placementLastShownAt[placement] = SystemClock.elapsedRealtime()
    }

    private fun rewardCountToday(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = LocalDate.now().toString()
        return if (prefs.getString(KEY_REWARD_DATE, "") == today) {
            prefs.getInt(KEY_REWARD_COUNT, 0)
        } else {
            0
        }
    }

    private fun incrementRewardCount(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = LocalDate.now().toString()
        val count = if (prefs.getString(KEY_REWARD_DATE, "") == today) {
            prefs.getInt(KEY_REWARD_COUNT, 0) + 1
        } else {
            1
        }
        prefs.edit()
            .putString(KEY_REWARD_DATE, today)
            .putInt(KEY_REWARD_COUNT, count)
            .apply()
    }
}
