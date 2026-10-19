package com.mobile.trackerapp.ui.tools

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.mobile.trackerapp.R
import com.mobile.trackerapp.ads.bindNativeAllAd
import com.mobile.trackerapp.ads.AdsManager
import com.mobile.trackerapp.ui.DashboardActivity

/** Collection of the app's GPS and navigation tools. */
class GpsToolsActivity : DashboardActivity() {
    private val ink = Color.rgb(13, 22, 39)
    private val muted = Color.rgb(119, 130, 148)
    private val outline = Color.rgb(224, 234, 228)

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.statusBarColor = Color.rgb(246, 251, 248)
        window.navigationBarColor = Color.rgb(246, 251, 248)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        hideNavigationBar()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val binding = com.mobile.trackerapp.databinding.ActivityGpsToolsBinding.inflate(layoutInflater)
        setContentView(binding.root); binding.gpsToolsBack.setOnClickListener { finish() }
        AdsManager.loadInterHome(this, ignoreLimit = true)
        AdsManager.loadNativeAll(this, R.layout.layout_native_ad_small)
        bindNativeAllAd(this, findViewById(R.id.native_all_container))
        binding.gpsToolsBack.setOnClickListener { onBackPressed() }
        binding.gpsCompassRow.root.setOnClickListener { startActivity(Intent(this, CompassActivity::class.java)) }
        binding.gpsStopwatchRow.root.setOnClickListener { startActivity(Intent(this, StopwatchActivity::class.java)) }
        binding.gpsSpeedometerRow.root.setOnClickListener { startActivity(Intent(this, SpeedometerActivity::class.java)) }
        binding.gpsAreaCodesRow.root.setOnClickListener { startActivity(Intent(this, AreaCodesActivity::class.java)) }
        binding.gpsLevelMeterRow.root.setOnClickListener { startActivity(Intent(this, LevelMeterActivity::class.java)) }
    }

    private fun toolCard(tool: Tool): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(16), dp(14), dp(16))
        background = bordered(Color.WHITE, outline, dp(22))
        isClickable = true
        isFocusable = true
        contentDescription = "${tool.title}. ${tool.subtitle}"
        setOnClickListener { startActivity(Intent(this@GpsToolsActivity, tool.activity)) }

        addView(ImageView(this@GpsToolsActivity).apply {
            setImageResource(tool.icon)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = null
        }, LinearLayout.LayoutParams(dp(54), dp(54)).apply { marginEnd = dp(16) })

        val labels = LinearLayout(this@GpsToolsActivity).apply { orientation = LinearLayout.VERTICAL }
        labels.addView(text(tool.title, 20f, ink, true))
        labels.addView(text(tool.subtitle, 15f, muted).apply { setPadding(0, dp(3), 0, 0) })
        addView(labels, LinearLayout.LayoutParams(0, wrap(), 1f))
        addView(text("›", 30f, Color.rgb(181, 191, 201), true).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(wrap(), wrap()).apply { marginStart = dp(8) })
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.create("sans-serif", Typeface.BOLD)
    }

    private fun bordered(color: Int, stroke: Int, radius: Int) = android.graphics.drawable.GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
        setStroke(dp(1), stroke)
    }

    override fun onBackPressed() {
        AdsManager.showInterHomeBack(this) {
            finish()
        }
    }

    private data class Tool(val title: String, val subtitle: String, val icon: Int, val activity: Class<out android.app.Activity>)
}
