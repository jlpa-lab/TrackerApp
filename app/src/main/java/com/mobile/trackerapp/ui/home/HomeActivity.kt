package com.mobile.trackerapp.ui.home

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.mobile.trackerapp.R
import com.mobile.trackerapp.ui.connection.ConnectionActivity
import com.mobile.trackerapp.ui.connection.FriendRequestsActivity
import com.mobile.trackerapp.ui.settings.SettingsActivity
import com.mobile.trackerapp.ui.tools.GpsToolsActivity
import com.mobile.trackerapp.ui.tools.NearbyActivity
import com.mobile.trackerapp.ui.tools.TrafficAlertActivity
import com.mobile.trackerapp.ui.tools.ZoneAlertActivity
import com.mobile.trackerapp.ui.tools.ToolsActivity
import com.mobile.trackerapp.ui.phonelocator.PhoneLocatorActivity
import com.mobile.trackerapp.ui.tracker.TrackFriendActivity

class HomeActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.activity_main)
        //hideNavigationBar()
        findViewById<android.view.View>(R.id.open_live_map).setOnClickListener { startActivity(Intent(this, com.mobile.trackerapp.ui.livelocation.LiveLocationActivity::class.java)) }
        findViewById<android.view.View>(R.id.action_phone_locator).setOnClickListener { startActivity(Intent(this, PhoneLocatorActivity::class.java)) }
        findViewById<android.view.View>(R.id.action_track_friend).setOnClickListener { startActivity(Intent(this, TrackFriendActivity::class.java)) }
        findViewById<android.view.View>(R.id.action_shared_connection).setOnClickListener { startActivity(Intent(this, FriendRequestsActivity::class.java)) }
        findViewById<android.view.View>(R.id.view_all_tools).setOnClickListener { startActivity(Intent(this, ToolsActivity::class.java)) }
        findViewById<android.view.View>(R.id.home_gps_tools_tile).setOnClickListener { startActivity(Intent(this, GpsToolsActivity::class.java)) }
        findViewById<android.view.View>(R.id.home_traffic_alert_tile).setOnClickListener { startActivity(Intent(this, TrafficAlertActivity::class.java)) }
        findViewById<android.view.View>(R.id.home_zone_alert_tile).setOnClickListener { startActivity(Intent(this, ZoneAlertActivity::class.java)) }
        findViewById<android.view.View>(R.id.home_nearby_tile).setOnClickListener { startActivity(Intent(this, NearbyActivity::class.java)) }
        findViewById<android.view.View>(R.id.nav_tools).setOnClickListener { startActivity(Intent(this, ToolsActivity::class.java)) }
        findViewById<android.view.View>(R.id.nav_connection).setOnClickListener { startActivity(Intent(this, ConnectionActivity::class.java)) }
        findViewById<android.view.View>(R.id.nav_settings).setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
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
