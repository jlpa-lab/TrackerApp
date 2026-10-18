package com.mobile.trackerapp.ads

import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import com.facebook.shimmer.ShimmerFrameLayout
import com.mobile.trackerapp.R

fun bindNativeAllAd(activity: AppCompatActivity, root: View?) {
    root ?: return
    val adContainer = root.findViewById<FrameLayout>(R.id.native_all_ad) ?: return
    val shimmer = root.findViewById<ShimmerFrameLayout>(R.id.shimmer_native_small) ?: return
    var pendingRender: Runnable? = null
    root.visibility = View.VISIBLE
    adContainer.visibility = View.GONE
    shimmer.visibility = View.VISIBLE
    shimmer.startShimmer()

    AdsManager.nativeAllLoadingLive.observe(activity) { loading ->
        if (loading && AdsManager.nativeAllAdLive.value == null) {
            root.visibility = View.VISIBLE
            adContainer.visibility = View.GONE
            shimmer.visibility = View.VISIBLE
            shimmer.startShimmer()
        }
    }

    AdsManager.nativeAllAdLive.observe(activity) { ad ->
        if (ad != null) {
            root.visibility = View.VISIBLE
            // Always give the loading state a visible handoff, including when
            // this screen receives a cached ad immediately on entry.
            pendingRender?.let(root::removeCallbacks)
            adContainer.visibility = View.GONE
            shimmer.visibility = View.VISIBLE
            shimmer.startShimmer()
            pendingRender = Runnable {
                if (!activity.isFinishing && !activity.isDestroyed && root.isAttachedToWindow) {
                    adContainer.visibility = View.VISIBLE
                    populateNativeAdView(activity, ad, adContainer, shimmer)
                }
            }.also { root.postDelayed(it, SHIMMER_HANDOFF_DELAY_MS) }
        } else {
            if (AdsManager.nativeAllLoadingLive.value != true) {
                shimmer.stopShimmer()
                root.visibility = View.GONE
            }
        }
    }
}

private const val SHIMMER_HANDOFF_DELAY_MS = 220L
