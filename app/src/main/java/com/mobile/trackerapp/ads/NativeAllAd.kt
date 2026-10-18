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
    root.visibility = View.VISIBLE
    adContainer.visibility = View.GONE
    shimmer.visibility = View.VISIBLE
    shimmer.startShimmer()

    AdsManager.nativeAllLoadingLive.observe(activity) { loading ->
        if (loading) {
            root.visibility = View.VISIBLE
            adContainer.visibility = View.GONE
            shimmer.visibility = View.VISIBLE
            shimmer.startShimmer()
        }
    }

    AdsManager.nativeAllAdLive.observe(activity) { ad ->
        if (ad != null) {
            root.visibility = View.VISIBLE
            adContainer.visibility = View.VISIBLE
            populateNativeAdView(activity, ad, adContainer, shimmer)
        } else {
            if (AdsManager.nativeAllLoadingLive.value != true) {
                shimmer.stopShimmer()
                root.visibility = View.GONE
            }
        }
    }
}
