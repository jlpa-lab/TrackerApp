package com.deep.lumoraai.ads.nativead

import android.content.Context
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.mobile.trackerapp.ads.AdFormat
import com.mobile.trackerapp.ads.AdPlacement
import com.mobile.trackerapp.ads.AdRevenueTracker
import com.mobile.trackerapp.ads.AdsConfigStore
import com.mobile.trackerapp.ads.AdsLogger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads native ad objects. The SDK returns the assets; the app renders them via
 * XML layouts (see NativeAdCard). A per-placement-key cache prevents reloading
 * on recomposition/scroll and controls list insertion cost.
 */
@Singleton
class NativeAdManager @Inject constructor(
    private val configStore: AdsConfigStore,
) {
    private val cache = mutableMapOf<String, NativeAd>()
    private val loading = mutableSetOf<String>()

    /**
     * Load (or return cached) native ad for [placement].
     * @param onLoaded fires with the ad when ready.
     * @param onFailed fires on load failure/no-fill so the UI can collapse the
     *   shimmer instead of showing it forever.
     */
    fun load(
        context: Context,
        placement: AdPlacement,
        cacheKey: String = placement.key,
        onLoaded: (NativeAd) -> Unit,
        onFailed: () -> Unit = {},
    ) {
        val config = configStore.current
        if (!config.formatEnabled(AdFormat.NATIVE)) {
            onFailed(); return
        }
        val key = cacheKey
        cache[key]?.let { onLoaded(it); return }
        if (key in loading) return
        loading.add(key)

        val unitId = config.unitIdFor(placement) ?: run {
            AdsLogger.missingUnitId(placement)
            onFailed()
            return
        }
        AdsLogger.loadStarted(placement, unitId, config.testMode)
        AdLoader.Builder(context.applicationContext, unitId)
            .forNativeAd { nativeAd ->
                nativeAd.setOnPaidEventListener { adValue ->
                    AdRevenueTracker.trackPaidAd(context, placement, unitId, adValue)
                }
                AdsLogger.loadSucceeded(placement, nativeAd.responseInfo?.mediationAdapterClassName)
                cache.put(key, nativeAd)?.destroy()
                loading.remove(key)
                onLoaded(nativeAd)
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    AdsLogger.loadFailed(placement, error.code, error.message)
                    loading.remove(key)
                    onFailed()
                }
            })
            .build()
            .loadAd(AdRequest.Builder().build())
    }

    fun destroy(placement: AdPlacement) {
        cache.remove(placement.key)?.destroy()
    }

    fun destroyAll() {
        cache.values.forEach { it.destroy() }
        cache.clear()
        loading.clear()
    }
}
