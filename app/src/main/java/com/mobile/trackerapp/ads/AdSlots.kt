package com.mobile.trackerapp.ads

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mobile.trackerapp.ads.banner.BannerAdView
import com.mobile.trackerapp.ads.nativead.NativeAdCard

/**
 * Ergonomic entry points screens use to drop an ad into their layout. They read
 * the ambient AdsManager/AdsConfigStore from [AdsProvider] and render nothing if
 * ads are unavailable — so a screen can place them unconditionally and stay
 * ad-free/safe when disabled.
 */

/** Adaptive banner slot for [placement] (a BANNER-format placement). */
@Composable
fun PlacementBanner(
    placement: AdPlacement,
    modifier: Modifier = Modifier,
    applyNavBarPadding: Boolean = true,
    onLoadStateChange: (Boolean?) -> Unit = {},
) {
    val store = LocalAdsConfigStore.current ?: run {
        onLoadStateChange(false)
        return
    }
    BannerAdView(
        placement = placement,
        configStore = store,
        modifier = modifier,
        applyNavBarPadding = applyNavBarPadding,
        onLoadStateChange = onLoadStateChange,
    )
}

/** Native ad slot for [placement] (a NATIVE-format placement). */
@Composable
fun PlacementNativeAd(
    placement: AdPlacement,
    modifier: Modifier = Modifier,
    slotKey: String? = null,
) {
    val manager = LocalAdsManager.current ?: return
    val store = LocalAdsConfigStore.current ?: return
    NativeAdCard(
        placement = placement,
        nativeAdManager = manager.nativeManager,
        configStore = store,
        modifier = modifier,
        slotKey = slotKey,
    )
}
