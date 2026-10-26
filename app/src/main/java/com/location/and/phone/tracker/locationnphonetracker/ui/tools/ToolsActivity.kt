package com.location.and.phone.tracker.locationnphonetracker.ui.tools

import android.os.Bundle
import android.content.Intent
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import com.location.and.phone.tracker.locationnphonetracker.ads.AdRemoteConfig
import com.location.and.phone.tracker.locationnphonetracker.ads.AdsManager
import com.location.and.phone.tracker.locationnphonetracker.ads.banner_all
import com.location.and.phone.tracker.locationnphonetracker.databinding.ActivityToolsBinding
import com.location.and.phone.tracker.locationnphonetracker.ui.DashboardActivity
import com.location.and.phone.tracker.locationnphonetracker.ui.rating.RateAppManager

class ToolsActivity : DashboardActivity() {
    private lateinit var binding: ActivityToolsBinding

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        binding = ActivityToolsBinding.inflate(layoutInflater)
        val toolsBanner = binding.frBanner
        binding.root.removeView(toolsBanner)
        binding.root.addView(nav("Tools"), LinearLayout.LayoutParams(-1, wrap()))
        binding.root.addView(toolsBanner)
        setContentView(binding.root)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                AdsManager.showInterHomeBack(this@ToolsActivity, onAction = { adWasShown ->
                    if (adWasShown) RateAppManager.skipAutomaticTriggerForInterstitial()
                    finish()
                })
            }
        })
        AdsManager.loadInterHome(this, ignoreLimit = true)
        hideNavigationBar()
        loadToolsBanner()
        binding.toolCompassRow.root.setOnClickListener {
            startActivity(Intent(this, CompassActivity::class.java))
        }
        binding.toolSpeedometerRow.root.setOnClickListener {
            startActivity(Intent(this, SpeedometerActivity::class.java))
        }
        binding.toolLevelRow.root.setOnClickListener {
            startActivity(Intent(this, LevelMeterActivity::class.java))
        }
        binding.toolAreaCodesRow.root.setOnClickListener {
            startActivity(Intent(this, AreaCodesActivity::class.java))
        }
        binding.toolStopwatchRow.root.setOnClickListener {
            startActivity(Intent(this, StopwatchActivity::class.java))
        }
        binding.toolTrafficRow.root.setOnClickListener {
            startActivity(Intent(this, TrafficAlertActivity::class.java))
        }
        binding.toolZoneRow.root.setOnClickListener {
            startActivity(Intent(this, ZoneAlertActivity::class.java))
        }
        binding.toolNearbyRow.root.setOnClickListener {
            startActivity(Intent(this, NearbyActivity::class.java).putExtra(NearbyActivity.EXTRA_RETURN_TO_HOME, false))
        }
    }

    private fun loadToolsBanner() {
        val bannerConfig = AdRemoteConfig.banner_all
        AdsManager.loadBanner(
            this, bannerConfig, binding.frBanner, isCollapse = false
        )
    }

}
