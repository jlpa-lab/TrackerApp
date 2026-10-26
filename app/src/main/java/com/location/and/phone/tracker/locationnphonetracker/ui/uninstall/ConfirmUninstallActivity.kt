package com.location.and.phone.tracker.locationnphonetracker.ui.uninstall
import com.location.and.phone.tracker.locationnphonetracker.R

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import com.ads.module.ads.wrapper.ApNativeAd
import com.facebook.shimmer.ShimmerFrameLayout
import com.location.and.phone.tracker.locationnphonetracker.ads.AdsManager
import com.location.and.phone.tracker.locationnphonetracker.ads.populateNativeAdView
import com.location.and.phone.tracker.locationnphonetracker.bases.goneView
import com.location.and.phone.tracker.locationnphonetracker.bases.visibleView
import com.location.and.phone.tracker.locationnphonetracker.databinding.ActivityUninstallConfirmBinding
import com.location.and.phone.tracker.locationnphonetracker.ui.BaseActivity
import com.location.and.phone.tracker.locationnphonetracker.ui.home.HomeActivity
import com.location.and.phone.tracker.locationnphonetracker.utils.Routes

/** First shortcut screen, giving the user a chance to keep the app. */
class ConfirmUninstallActivity : BaseActivity<ActivityUninstallConfirmBinding>() {
    override fun getLayoutActivity() = R.layout.activity_uninstall_confirm
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.WHITE
        window.navigationBarColor = Color.WHITE
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        setContentView(mBinding.root)
        mBinding.root.findViewById<View>(R.id.fr_ads)?.updateLayoutParams<android.view.ViewGroup.MarginLayoutParams> { bottomMargin = 0 }
        applySystemInsets(findViewById(R.id.uninstall_confirm_root))
        Log.d("AppEvent", "MainActivity_uninstall_screen_01")

        findViewById<View>(R.id.back_button).setOnClickListener { openHome() }
        findViewById<View>(R.id.keep_app_button).setOnClickListener {
            Log.d("AppEvent", "Uninstall01_keep_phone_tracker")
            openHome()
        }
        findViewById<View>(R.id.continue_uninstall_button).setOnClickListener {
            Log.d("AppEvent", "Uninstall01_continue_to_uninstall")
            startActivity(Intent(this, ReasonActivity::class.java))
            finish()
        }
    }

    private fun applySystemInsets(root: View) {
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, bars.top, view.paddingRight, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun openHome() {
        // Reuse home instead of adding duplicate activities to the back stack.
        startActivity(Intent(this, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        })
        finish()
    }

    override fun initViews() {
        super.initViews()
        AdsManager.loadNativeConfirmUninstall(this, R.layout.layout_native_uninstall_confirm)
    }

    override fun observerData() {
        super.observerData()
        AdsManager.nativeConfirmUninstallAdLive.observe(this) { ad ->
            renderConfirmUninstallAd(ad)
        }
    }
    private fun renderConfirmUninstallAd(ad: ApNativeAd?) {
        val frAds = mBinding.root.findViewById<FrameLayout>(R.id.fr_ads) ?: return
        if (ad == null) {
            frAds.goneView()
            return
        }
        frAds.visibleView()
        val shimmer = mBinding.root.findViewById<ShimmerFrameLayout>(R.id.shimmer_ads)
        if (shimmer != null) {
            populateNativeAdView(this, ad, frAds, shimmer)
        }
    }

    override fun onActivityBackPressed() {
        openHome()
    }
}










