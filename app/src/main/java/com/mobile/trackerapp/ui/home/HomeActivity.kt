package com.mobile.trackerapp.ui.home

import android.content.Intent
import android.os.Bundle
import com.ads.module.ads.ERainAd
import com.ads.module.funtion.AdCallback
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import com.facebook.shimmer.ShimmerFrameLayout
import com.mobile.trackerapp.R
import com.mobile.trackerapp.ads.AdRemoteConfig
import com.mobile.trackerapp.ads.AdsManager
import com.mobile.trackerapp.ads.inter_home
import com.mobile.trackerapp.ads.inter_splash
import com.mobile.trackerapp.ads.inter_splash_uninstall
import com.mobile.trackerapp.bases.ext.isNetwork
import com.mobile.trackerapp.ads.populateNativeAdView
import com.mobile.trackerapp.databinding.ActivityMainBinding
import com.mobile.trackerapp.ui.connection.ConnectionActivity
import com.mobile.trackerapp.ui.connection.FriendRequestsActivity
import com.mobile.trackerapp.ui.connection.ShareConnectionActivity
import com.mobile.trackerapp.ui.settings.SettingsActivity
import com.mobile.trackerapp.ui.tools.GpsToolsActivity
import com.mobile.trackerapp.ui.tools.NearbyActivity
import com.mobile.trackerapp.ui.tools.TrafficAlertActivity
import com.mobile.trackerapp.ui.tools.ZoneAlertActivity
import com.mobile.trackerapp.ui.tools.ToolsActivity
import com.mobile.trackerapp.ui.phonelocator.PhoneLocatorActivity
import com.mobile.trackerapp.ui.livelocation.LiveLocationActivity
import com.mobile.trackerapp.ui.tracker.TrackFriendMapActivity
import com.mobile.trackerapp.ui.tracker.TrackFriendActivity
import com.mobile.trackerapp.utils.Routes

class HomeActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var nativeHomeContainer: FrameLayout
    private lateinit var nativeHomeAdContainer: FrameLayout
    private lateinit var nativeHomeShimmer: ShimmerFrameLayout

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        nativeHomeContainer = binding.nativeHomeContainer
        nativeHomeAdContainer = binding.frNativeHome
        nativeHomeShimmer = binding.nativeHomeShimmer.root
        observeNativeHomeAd()
        AdsManager.loadNativeHome(this, R.layout.layout_native_home)
        AdsManager.loadNativeAll(this, R.layout.layout_native_ad_small)
        //hideNavigationBar()
        findViewById<android.view.View>(R.id.open_live_map).setOnClickListener { startActivity(Intent(this, com.mobile.trackerapp.ui.livelocation.LiveLocationActivity::class.java)) }
        findViewById<android.view.View>(R.id.action_phone_locator).setOnClickListener {
            AdsManager.showInterHome(this) {
                startActivity(Intent(this, PhoneLocatorActivity::class.java))
            }

        }
        findViewById<android.view.View>(R.id.action_track_friend).setOnClickListener {
            startActivity(Intent(this, TrackFriendMapActivity::class.java))
        }
        findViewById<android.view.View>(R.id.action_shared_connection).setOnClickListener { startActivity(Intent(this, ShareConnectionActivity::class.java)) }
        findViewById<android.view.View>(R.id.view_all_tools).setOnClickListener { startActivity(Intent(this, ToolsActivity::class.java)) }
        findViewById<android.view.View>(R.id.home_gps_tools_tile).setOnClickListener { startActivity(Intent(this, GpsToolsActivity::class.java)) }
        findViewById<android.view.View>(R.id.home_traffic_alert_tile).setOnClickListener { startActivity(Intent(this, TrafficAlertActivity::class.java)) }
        findViewById<android.view.View>(R.id.home_zone_alert_tile).setOnClickListener { startActivity(Intent(this, ZoneAlertActivity::class.java)) }
        findViewById<android.view.View>(R.id.home_nearby_tile).setOnClickListener { startActivity(Intent(this, NearbyActivity::class.java)) }
        findViewById<android.view.View>(R.id.nav_tools).setOnClickListener { startActivity(Intent(this, ToolsActivity::class.java)) }
        findViewById<android.view.View>(R.id.nav_connection).setOnClickListener { startActivity(Intent(this, ConnectionActivity::class.java)) }
        findViewById<android.view.View>(R.id.nav_settings).setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
    }

    private fun observeNativeHomeAd() {
        AdsManager.nativeHomeAdLive.removeObservers(this)
        AdsManager.nativeHomeLoadingLive.observe(this) { loading ->
            if (loading) {
                showNativeHomeShimmer()
            } else if (AdsManager.nativeHomeAdLive.value == null) {
                hideNativeHomeAd()
            }
        }
        AdsManager.nativeHomeAdLive.observe(this) { nativeAd ->
            if (nativeAd == null) {
                if (AdsManager.nativeHomeLoadingLive.value != true) hideNativeHomeAd()
                return@observe
            }

            if (nativeAd.admobNativeAd == null && nativeAd.nativeView == null) {
                hideNativeHomeAd()
                return@observe
            }

            renderNativeHomeAd(nativeAd)
        }
    }

    private fun renderNativeHomeAd(nativeAd: com.ads.module.ads.wrapper.ApNativeAd) {
        nativeHomeContainer.visibility = View.VISIBLE
        nativeHomeAdContainer.visibility = View.VISIBLE
        populateNativeAdView(
            activity = this,
            apNativeAd = nativeAd,
            adPlaceHolder = nativeHomeAdContainer,
            containerShimmerLoading = nativeHomeShimmer
        )
    }

    override fun onStart() {
        super.onStart()
        AdsManager.nativeHomeAdLive.value?.let { renderNativeHomeAd(it) }
    }

    private fun showNativeHomeShimmer() {
        nativeHomeContainer.visibility = View.VISIBLE
        nativeHomeAdContainer.visibility = View.GONE
        nativeHomeShimmer.visibility = View.VISIBLE
        nativeHomeShimmer.startShimmer()
    }

    private fun hideNativeHomeAd() {
        nativeHomeShimmer.stopShimmer()
        nativeHomeShimmer.visibility = View.GONE
        nativeHomeAdContainer.removeAllViews()
        nativeHomeAdContainer.visibility = View.GONE
        nativeHomeContainer.visibility = View.GONE
    }

    override fun onDestroy() {
        if (::nativeHomeShimmer.isInitialized) nativeHomeShimmer.stopShimmer()
        super.onDestroy()
    }

    private fun hideNavigationBar() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(android.view.WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }
    }
}
