package com.mobile.trackerapp

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.RadioGroup
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.ads.module.ads.wrapper.ApNativeAd
import com.mobile.trackerapp.ads.AdsManager
import com.mobile.trackerapp.ads.populateNativeAdView
import com.mobile.trackerapp.bases.goneView
import com.mobile.trackerapp.bases.visibleView
import com.mobile.trackerapp.databinding.ActivityUninstallReasonBinding
import com.mobile.trackerapp.ui.BaseActivity

/** Collects a reason before handing uninstalling to Android's system screen. */
class UninstallReasonActivity : BaseActivity<ActivityUninstallReasonBinding>() {

    override fun getLayoutActivity() = R.layout.activity_uninstall_reason
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
        applySystemInsets(findViewById(R.id.uninstall_reason_root))
        Log.d("AppEvent", "MainActivity_uninstall_screen_02")

        findViewById<View>(R.id.back_button).setOnClickListener { finish() }
        findViewById<View>(R.id.home_button).setOnClickListener { openHome() }
        findViewById<View>(R.id.cancel_button).setOnClickListener {
            Log.d("AppEvent", "Uninstall02_cancel")
            openHome()
        }
        findViewById<View>(R.id.uninstall_button).setOnClickListener {
            // Android owns the final confirmation and package removal.
            try {
                val intent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    "package:${packageName}".toUri()
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            } catch (_: Exception) {
            }
            finish()

//            startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName")))
        }
    }
    override fun initViews() {
        super.initViews()
        AdsManager.loadNativeSurvey(this, R.layout.layout_native_ad_medium)
    }

    override fun observerData() {
        super.observerData()
        AdsManager.nativeSurveyAdLive.observe(this) { ad -> renderSurveyAd(ad) }
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
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        })
        finish()
    }

    private fun renderSurveyAd(ad: ApNativeAd?) {
        val frAds = mBinding.root.findViewById<android.widget.FrameLayout>(R.id.fr_ads)
            ?: return
        if (ad == null) {
            frAds.goneView()
            return
        }
        frAds.visibleView()
        val shimmer = mBinding.root.findViewById<com.facebook.shimmer.ShimmerFrameLayout>(R.id.shimmer_ads)
        if (shimmer != null) {
            populateNativeAdView(this, ad, frAds, shimmer)
        }
    }

}
