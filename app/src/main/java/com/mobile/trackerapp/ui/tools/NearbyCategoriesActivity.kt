package com.mobile.trackerapp.ui.tools

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.view.WindowCompat
import com.mobile.trackerapp.R
import com.mobile.trackerapp.ads.AdRemoteConfig
import com.mobile.trackerapp.ads.AdsManager
import com.mobile.trackerapp.ads.banner_all
import com.mobile.trackerapp.databinding.ActivityNearbyCategoriesBinding
import com.mobile.trackerapp.ui.DashboardActivity

/** Fixed nearby category picker matching the app's three-column reference design. */
class NearbyCategoriesActivity : DashboardActivity() {
    private val cardStroke = Color.rgb(232, 232, 232)
    private val labelColor = Color.rgb(18, 18, 18)

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        window.statusBarColor = Color.WHITE
        window.navigationBarColor = Color.WHITE
        hideNavigationBar()
        WindowCompat.setDecorFitsSystemWindows(window, true)

        val binding = ActivityNearbyCategoriesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.categoriesBack.setOnClickListener { finish() }
        AdsManager.loadBanner(this, AdRemoteConfig.banner_all, binding.frBanner, isCollapse = false)

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
        val categories = exploreCategories().filter { category ->
            normalizedQuery.isBlank() ||
                category.title.contains(normalizedQuery, ignoreCase = true) ||
                category.catalogName.contains(normalizedQuery, ignoreCase = true)
        }
        binding.categoriesCount.text = getString(
            if (normalizedQuery.isBlank()) R.string.nearby_categories_count
            else R.string.nearby_categories_match_count,
            categories.size
        )
        binding.categoriesSearchClear.visibility = if (normalizedQuery.isBlank()) View.GONE else View.VISIBLE
        binding.categoriesScroll.visibility = if (categories.isEmpty()) View.GONE else View.VISIBLE
        binding.categoriesEmpty.visibility = if (categories.isEmpty()) View.VISIBLE else View.GONE
        if (categories.isNotEmpty()) {
            populateCategoryGrid(binding.categoriesGrid, categories, BROWSE_COLUMN_COUNT)
        }
    }

    private fun populateCategoryGrid(
        gridRows: LinearLayout,
        categories: List<ExploreCategory>,
        columns: Int
    ) {
        gridRows.removeAllViews()
        categories.chunked(columns).forEach { rowItems ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.TOP
            }
            rowItems.forEachIndexed { index, category ->
                row.addView(categoryTile(category), gridSlotParams(index, columns))
            }
            repeat(columns - rowItems.size) { offset ->
                row.addView(View(this), gridSlotParams(rowItems.size + offset, columns))
            }
            gridRows.addView(row, LinearLayout.LayoutParams(-1, wrap()))
        }
    }

    private fun gridSlotParams(index: Int, columns: Int) =
        LinearLayout.LayoutParams(0, dp(96), 1f).apply {
            if (index < columns - 1) marginEnd = dp(10)
            bottomMargin = dp(10)
        }

    private fun categoryTile(category: ExploreCategory): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(4), dp(3), dp(4), dp(3))
        background = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = dp(18).toFloat()
            setStroke(dp(1f), cardStroke)
        }
        elevation = dp(2).toFloat()
        isClickable = true
        isFocusable = true
        contentDescription = category.title
        setOnClickListener {
            setResult(Activity.RESULT_OK, intent.putExtra(EXTRA_CATEGORY, category.catalogName))
            finish()
        }

        addView(ImageView(this@NearbyCategoriesActivity).apply {
            setImageResource(category.iconRes)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            contentDescription = null
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(44), dp(44)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        })

        addView(TextView(this@NearbyCategoriesActivity).apply {
            text = category.title
            textSize = 14f
            setTextColor(labelColor)
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            gravity = Gravity.CENTER
            includeFontPadding = false
            maxLines = 2
            setLineSpacing(0f, 0.98f)
        }, LinearLayout.LayoutParams(-1, dp(32)).apply {
            topMargin = dp(1)
        })
    }

    private fun exploreCategories() = listOf(
        category("Art Gallery", "Art gallery", R.drawable.nearby_cat_art_gallery),
        category("Bars", "Bar", R.drawable.nearby_cat_bars),
        category("Bank", "Bank", R.drawable.nearby_cat_bank),
        category("Bank ATMs", "ATM", R.drawable.nearby_cat_bank_atms),
        category("Beauty Salons", "Beauty salon", R.drawable.nearby_cat_beauty_salons),
        category("Book Stores", "Book store", R.drawable.nearby_cat_book_stores),
        category("Coffee Shops", "Coffee shop", R.drawable.nearby_cat_coffee_shops),
        category("Car Dealers", "Car dealer", R.drawable.nearby_cat_car_dealers),
        category("Gas Station", "Gas station", R.drawable.nearby_cat_gas_station),
        category("Gym", "Gym", R.drawable.nearby_cat_gym),
        category("Hospitals", "Hospital", R.drawable.nearby_cat_hospitals),
        category("Hotels", "Hotel", R.drawable.nearby_cat_hotels),
        category("Parking", "Parking", R.drawable.nearby_cat_parking),
        category("Restaurants", "Restaurant", R.drawable.nearby_cat_restaurants),
        category("Pharmacy", "Pharmacy", R.drawable.nearby_cat_pharmacy),
        category("Supermarket", "Supermarket", R.drawable.nearby_cat_supermarket),
        category("Cinema", "Movie theater", R.drawable.nearby_cat_cinema),
        category("Parks", "Park", R.drawable.nearby_cat_parks),
        category("Zoo", "Zoo", R.drawable.nearby_cat_zoo),
        category("Shopping Mall", "Shopping mall", R.drawable.nearby_cat_shopping_mall),
        category("Airport", "Airport", R.drawable.nearby_cat_airport),
        category("Bus Station", "Bus station", R.drawable.nearby_cat_bus_station),
        category("Train Station", "Train station", R.drawable.nearby_cat_train_station),
        category("Taxi Stand", "Taxi stand", R.drawable.nearby_cat_taxi_stand),
        category("Bakery", "Bakery", R.drawable.nearby_cat_bakery),
        category("Spa", "Spa", R.drawable.nearby_cat_spa),
        category("Dentist", "Dentist", R.drawable.nearby_cat_dentist),
        category("Pet Shop / Vet", "Pet store", R.drawable.nearby_cat_pet_shop_vet),
        category("Night Club", "Night club", R.drawable.nearby_cat_night_club),
        category("Post Office", "Post office", R.drawable.nearby_cat_post_office),
        category("Police Station", "Police", R.drawable.nearby_cat_police_station),
        category("School", "School", R.drawable.nearby_cat_school),
        category("University", "University", R.drawable.nearby_cat_university),
        category("Library", "Library", R.drawable.nearby_cat_library),
        category("Laundry", "Laundry", R.drawable.nearby_cat_laundry),
        category("Food Court", "Food court", R.drawable.nearby_cat_food_court),
        category("Convenience Store", "Convenience store", R.drawable.nearby_cat_convenience_store),
        category("Gaming Zone", "Video arcade", R.drawable.nearby_cat_gaming_zone),
        category("Place of Worship", "Place of worship", R.drawable.nearby_cat_place_of_worship),
        category("Laboratory", "Medical lab", R.drawable.nearby_cat_laboratory),
        category("Swimming Pool", "Swimming pool", R.drawable.nearby_cat_swimming_pool),
        category("Amusement Park", "Amusement park", R.drawable.nearby_cat_amusement_park),
        category("Photo Studio", "Photo studio", R.drawable.nearby_cat_photo_studio),
        category("Auto Repair", "Car repair", R.drawable.nearby_cat_auto_repair),
        category("Electronics Store", "Electronics store", R.drawable.nearby_cat_electronics_store),
        category("Hardware Store", "Hardware store", R.drawable.nearby_cat_hardware_store),
        category("Toy Store", "Toy store", R.drawable.nearby_cat_toy_store),
        category("Jewelry Store", "Jewelry store", R.drawable.nearby_cat_jewelry_store)
    )

    private fun category(title: String, catalogName: String, @DrawableRes iconRes: Int) =
        ExploreCategory(title, catalogName, iconRes)

    private data class ExploreCategory(
        val title: String,
        val catalogName: String,
        @DrawableRes val iconRes: Int
    )

    private fun dp(value: Float) = (value * resources.displayMetrics.density).toInt().coerceAtLeast(1)
    companion object {
        const val EXTRA_CATEGORY = "nearby_category"
        private const val BROWSE_COLUMN_COUNT = 3
    }
}
