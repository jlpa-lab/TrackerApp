package com.mobile.trackerapp

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
import com.ads.module.ads.wrapper.ApNativeAd
import com.facebook.shimmer.ShimmerFrameLayout
import com.mobile.trackerapp.ads.AdsManager
import com.mobile.trackerapp.ads.populateNativeAdView
import com.mobile.trackerapp.bases.goneView
import com.mobile.trackerapp.bases.visibleView
import com.mobile.trackerapp.databinding.ActivityUninstallConfirmBinding
import com.mobile.trackerapp.ui.BaseActivity
import com.mobile.trackerapp.utils.Routes

/** First shortcut screen, giving the user a chance to keep the app. */
class UninstallConfirmActivity : BaseActivity<ActivityUninstallConfirmBinding>() {
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
        applySystemInsets(findViewById(R.id.uninstall_confirm_root))
        Log.d("AppEvent", "MainActivity_uninstall_screen_01")

        findViewById<View>(R.id.back_button).setOnClickListener { finish() }
        findViewById<View>(R.id.home_button).setOnClickListener { openHome() }
        findViewById<View>(R.id.keep_app_button).setOnClickListener {
            Log.d("AppEvent", "Uninstall01_keep_phone_tracker")
            openHome()
        }
        findViewById<View>(R.id.continue_uninstall_button).setOnClickListener {
            Log.d("AppEvent", "Uninstall01_continue_to_uninstall")
            startActivity(Intent(this, UninstallReasonActivity::class.java))
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
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        })
        finish()
    }

    override fun initViews() {
        super.initViews()
        AdsManager.loadNativeConfirmUninstall(this, R.layout.layout_native_ad_medium)
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

    override fun onBackPressed() {
        Routes.startMainActivity(this)
    }
}
