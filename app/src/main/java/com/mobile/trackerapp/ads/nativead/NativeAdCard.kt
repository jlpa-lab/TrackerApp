package com.mobile.trackerapp.ads.nativead

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.deep.lumoraai.ads.nativead.NativeAdManager
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.mobile.trackerapp.R
import com.mobile.trackerapp.ads.AdFormat
import com.mobile.trackerapp.ads.AdPlacement
import com.mobile.trackerapp.ads.AdsConfigStore
import com.mobile.trackerapp.ads.NativeAdStyle
import com.mobile.trackerapp.ads.NativeStyleConfig
import com.mobile.trackerapp.ads.shimmer.NativeLargeShimmer
import com.mobile.trackerapp.ads.shimmer.NativeRegularShimmer

/**
 * Renders a native ad by inflating the dedicated XML layout (regular or large)
 * and registering the SDK asset views on the [NativeAdView] — the SDK supplies
 * assets; we own the presentation.
 *
 * State machine: Loading -> shimmer -> (Loaded -> render) or (Failed -> collapse).
 * Never shows an infinite shimmer. Remote-Config styling is applied defensively.
 * Renders nothing when ads/format/placement are disabled.
 */
@Composable
fun NativeAdCard(
    placement: AdPlacement,
    nativeAdManager: NativeAdManager,
    configStore: AdsConfigStore,
    modifier: Modifier = Modifier,
    slotKey: String? = null,
) {
    val configVersion = configStore.version
    val config = configStore.current
    if (!config.formatEnabled(AdFormat.NATIVE) || !config.isPlacementEnabled(placement)) return

    val context = LocalContext.current
    val style = config.nativeStyle
    val cacheKey = slotKey ?: placement.key
    // null = loading, ad = loaded, and a separate failed flag to collapse.
    var nativeAd by remember(configVersion, cacheKey) { mutableStateOf<NativeAd?>(null) }
    var failed by remember(configVersion, cacheKey) { mutableStateOf(false) }

    DisposableEffect(configVersion, cacheKey) {
        nativeAdManager.load(
            context = context,
            placement = placement,
            cacheKey = cacheKey,
            onLoaded = { ad -> nativeAd = ad; failed = false },
            onFailed = { failed = true },
        )
        onDispose { /* manager owns cache lifecycle */ }
    }

    // Collapse entirely on failure/no-fill.
    if (failed && nativeAd == null) return

    val ad = nativeAd
    if (ad == null) {
        if (style.shimmerEnabled) {
            when (placement.nativeStyle) {
                NativeAdStyle.LARGE -> NativeLargeShimmer(modifier.fillMaxWidth())
                else -> NativeRegularShimmer(modifier.fillMaxWidth())
            }
        }
        return
    }

    AnimatedVisibility(visible = true, enter = fadeIn(), exit = fadeOut()) {
        AndroidView(
            modifier = modifier.fillMaxWidth(),
            factory = { ctx ->
                val layoutRes = if (placement.nativeStyle == NativeAdStyle.LARGE) {
                    R.layout.ad_native_large
                } else {
                    R.layout.ad_native_regular
                }
                val adView = LayoutInflater.from(ctx).inflate(layoutRes, null) as NativeAdView
                bindAssetViews(adView)
                applyStyle(adView, style)
                adView
            },
            update = { adView -> populate(adView, ad) },
        )
    }
}

/** Register each asset view on the NativeAdView (required by AdMob). */
private fun bindAssetViews(adView: NativeAdView) {
    adView.headlineView = adView.findViewById<TextView>(R.id.ad_headline)
    adView.bodyView = adView.findViewById<TextView>(R.id.ad_body)
    adView.callToActionView = adView.findViewById<Button>(R.id.ad_call_to_action)
    adView.iconView = adView.findViewById<ImageView>(R.id.ad_app_icon)
    adView.mediaView = adView.findViewById<MediaView?>(R.id.ad_media)
}

/** Apply validated Remote-Config styling to the container/text/CTA. */
private fun applyStyle(adView: NativeAdView, style: NativeStyleConfig) {
    val container = adView.findViewById<View>(R.id.ad_container)
    val density = adView.resources.displayMetrics.density
    val bg = GradientDrawable().apply {
        setColor(style.backgroundColor.toInt())
        cornerRadius = style.cornerRadiusDp * density
        // Always keep a visible border on the native ad card.
        setStroke((1 * density).toInt(), 0x33D6FF2F.toInt())
    }
    container?.background = bg
    val pad = (style.paddingDp * density).toInt()
    container?.setPadding(pad, pad, pad, pad)

    (adView.headlineView as? TextView)?.apply {
        setTextColor(style.titleColor.toInt())
        textSize = style.titleSp.toFloat()
    }
    (adView.bodyView as? TextView)?.apply {
        setTextColor(style.bodyColor.toInt())
        textSize = style.bodySp.toFloat()
    }
    (adView.callToActionView as? Button)?.apply {
        val cta = GradientDrawable().apply {
            setColor(style.ctaBackgroundColor.toInt())
            cornerRadius = 10f * density
        }
        background = cta
        setTextColor(style.ctaTextColor.toInt())
    }
}

/** Fill asset views with the ad's content and finalize the NativeAdView. */
private fun populate(adView: NativeAdView, ad: NativeAd) {
    (adView.headlineView as? TextView)?.text = ad.headline
    (adView.bodyView as? TextView)?.apply {
        text = ad.body
        visibility = if (ad.body.isNullOrBlank()) View.GONE else View.VISIBLE
    }
    (adView.callToActionView as? Button)?.apply {
        text = ad.callToAction ?: "Learn more"
        visibility = if (ad.callToAction.isNullOrBlank()) View.GONE else View.VISIBLE
    }
    (adView.iconView as? ImageView)?.apply {
        val drawable = ad.icon?.drawable
        if (drawable != null) {
            setImageDrawable(drawable)
            visibility = View.VISIBLE
        } else {
            visibility = View.GONE
        }
    }
    adView.mediaView?.let { mv ->
        // FIT_CENTER shows the FULL ad creative without cropping/cutting it.
        mv.setImageScaleType(ImageView.ScaleType.FIT_CENTER)
        ad.mediaContent?.let { mv.mediaContent = it }
    }
    adView.setNativeAd(ad)
}
