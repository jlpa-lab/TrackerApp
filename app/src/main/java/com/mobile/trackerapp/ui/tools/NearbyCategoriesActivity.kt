package com.mobile.trackerapp.ui.tools

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.WindowCompat
import com.mobile.trackerapp.R
import com.mobile.trackerapp.databinding.ActivityNearbyCategoriesBinding
import com.mobile.trackerapp.ui.DashboardActivity

/** Searchable three-column category picker opened from Nearby's More chip. */
class NearbyCategoriesActivity : DashboardActivity() {
    private val ink = Color.rgb(17, 24, 39)
    private val cardStroke = Color.rgb(225, 235, 229)
    private val iconBackground = Color.rgb(204, 237, 216)
    private val iconAccent = Color.rgb(0, 112, 52)

    // Keep the Explore screen focused on exactly 25 useful daily categories.
    // The catalog still contains all Google Places subtypes for API requests,
    // but those subtypes are not all shown in this picker.
    private val mainCategoryNamesByGroup = mapOf(
        "Automotive" to setOf("Gas station", "Parking"),
        "Business" to setOf("Business center", "Corporate office"),
        "Culture" to setOf("Art gallery"),
        "Education" to setOf("School"),
        "Entertainment and Recreation" to setOf("Amusement park", "Park"),
        "Facilities" to setOf("Public bathroom"),
        "Finance" to setOf("ATM", "Bank"),
        "Food and Drink" to setOf("Cafe", "Pizza restaurant", "Restaurant"),
        "Health and Wellness" to setOf("Doctor", "Hospital", "Pharmacy"),
        "Housing" to setOf("Apartment building", "Apartment complex", "Condominium complex"),
        "Lodging" to setOf("Guest house", "Hotel"),
        "Natural Features" to setOf("Beach", "Lake", "Scenic spot"),
    )

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        window.statusBarColor = Color.rgb(246, 251, 248)
        window.navigationBarColor = Color.rgb(246, 251, 248)
        hideNavigationBar()
        WindowCompat.setDecorFitsSystemWindows(window, true)

        val binding = ActivityNearbyCategoriesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.isFocusableInTouchMode = true
        binding.root.requestFocus()
        binding.categoriesBack.setOnClickListener { finish() }

        binding.categoriesSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) {
                renderCategories(binding, text?.toString().orEmpty())
            }
            override fun afterTextChanged(text: Editable?) = Unit
        })
        binding.categoriesSearch.setOnEditorActionListener { view, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .hideSoftInputFromWindow(view.windowToken, 0)
                view.clearFocus()
                true
            } else {
                false
            }
        }
        binding.categoriesSearchClear.setOnClickListener { binding.categoriesSearch.text?.clear() }

        renderCategories(binding, "")
    }

    private fun renderCategories(binding: ActivityNearbyCategoriesBinding, query: String) {
        val normalizedQuery = query.trim()
        val filteredCategories = mainCategories().filter { category ->
            normalizedQuery.isBlank() ||
                category.name.contains(normalizedQuery, ignoreCase = true) ||
                category.group.contains(normalizedQuery, ignoreCase = true)
        }
        val matchCount = filteredCategories.size
        val hasQuery = normalizedQuery.isNotBlank()

        binding.categoriesCount.text = getString(
            if (hasQuery) R.string.nearby_categories_match_count else R.string.nearby_categories_count,
            matchCount
        )
        binding.categoriesSearchClear.visibility = if (hasQuery) View.VISIBLE else View.GONE
        binding.categoriesScroll.visibility = if (matchCount == 0) View.GONE else View.VISIBLE
        binding.categoriesEmpty.visibility = if (matchCount == 0) View.VISIBLE else View.GONE

        if (matchCount > 0) populateCategoryGrid(binding.categoriesGrid, filteredCategories)
    }

    private fun mainCategories(): List<NearbyCategory> =
        NearbyCategoryCatalog.groups().flatMap { (group, categories) ->
            val mainNames = mainCategoryNamesByGroup[group].orEmpty()
            categories.filter { it.name in mainNames }
        }

    private fun populateCategoryGrid(
        gridRows: LinearLayout,
        categories: List<NearbyCategory>
    ) {
        gridRows.removeAllViews()
        categories.chunked(3).forEach { rowItems ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.TOP
            }
            rowItems.forEachIndexed { index, category ->
                row.addView(categoryTile(category), LinearLayout.LayoutParams(0, dp(112), 1f).apply {
                    if (index < rowItems.lastIndex) marginEnd = dp(8)
                    bottomMargin = dp(10)
                })
            }
            repeat(3 - rowItems.size) {
                row.addView(View(this), LinearLayout.LayoutParams(0, dp(112), 1f).apply {
                    marginStart = dp(8)
                    bottomMargin = dp(10)
                })
            }
            gridRows.addView(row, LinearLayout.LayoutParams(-1, wrap()))
        }
    }

    private fun categoryTile(category: NearbyCategory): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(6), dp(8), dp(6), dp(8))
        background = bordered(Color.WHITE, cardStroke, 18)
        isClickable = true
        isFocusable = true
        contentDescription = category.name
        setOnClickListener {
            setResult(Activity.RESULT_OK, intent.putExtra(EXTRA_CATEGORY, category.name))
            finish()
        }

        addView(TextView(this@NearbyCategoriesActivity).apply {
            text = category.emoji
            textSize = 23f
            setTextColor(iconAccent)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            includeFontPadding = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            background = rounded(iconBackground, 15).apply {
                setStroke(dp(1f), Color.rgb(152, 211, 172))
            }
        }, LinearLayout.LayoutParams(dp(46), dp(46)))

        addView(label(category.name, 13f, ink, true).apply {
            gravity = Gravity.CENTER
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            includeFontPadding = false
        }, LinearLayout.LayoutParams(-1, 0, 1f).apply {
            gravity = Gravity.CENTER
            topMargin = dp(6)
        })
    }

    private fun label(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.create("sans-serif", Typeface.BOLD)
    }

    private fun rounded(color: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
    }

    private fun bordered(color: Int, stroke: Int, radiusDp: Int) = rounded(color, radiusDp).apply {
        setStroke(dp(1f), stroke)
    }

    private fun dp(value: Float) = (value * resources.displayMetrics.density).toInt().coerceAtLeast(1)

    companion object {
        const val EXTRA_CATEGORY = "nearby_category"
    }
}
