package com.mobile.trackerapp.ui.tools

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import com.mobile.trackerapp.R
import com.mobile.trackerapp.ui.DashboardActivity

class ToolsActivity : DashboardActivity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val root = layoutInflater.inflate(R.layout.activity_tools, null) as LinearLayout
        root.addView(nav("Tools"), LinearLayout.LayoutParams(-1, wrap()))
        setContentView(root)
        hideNavigationBar()
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
}
