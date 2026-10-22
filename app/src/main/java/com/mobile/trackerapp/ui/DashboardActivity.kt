package com.location.and.phone.tracker.locationnphonetracker.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.appcompat.app.AppCompatActivity
import com.location.and.phone.tracker.locationnphonetracker.ads.AdsManager

abstract class DashboardActivity : AppCompatActivity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        AdsManager.loadInterBack(this, ignoreLimit = true)
    }

    protected fun hideNavigationBar() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.decorView.windowInsetsController?.apply {
                hide(WindowInsets.Type.navigationBars())
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }
    }

    protected fun screen(title: String, subtitle: String): android.widget.LinearLayout {
        val root = android.widget.LinearLayout(this)
        root.orientation = android.widget.LinearLayout.VERTICAL
        root.setBackgroundColor(Color.rgb(248, 252, 249))
        root.fitsSystemWindows = false
        root.setPadding(dp(16), dp(16), dp(16), 0)
        val scroll = android.widget.ScrollView(this)
        val content = android.widget.LinearLayout(this)
        content.orientation = android.widget.LinearLayout.VERTICAL
        val heading = android.widget.TextView(this).apply { text = title; textSize = 32f; setTextColor(Color.rgb(10, 18, 35)); setTypeface(typeface, android.graphics.Typeface.BOLD) }
        val sub = android.widget.TextView(this).apply { text = subtitle; textSize = 18f; setTextColor(Color.rgb(120, 132, 148)) }
        content.addView(heading); content.addView(sub, android.widget.LinearLayout.LayoutParams(wrap(), wrap()).apply { topMargin = dp(4) })
        scroll.addView(content); root.addView(scroll, android.widget.LinearLayout.LayoutParams(-1, 0, 1f))
        return root
    }
    protected fun text(value: String, size: Float = 16f, color: Int = Color.rgb(10, 18, 35)): android.widget.TextView = android.widget.TextView(this).apply { text = value; textSize = size; setTextColor(color); setPadding(0, dp(10), 0, dp(10)) }
    protected fun card(): android.widget.LinearLayout = android.widget.LinearLayout(this).apply { orientation = android.widget.LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE); setPadding(dp(16), dp(12), dp(16), dp(12)) }
    protected fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    protected fun wrap() = android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
    protected fun nav(active: String): android.widget.LinearLayout = android.widget.LinearLayout(this).apply {
        val bar = layoutInflater.inflate(com.location.and.phone.tracker.locationnphonetracker.R.layout.home_bottom_nav, this, false) as android.widget.LinearLayout
        val activeColor = Color.rgb(8, 163, 49)
        val inactiveColor = Color.rgb(120, 132, 148)
        val tabs = listOf(
            "Home" to bar.getChildAt(0),
            "Tools" to bar.findViewById<View>(com.location.and.phone.tracker.locationnphonetracker.R.id.nav_tools),
            "Connection" to bar.findViewById<View>(com.location.and.phone.tracker.locationnphonetracker.R.id.nav_connection),
            "Setting" to bar.findViewById<View>(com.location.and.phone.tracker.locationnphonetracker.R.id.nav_settings)
        )
        tabs.forEach { (name, tab) ->
            val selected = name == active
            val stateColor = if (selected) activeColor else inactiveColor
            (tab as android.view.ViewGroup).getChildAt(1).let { label ->
                (label as android.widget.TextView).setTextColor(stateColor)
                label.typeface = android.graphics.Typeface.create(
                    if (selected) "sans-serif-medium" else "sans-serif", android.graphics.Typeface.NORMAL
                )
            }
            ((tab as android.view.ViewGroup).getChildAt(0) as? android.widget.ImageView)?.apply {
                // Apply the inactive tint too; Home's vector drawable is green by default.
                setColorFilter(stateColor)
            }
        }
        bar.findViewById<View>(com.location.and.phone.tracker.locationnphonetracker.R.id.nav_tools).setOnClickListener {
            if (active != "Tools") { startActivity(Intent(this@DashboardActivity, com.location.and.phone.tracker.locationnphonetracker.ui.tools.ToolsActivity::class.java)); finish() }
        }
        bar.findViewById<View>(com.location.and.phone.tracker.locationnphonetracker.R.id.nav_connection).setOnClickListener {
            if (active != "Connection") { startActivity(Intent(this@DashboardActivity, com.location.and.phone.tracker.locationnphonetracker.ui.connection.ConnectionActivity::class.java)); finish() }
        }
        bar.findViewById<View>(com.location.and.phone.tracker.locationnphonetracker.R.id.nav_settings).setOnClickListener {
            if (active != "Setting") { startActivity(Intent(this@DashboardActivity, com.location.and.phone.tracker.locationnphonetracker.ui.settings.SettingsActivity::class.java)); finish() }
        }
        bar.getChildAt(0).setOnClickListener {
            if (active != "Home") { startActivity(Intent(this@DashboardActivity, com.location.and.phone.tracker.locationnphonetracker.ui.home.HomeActivity::class.java)); finish() }
        }
        addView(bar, android.widget.LinearLayout.LayoutParams(-1, wrap()))
    }
}


