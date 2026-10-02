package com.mobile.trackerapp.ads.banner

import android.app.Activity
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
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
import com.mobile.trackerapp.ads.shimmer.AdShimmerBox

/**
 * Adaptive anchored banner as a Composable.
 *
 * - Responsive: adaptive size computed from the current screen width, so it
 *   adjusts to width/resolution/orientation.
 * - Safe-area aware: adds navigation-bar insets so it never sits under system
 *   navigation / gesture regions (policy: no overlap with system nav).
 * - Loading state: shimmer placeholder sized to the banner height; collapses on
 *   failure/no-fill (never an infinite shimmer).
 * - Lifecycle-correct: the AdView is created once per unit/size and destroyed
 *   on dispose (not recreated on every recomposition).
 *
 * Renders nothing when ads/format/placement are disabled — the app is ad-free
 * with no empty blocking container.
 */
@Composable
fun BannerAdView(
    placement: AdPlacement,
    configStore: AdsConfigStore,
    modifier: Modifier = Modifier,
    applyNavBarPadding: Boolean = true,
    onLoadStateChange: (Boolean?) -> Unit = {},
) {
    val configVersion = configStore.version
    val config = configStore.current
    if (if (!config.formatEnabled(AdFormat.BANNER) || !config.isPlacementEnabled(placement)) {
        true
    } else {
        false
    }
    ) {
        onLoadStateChange(false)
        return
    }

    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val activity = context as? Activity ?: run {
        onLoadStateChange(false)
        return
    }
    val unitId = config.unitIdFor(placement) ?: run {
        AdsLogger.missingUnitId(placement)
        onLoadStateChange(false)
        return
    }

    val adSize = remember(configuration.screenWidthDp) {
        AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, configuration.screenWidthDp)
    }

    // load state: null=loading, true=loaded, false=failed(collapse)
    var loaded by remember(configVersion, unitId, adSize) { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(loaded) {
        onLoadStateChange(loaded)
    }

    val heightModifier = Modifier.height(adSize.height.dp.coerceAtLeastZero())
    val containerModifier = modifier
        .fillMaxWidth()
        .then(if (applyNavBarPadding) Modifier.navigationBarsPadding() else Modifier)

    Box(modifier = containerModifier, contentAlignment = Alignment.Center) {
        // Shimmer only while genuinely loading and if enabled.
        AnimatedVisibility(visible = loaded == null && config.nativeStyle.shimmerEnabled, enter = fadeIn(), exit = fadeOut()) {
            AdShimmerBox(modifier = Modifier.fillMaxWidth().then(heightModifier))
        }

        val adView = remember(configVersion, unitId, adSize) {
            AdView(context).apply {
                setAdSize(adSize)
                adUnitId = unitId
                adListener = object : AdListener() {
                    override fun onAdLoaded() {
                        AdsLogger.showSuccess(placement)
                        loaded = true
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        AdsLogger.loadFailed(placement, error.code, error.message)
                        loaded = false // collapse
                    }
                }
                setOnPaidEventListener { adValue ->
                    AdRevenueTracker.trackPaidAd(context, placement, unitId, adValue)
                }
                loadAd(AdRequest.Builder().build())
            }
        }

        // Only occupy space when loaded; failed banners collapse to nothing.
        AnimatedVisibility(visible = loaded == true, enter = fadeIn(), exit = fadeOut()) {
            AndroidView(
                factory = { FrameLayout(context).apply { addBannerView(adView) } },
                modifier = Modifier.fillMaxWidth().then(heightModifier),
            )
        }

        DisposableEffect(adView) {
            onDispose { adView.destroy() }
        }
    }
}

private fun FrameLayout.addBannerView(adView: AdView) {
    (adView.parent as? FrameLayout)?.removeView(adView)
    addView(
        adView,
        FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
        ).apply { gravity = Gravity.CENTER }
    )
    visibility = View.VISIBLE
}

private fun Dp.coerceAtLeastZero(): Dp =
    if (value < 0f) 0.dp else this
