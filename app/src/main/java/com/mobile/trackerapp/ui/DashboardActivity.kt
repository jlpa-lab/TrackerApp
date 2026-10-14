package com.mobile.trackerapp.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController

abstract class DashboardActivity : Activity() {
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
        root.fitsSystemWindows = true
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
        gravity = android.view.Gravity.CENTER; setBackgroundResource(com.mobile.trackerapp.R.drawable.bg_bottom_nav); setPadding(0, dp(12), 0, dp(12))
        val items = listOf(
            Triple("Home", com.mobile.trackerapp.ui.home.HomeActivity::class.java, com.mobile.trackerapp.R.drawable.ic_nav_home),
            Triple("Tools", com.mobile.trackerapp.ui.tools.ToolsActivity::class.java, com.mobile.trackerapp.R.drawable.ic_nav_tools),
            Triple("Connection", com.mobile.trackerapp.ui.connection.ConnectionActivity::class.java, com.mobile.trackerapp.R.drawable.ic_nav_connection),
            Triple("Setting", com.mobile.trackerapp.ui.settings.SettingsActivity::class.java, com.mobile.trackerapp.R.drawable.ic_nav_settings)
        )
        items.forEach { (label, target, icon) ->
            val item = android.widget.LinearLayout(this@DashboardActivity).apply { orientation = android.widget.LinearLayout.VERTICAL; gravity = android.view.Gravity.CENTER }
            item.addView(android.widget.ImageView(this@DashboardActivity).apply {
                setImageResource(
                    when {
                        label == "Tools" && label == active -> com.mobile.trackerapp.R.drawable.ic_nav_tools_active
                        label == "Home" && label != active -> com.mobile.trackerapp.R.drawable.ic_nav_home_inactive
                        else -> icon
                    }
                )
                alpha = 1f
                if (label == active && label != "Tools") {
                    setColorFilter(Color.rgb(8, 163, 49))
                } else {
                    clearColorFilter()
                }
            }, android.widget.LinearLayout.LayoutParams(dp(24), dp(24)))
            item.addView(text(label, 14f, if (label == active) Color.rgb(8, 163, 49) else Color.rgb(72, 76, 82)).apply { gravity = android.view.Gravity.CENTER; setPadding(0, dp(2), 0, 0) })
            addView(item, android.widget.LinearLayout.LayoutParams(0, wrap(), 1f)); item.setOnClickListener { if (label != active) { startActivity(Intent(this@DashboardActivity, target)); finish() } }
        }
    }
}


