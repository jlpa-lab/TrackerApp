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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
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
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val binding = com.mobile.trackerapp.databinding.ActivityNearbyCategoriesBinding.inflate(layoutInflater)
        setContentView(binding.root); binding.categoriesBack.setOnClickListener { finish() }
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val statusBarInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.setPadding(dp(16), statusBarInset + dp(16), dp(16), dp(16))
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
        populateCategoryGrid(binding.categoriesGrid)
    }

    private fun populateCategoryGrid(gridRows: LinearLayout) {
        NearbyCategoryCatalog.all.chunked(3).forEach { rowItems ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.TOP }
            rowItems.forEach { category ->
                val tile = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(6), dp(12), dp(6), dp(12))
                    background = bordered(Color.WHITE, Color.rgb(229, 237, 233), dp(15)); isClickable = true; isFocusable = true
                    setOnClickListener { setResult(Activity.RESULT_OK, intent.putExtra(EXTRA_CATEGORY, category.name)); finish() }
                }
                tile.addView(label(category.icon, 24f, green).apply { gravity = Gravity.CENTER; setPadding(dp(6), dp(5), dp(6), dp(5)); background = rounded(Color.rgb(237, 249, 244), dp(25)) })
                tile.addView(label(category.name, 12f, ink, true).apply { gravity = Gravity.CENTER; maxLines = 2; setPadding(0, dp(7), 0, 0) }, LinearLayout.LayoutParams(-1, wrap()))
                row.addView(tile, LinearLayout.LayoutParams(0, wrap(), 1f).apply { marginEnd = dp(7); bottomMargin = dp(8) })
            }
            repeat(3 - rowItems.size) { row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f).apply { marginEnd = dp(7) }) }
            gridRows.addView(row, LinearLayout.LayoutParams(-1, wrap()))
        }
    }

    private fun label(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color)
        if (bold) typeface = Typeface.create("sans-serif", Typeface.BOLD)
    }

    private fun rounded(color: Int, radiusDp: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radiusDp).toFloat() }
    private fun bordered(color: Int, stroke: Int, radiusDp: Int) = rounded(color, radiusDp).apply { setStroke(dp(0.6f), stroke) }

    private fun dp(value: Float) = (value * resources.displayMetrics.density).toInt().coerceAtLeast(1)

    companion object { const val EXTRA_CATEGORY = "nearby_category" }
}
