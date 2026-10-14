package com.mobile.trackerapp.ui.tools

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.mobile.trackerapp.R
import com.mobile.trackerapp.ui.DashboardActivity

/** Full, alphabetized category picker opened from Nearby's More chip. */
class NearbyCategoriesActivity : DashboardActivity() {
    private val ink = Color.rgb(17, 24, 39)
    private val muted = Color.rgb(126, 137, 153)
    private val green = Color.rgb(0, 164, 76)

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
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(246, 251, 248)); fitsSystemWindows = true }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(10), dp(16), dp(18)) }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val back = ImageView(this).apply {
            setImageResource(R.drawable.ic_phone_back)
            imageTintList = android.content.res.ColorStateList.valueOf(ink)
            contentDescription = "Back"
            isClickable = true; isFocusable = true
            setOnClickListener { finish() }
        }
        header.addView(back, LinearLayout.LayoutParams(dp(28), dp(34)).apply { marginEnd = dp(14) })
        val heading = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        heading.addView(label("Explore categories", 24f, ink, true))
        heading.addView(label("Choose a place type near you", 14f, muted).apply { setPadding(0, dp(2), 0, 0) })
        header.addView(heading, LinearLayout.LayoutParams(0, wrap(), 1f))
        content.addView(header)
        content.addView(label("ALL CATEGORIES", 12f, muted, true), LinearLayout.LayoutParams(-1, wrap()).apply { topMargin = dp(20); bottomMargin = dp(10) })

        val scroll = ScrollView(this).apply { isFillViewport = false; clipToPadding = false }
        val gridRows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val categories = NearbyCategoryCatalog.all
        categories.chunked(3).forEach { rowItems ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.TOP }
            rowItems.forEach { category ->
                val tile = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(dp(6), dp(12), dp(6), dp(12))
                    background = bordered(Color.WHITE, Color.rgb(229, 237, 233), dp(15))
                    isClickable = true; isFocusable = true
                    setOnClickListener {
                        setResult(Activity.RESULT_OK, intent.putExtra(EXTRA_CATEGORY, category.name))
                        finish()
                    }
                }
                tile.addView(label(category.icon, 24f, green).apply {
                    gravity = Gravity.CENTER
                    setPadding(dp(6), dp(5), dp(6), dp(5))
                    background = rounded(Color.rgb(237, 249, 244), dp(25))
                })
                tile.addView(label(category.name, 12f, ink, true).apply {
                    gravity = Gravity.CENTER
                    maxLines = 2
                    setPadding(0, dp(7), 0, 0)
                }, LinearLayout.LayoutParams(-1, wrap()))
                row.addView(tile, LinearLayout.LayoutParams(0, wrap(), 1f).apply {
                    marginEnd = dp(7)
                    bottomMargin = dp(8)
                })
            }
            if (rowItems.size < 3) {
                repeat(3 - rowItems.size) { row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f).apply { marginEnd = dp(7) }) }
            }
            gridRows.addView(row, LinearLayout.LayoutParams(-1, wrap()))
        }
        scroll.addView(gridRows, android.view.ViewGroup.LayoutParams(-1, wrap()))
        content.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        return root
    }

    private fun label(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color)
        if (bold) typeface = Typeface.create("sans-serif", Typeface.BOLD)
    }

    private fun rounded(color: Int, radiusDp: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radiusDp).toFloat() }
    private fun bordered(color: Int, stroke: Int, radiusDp: Int) = rounded(color, radiusDp).apply { setStroke(dp(1), stroke) }

    companion object { const val EXTRA_CATEGORY = "nearby_category" }
}
