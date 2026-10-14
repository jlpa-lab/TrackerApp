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
import com.mobile.trackerapp.R
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
        setContentView(buildScreen())
    }

    private fun buildScreen(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(246, 251, 248))
            fitsSystemWindows = true
        }
        val scroll = ScrollView(this).apply { isFillViewport = true; clipToPadding = false }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(22))
        }
        scroll.addView(content, android.view.ViewGroup.LayoutParams(-1, -2))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val back = ImageView(this).apply {
            setImageResource(R.drawable.ic_phone_back)
            imageTintList = android.content.res.ColorStateList.valueOf(ink)
            contentDescription = "Back"
            isClickable = true
            isFocusable = true
            setOnClickListener { finish() }
        }
        header.addView(back, LinearLayout.LayoutParams(dp(28), dp(34)).apply { marginEnd = dp(14) })
        val heading = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        heading.addView(text("GPS Tools", 28f, ink, true))
        heading.addView(text("Your everyday navigation toolkit", 15f, muted).apply { setPadding(0, dp(3), 0, 0) })
        header.addView(heading, LinearLayout.LayoutParams(0, wrap(), 1f))
        content.addView(header, LinearLayout.LayoutParams(-1, wrap()).apply { bottomMargin = dp(18) })

        val tools = listOf(
            Tool("Compass", "Find your heading and orientation", R.drawable.tool_compass, CompassActivity::class.java),
            Tool("Stopwatch", "Track time accurately", R.drawable.tool_stopwatch, StopwatchActivity::class.java),
            Tool("Speedometer", "Track your current speed", R.drawable.tool_speedometer, SpeedometerActivity::class.java),
            Tool("Area Codes", "Look up phone area codes by location", R.drawable.tool_area_codes, AreaCodesActivity::class.java),
            Tool("Level Meter", "Check if a surface is flat or level", R.drawable.tool_level_meter, LevelMeterActivity::class.java)
        )
        tools.forEachIndexed { index, tool ->
            content.addView(toolCard(tool), LinearLayout.LayoutParams(-1, wrap()).apply {
                if (index > 0) topMargin = dp(8)
            })
        }
        return root
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

    private data class Tool(val title: String, val subtitle: String, val icon: Int, val activity: Class<out android.app.Activity>)
}
