package com.mobile.trackerapp.ui.tools

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import com.mobile.trackerapp.R
import com.mobile.trackerapp.ads.AdRemoteConfig
import com.mobile.trackerapp.ads.AdsManager
import com.mobile.trackerapp.ads.banner_all
import com.mobile.trackerapp.ads.banner_splash
import com.mobile.trackerapp.ads.banner_splash_uninstall
import com.mobile.trackerapp.databinding.ActivitySplashBinding
import com.mobile.trackerapp.databinding.ActivityToolsBinding
import com.mobile.trackerapp.ui.DashboardActivity

class ToolsActivity : DashboardActivity() {
    private lateinit var binding: ActivityToolsBinding

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val root = layoutInflater.inflate(R.layout.activity_tools, null) as LinearLayout
        //root.addView(nav("Tools"), LinearLayout.LayoutParams(-1, wrap()))
        binding = ActivityToolsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        AdsManager.loadInterHome(this, ignoreLimit = true)
        hideNavigationBar()
        loadToolsBanner()
        root.findViewById<View>(R.id.tool_compass_row).setOnClickListener {
            startActivity(android.content.Intent(this, CompassActivity::class.java))
        }
        root.findViewById<View>(R.id.tool_speedometer_row).setOnClickListener {
            startActivity(android.content.Intent(this, SpeedometerActivity::class.java))
        }
        root.findViewById<View>(R.id.tool_level_row).setOnClickListener {
            startActivity(android.content.Intent(this, LevelMeterActivity::class.java))
        }
        root.findViewById<View>(R.id.tool_area_codes_row).setOnClickListener {
            startActivity(android.content.Intent(this, AreaCodesActivity::class.java))
        }
        root.findViewById<View>(R.id.tool_stopwatch_row).setOnClickListener {
            startActivity(android.content.Intent(this, StopwatchActivity::class.java))
        }
        root.findViewById<View>(R.id.tool_traffic_row).setOnClickListener {
            startActivity(android.content.Intent(this, TrafficAlertActivity::class.java))
        }
        root.findViewById<View>(R.id.tool_zone_row).setOnClickListener {
            startActivity(android.content.Intent(this, ZoneAlertActivity::class.java))
        }
        root.findViewById<View>(R.id.tool_nearby_row).setOnClickListener {
            startActivity(android.content.Intent(this, NearbyActivity::class.java))
        }
    }

    private fun loadToolsBanner() {
        val bannerConfig = AdRemoteConfig.banner_all
        AdsManager.loadBanner(
            this, bannerConfig, binding.frBanner, isCollapse = false
        )
    }

    override fun onBackPressed() {
        AdsManager.showInterHomeBack(this) {
            finish()
        }
    }
}
