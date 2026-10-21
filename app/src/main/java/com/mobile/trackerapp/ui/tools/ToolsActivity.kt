package com.mobile.trackerapp.ui.tools

import android.os.Bundle
import android.content.Intent
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import com.mobile.trackerapp.ads.AdRemoteConfig
import com.mobile.trackerapp.ads.AdsManager
import com.mobile.trackerapp.ads.banner_all
import com.mobile.trackerapp.databinding.ActivityToolsBinding
import com.mobile.trackerapp.ui.DashboardActivity
import com.mobile.trackerapp.ui.rating.RateAppManager

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
                AdsManager.showInterHomeBack(this@ToolsActivity) {
                    finish()
                }
            }
        })
        AdsManager.loadInterHome(this, ignoreLimit = true)
        hideNavigationBar()
        loadToolsBanner()
        binding.toolCompassRow.root.setOnClickListener {
            RateAppManager.markGpsToolUsed()
            startActivity(Intent(this, CompassActivity::class.java))
        }
        binding.toolSpeedometerRow.root.setOnClickListener {
            RateAppManager.markGpsToolUsed()
            startActivity(Intent(this, SpeedometerActivity::class.java))
        }
        binding.toolLevelRow.root.setOnClickListener {
            RateAppManager.markGpsToolUsed()
            startActivity(Intent(this, LevelMeterActivity::class.java))
        }
        binding.toolAreaCodesRow.root.setOnClickListener {
            RateAppManager.markGpsToolUsed()
            startActivity(Intent(this, AreaCodesActivity::class.java))
        }
        binding.toolStopwatchRow.root.setOnClickListener {
            RateAppManager.markGpsToolUsed()
            startActivity(Intent(this, StopwatchActivity::class.java))
        }
        binding.toolTrafficRow.root.setOnClickListener {
            RateAppManager.markCoreFeatureUsed()
            startActivity(Intent(this, TrafficAlertActivity::class.java))
        }
        binding.toolZoneRow.root.setOnClickListener {
            RateAppManager.markZoneCreated()
            startActivity(Intent(this, ZoneAlertActivity::class.java))
        }
        binding.toolNearbyRow.root.setOnClickListener {
            RateAppManager.markNearbyUsed()
            startActivity(Intent(this, NearbyActivity::class.java))
        }
    }

    private fun loadToolsBanner() {
        val bannerConfig = AdRemoteConfig.banner_all
        AdsManager.loadBanner(
            this, bannerConfig, binding.frBanner, isCollapse = false
        )
    }

}
