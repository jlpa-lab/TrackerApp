package com.mobile.trackerapp.ui.tools

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.view.MotionEvent
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.geocoding.PhoneNumberOfflineGeocoder
import com.mobile.trackerapp.R
import com.mobile.trackerapp.ads.AdRemoteConfig
import com.mobile.trackerapp.ads.AdsManager
import com.mobile.trackerapp.ads.banner_all
import com.mobile.trackerapp.databinding.ActivityAreaCodesBinding
import com.mobile.trackerapp.databinding.ActivityLevelMeterBinding
import com.mobile.trackerapp.ui.DashboardActivity
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.concurrent.thread
import java.util.concurrent.ConcurrentHashMap

/** Offline, country-aware area-code lookup. */
class AreaCodesActivity : DashboardActivity() {

    private lateinit var binding: ActivityAreaCodesBinding
    private val dark = android.graphics.Color.rgb(15, 23, 42)
    private val muted = android.graphics.Color.rgb(119, 130, 148)
    private val green = android.graphics.Color.rgb(8, 163, 73)
    private val phoneUtil by lazy { PhoneNumberUtil.getInstance() }
    private val phoneGeocoder by lazy { PhoneNumberOfflineGeocoder.getInstance() }
    private val locationManager by lazy { getSystemService(LOCATION_SERVICE) as LocationManager }
    private val locationHandler = Handler(Looper.getMainLooper())
    private val searchHandler = Handler(Looper.getMainLooper())
    private var pendingSearchRender: Runnable? = null
    private val entriesByRegion = ConcurrentHashMap<String, List<AreaCodeEntry>>()
    private val countries by lazy { buildCountryList() }
    private lateinit var selectedCountry: AreaCountry
    private lateinit var queryInput: EditText
    private lateinit var resultHost: FrameLayout
    private lateinit var countLabel: TextView
    private lateinit var sectionTitle: TextView
    private lateinit var locationButton: TextView
    private lateinit var locationButtonContainer: LinearLayout
    private lateinit var hintText: TextView
    private var allEntries: List<AreaCodeEntry> = emptyList()
    private var loading = true
    private var locationListener: LocationListener? = null
    private var countryDialog: AlertDialog? = null

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.statusBarColor = android.graphics.Color.rgb(247, 251, 248)
        window.navigationBarColor = android.graphics.Color.rgb(247, 251, 248)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        hideNavigationBar()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        selectedCountry = detectDefaultCountry()
        binding = ActivityAreaCodesBinding.inflate(layoutInflater)
        setContentView(binding.root); binding.areaBack.setOnClickListener { finish() }
        loadAreaCodeBanner()
        queryInput = binding.areaQuery; resultHost = binding.areaResults; countLabel = binding.areaCount
        sectionTitle = binding.areaSectionTitle; locationButton = binding.areaLocation; locationButtonContainer = binding.areaLocationContainer
        hintText = binding.areaHintText
        queryInput.setOnEditorActionListener { _, actionId, event ->
            val done = actionId == EditorInfo.IME_ACTION_DONE ||
                (event?.keyCode == android.view.KeyEvent.KEYCODE_ENTER)
            if (done) {
                hideAreaKeyboard()
                true
            } else false
        }
        binding.root.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN && !isTouchInside(event, queryInput)) {
                hideAreaKeyboard()
            }
            false
        }
        updateHintText(selectedCountry)
        locationButtonContainer.setOnClickListener { useMyLocation() }
        queryInput.addTextChangedListener(object : TextWatcher { override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int)=Unit; override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int)=scheduleResultsRender(); override fun afterTextChanged(s: Editable?)=Unit })
        renderResults()
        loadCountryEntries(selectedCountry)
    }

    private fun isTouchInside(event: MotionEvent, view: View): Boolean {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        val x = event.rawX
        val y = event.rawY
        return x >= location[0] && x <= location[0] + view.width &&
            y >= location[1] && y <= location[1] + view.height
    }

    private fun hideAreaKeyboard() {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(queryInput.windowToken, 0)
        queryInput.clearFocus()
    }

    private fun renderResults() {
        if (!::resultHost.isInitialized || !::queryInput.isInitialized || !::sectionTitle.isInitialized) return
        resultHost.removeAllViews()
        val query = queryInput.text?.toString()?.trim().orEmpty()
        sectionTitle.text = if (query.isBlank()) getString(R.string.area_popular) else getString(R.string.area_search_results)
        val matchingEntries = if (query.isBlank()) allEntries.take(4) else allEntries.filter {
            it.code.contains(query, ignoreCase = true) || it.city.contains(query, ignoreCase = true) || it.detail.contains(query, ignoreCase = true)
        }
        val visibleEntries = matchingEntries.take(MAX_RENDERED_RESULTS)
        countLabel.text = if (query.isBlank()) getString(R.string.area_regions_count, matchingEntries.size) else when {
            matchingEntries.size > MAX_RENDERED_RESULTS -> getString(R.string.area_results_more, MAX_RENDERED_RESULTS)
            matchingEntries.size == 1 -> getString(R.string.area_result_one)
            else -> getString(R.string.area_results_count, matchingEntries.size)
        }
        if (loading) {
            val progress = ProgressBar(this)
            resultHost.addView(progress, FrameLayout.LayoutParams(wrap(), wrap(), Gravity.CENTER))
            return
        }
        if (visibleEntries.isEmpty()) {
            showEmptyState(query)
            return
        }
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { clipToPadding = false }
        scroll.addView(list, android.view.ViewGroup.LayoutParams(-1, wrap()))
        resultHost.addView(scroll, FrameLayout.LayoutParams(-1, wrap()))
        visibleEntries.forEachIndexed { index, entry ->
            val row = buildEntryRow(entry)
            list.addView(row, LinearLayout.LayoutParams(-1, wrap()).apply { if (index > 0) topMargin = dp(12) })
        }
    }

    private fun scheduleResultsRender() {
        pendingSearchRender?.let(searchHandler::removeCallbacks)
        pendingSearchRender = Runnable { renderResults() }.also {
            searchHandler.postDelayed(it, SEARCH_DEBOUNCE_MS)
        }
    }

    private fun buildEntryRow(entry: AreaCodeEntry): View {
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(13), dp(13), dp(13), dp(13))
            background = ContextCompat.getDrawable(this@AreaCodesActivity, R.drawable.bg_area_result)
            isClickable = true; isFocusable = true
            setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.area_code_clipboard_label), entry.code))
                Toast.makeText(this@AreaCodesActivity, getString(R.string.area_code_copied, entry.code), Toast.LENGTH_SHORT).show()
            }
        }
        row.addView(TextView(this).apply {
            text = entry.code; textSize = 18f; gravity = Gravity.CENTER; setTextColor(dark)
            typeface = resources.getFont(R.font.inter_semibold)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = rounded(android.graphics.Color.rgb(235, 249, 242), dp(9).toFloat())
        }, LinearLayout.LayoutParams(wrap(), wrap()).apply { marginEnd = dp(8) })
        val copy = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        copy.addView(label(entry.city, 12f, dark, true))
        if (entry.detail.isNotBlank() && !entry.detail.equals(entry.city, ignoreCase = true)) {
            copy.addView(label(entry.detail, 10f, muted).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; setPadding(0, dp(3), 0, 0) })
        }
        row.addView(copy, LinearLayout.LayoutParams(0, wrap(), 1f))
        row.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_chevron)
            imageAlpha = 150
            contentDescription = getString(R.string.area_open_details)
        }, LinearLayout.LayoutParams(dp(16), dp(16)).apply { marginStart = dp(8) })
        return row
    }

    private fun showEmptyState(query: String) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(30), dp(28), dp(30), dp(28))
            background = borderedRounded(android.graphics.Color.WHITE, android.graphics.Color.rgb(237, 241, 239), dp(16).toFloat())
        }
        card.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_area_no_result)
            contentDescription = getString(R.string.area_no_codes)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }, LinearLayout.LayoutParams(dp(64), dp(64)))
        val emptyTitle = if (query.isBlank()) getString(R.string.area_no_regional_codes, selectedCountry.name) else getString(R.string.area_no_codes)
        val emptyMessage = if (query.isBlank()) getString(R.string.area_codes_unavailable) else getString(R.string.area_no_results_for, query)
        card.addView(label(emptyTitle, 14f, dark, true).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(-1, wrap()).apply { topMargin = dp(12) })
        card.addView(label(emptyMessage, 11f, muted).apply {
            gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER
        }, LinearLayout.LayoutParams(-1, wrap()).apply { topMargin = dp(12) })
        val clear = label(getString(R.string.nearby_clear_search), 12f, android.graphics.Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(30), dp(14), dp(30), dp(14))
            background = rounded(green, dp(100).toFloat())
            isClickable = true; isFocusable = true
            setOnClickListener { queryInput.text?.clear() }
        }
        card.addView(clear, LinearLayout.LayoutParams(wrap(), wrap()).apply { topMargin = dp(12) })
        resultHost.addView(card, FrameLayout.LayoutParams(-1, wrap()))
    }
    private fun loadAreaCodeBanner() {
        val bannerConfig = AdRemoteConfig.banner_all
        AdsManager.loadBanner(
            this, bannerConfig, binding.frBanner, isCollapse = false
        )
    }
    private fun loadCountryEntries(country: AreaCountry) {
        loading = true
        allEntries = emptyList()
        renderResults()
        val cached = entriesByRegion[country.iso]
        if (cached != null) {
            allEntries = cached
            loading = false
            renderResults()
            return
        }
        thread(name = "area-codes-${country.iso}") {
            val entries = runCatching { buildEntries(country.iso) }.getOrDefault(emptyList())
            entriesByRegion[country.iso] = entries
            runOnUiThread {
                if (selectedCountry.iso == country.iso) {
                    allEntries = entries
                    loading = false
                    renderResults()
                }
            }
        }
    }

    private fun buildEntries(iso: String): List<AreaCodeEntry> {
        if (iso == "IN") return readIndiaCodes()
        val popular = POPULAR_CODES[iso]
        if (popular != null) return popular
        return buildGeocodedEntries(iso)
    }

    /** Generates regional prefixes off the UI thread from libphonenumber's offline location data. */
    private fun buildGeocodedEntries(iso: String): List<AreaCodeEntry> {
        val callingCode = phoneUtil.getCountryCodeForRegion(iso)
        if (callingCode <= 0) return emptyList()
        val countryName = Locale("", iso).getDisplayCountry(Locale.getDefault())
        val regions = linkedMapOf<String, String>()
        for (prefixLength in 1..4) {
            val prefixCount = Math.pow(10.0, prefixLength.toDouble()).toInt()
            val suffix = "1".repeat((10 - prefixLength).coerceAtLeast(4))
            for (value in 0 until prefixCount) {
                val prefix = value.toString().padStart(prefixLength, '0')
                val regionName = runCatching {
                    val number = phoneUtil.parse("+$callingCode$prefix$suffix", "ZZ")
                    phoneGeocoder.getDescriptionForNumber(number, Locale.ENGLISH)
                }.getOrNull()?.trim().orEmpty()
                if (regionName.isNotBlank() && !regionName.equals(countryName, ignoreCase = true)) {
                    regions.putIfAbsent(regionName, prefix)
                }
            }
        }
        return regions.map { (city, prefix) -> AreaCodeEntry(prefix, city, "") }.sortedBy { it.city }
    }

    private fun readIndiaCodes(): List<AreaCodeEntry> {
        val dbFile = File(getDatabasePath("areacodes.db").absolutePath)
        if (!dbFile.exists()) {
            dbFile.parentFile?.mkdirs()
            assets.open("areacodes.db").use { input -> FileOutputStream(dbFile).use { output -> input.copyTo(output) } }
        }
        val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        val rows = mutableListOf<AreaCodeEntry>()
        db.rawQuery("SELECT city, stdcode FROM stdcodes ORDER BY city ASC", null).use { cursor ->
            while (cursor.moveToNext()) {
                val city = cursor.getString(0)?.trim().orEmpty()
                val code = "0${cursor.getInt(1)}"
                if (city.isNotEmpty()) rows += AreaCodeEntry(code, city, city)
            }
        }
        db.close()
        val popularIndia = listOf(
            AreaCodeEntry("022", "Mumbai", "Maharashtra · Western India"),
            AreaCodeEntry("011", "New Delhi", "Delhi · Northern India"),
            AreaCodeEntry("080", "Bengaluru", "Karnataka · Southern India"),
            AreaCodeEntry("044", "Chennai", "Tamil Nadu · Southern India")
        )
        val popularCodes = popularIndia.map { it.code }.toSet()
        return popularIndia + rows.filterNot { it.code in popularCodes }
    }

    private fun chooseCountry(country: AreaCountry) {
        selectedCountry = country
        updateHintText(country)
        queryInput.text?.clear()
        loadCountryEntries(country)
    }

    private fun updateHintText(country: AreaCountry) {
        if (::hintText.isInitialized) {
            hintText.text = getString(R.string.area_hint, country.dialCode)
        }
    }

    private fun showCountryPicker() {
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(12))
        }
        outer.addView(label(getString(R.string.area_choose_country), 17f, dark, true), LinearLayout.LayoutParams(-1, wrap()).apply { bottomMargin = dp(10) })
        val search = EditText(this).apply {
            hint = getString(R.string.area_search_country)
            textSize = 14f
            setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            background = borderedRounded(android.graphics.Color.rgb(247, 251, 248), android.graphics.Color.rgb(226, 234, 229), dp(11).toFloat())
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        outer.addView(search, LinearLayout.LayoutParams(-1, wrap()))
        val listContent = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val listScroll = ScrollView(this).apply { clipToPadding = false }
        listScroll.addView(listContent, android.view.ViewGroup.LayoutParams(-1, wrap()))
        val listHeight = (resources.displayMetrics.heightPixels * 0.56f).toInt()
        outer.addView(listScroll, LinearLayout.LayoutParams(-1, listHeight).apply { topMargin = dp(6) })
        val dialog = AlertDialog.Builder(this).setView(outer).create()
        countryDialog = dialog
        var pendingCountryFilter: Runnable? = null
        fun populate(filter: String) {
            listContent.removeAllViews()
            countries.filter { it.name.contains(filter, true) || it.iso.contains(filter, true) || it.dialCode.contains(filter) }
                .take(80).forEach { country ->
                    val item = label("${country.flag}   ${country.name}   ${country.dialCode}", 14f, dark)
                    item.gravity = Gravity.CENTER_VERTICAL
                    item.setPadding(dp(8), dp(9), dp(8), dp(9))
                    item.isClickable = true; item.isFocusable = true
                    item.setOnClickListener { dialog.dismiss(); chooseCountry(country) }
                    listContent.addView(item, LinearLayout.LayoutParams(-1, wrap()))
                }
        }
        populate("")
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val filter = s?.toString().orEmpty()
                pendingCountryFilter?.let(searchHandler::removeCallbacks)
                pendingCountryFilter = Runnable { populate(filter) }.also {
                    searchHandler.postDelayed(it, SEARCH_DEBOUNCE_MS)
                }
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        dialog.show()
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.92f).toInt(), WindowManager.LayoutParams.WRAP_CONTENT)
    }

    private fun useMyLocation() {
        if (!hasLocationPermission()) {
            ActivityCompat.requestPermissions(this, LOCATION_PERMISSIONS, LOCATION_REQUEST)
            return
        }
        val last = runCatching {
            locationManager.getProviders(true).mapNotNull { provider -> locationManager.getLastKnownLocation(provider) }
                .maxByOrNull { it.time }
        }.getOrNull()
        if (last != null) {
            resolveCountry(last)
            return
        }
        val provider = runCatching {
            listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
                .firstOrNull { locationManager.isProviderEnabled(it) }
        }.getOrNull()
        if (provider == null) {
            Toast.makeText(this, R.string.area_turn_on_location, Toast.LENGTH_SHORT).show()
            return
        }
        locationButton.setText(R.string.area_finding_region)
        locationButton.setTextColor(muted)
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                locationManager.removeUpdates(this)
                locationListener = null
                locationButton.setText(R.string.common_use_my_location)
                locationButton.setTextColor(green)
                resolveCountry(location)
            }
            override fun onProviderDisabled(provider: String) = Unit
            override fun onProviderEnabled(provider: String) = Unit
        }
        locationListener = listener
        try {
            locationManager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            locationHandler.postDelayed({
                locationListener?.let { locationManager.removeUpdates(it) }
                locationListener = null
                if (locationButton.text.toString() == getString(R.string.area_finding_region)) {
                    locationButton.setText(R.string.common_use_my_location)
                    locationButton.setTextColor(green)
                    Toast.makeText(this, R.string.area_location_retry, Toast.LENGTH_SHORT).show()
                }
            }, LOCATION_TIMEOUT_MS)
        } catch (_: SecurityException) {
            locationButton.setText(R.string.common_use_my_location)
            locationButton.setTextColor(green)
            Toast.makeText(this, R.string.area_location_permission_required, Toast.LENGTH_SHORT).show()
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveCountry(location: Location) {
        thread(name = "area-code-geocoder") {
            val iso = runCatching {
                if (!Geocoder.isPresent()) null else Geocoder(this, Locale.getDefault())
                    .getFromLocation(location.latitude, location.longitude, 1)?.firstOrNull()?.countryCode
            }.getOrNull()?.uppercase(Locale.ROOT)
            runOnUiThread {
                val match = countries.firstOrNull { it.iso == iso }
                if (match != null) {
                    chooseCountry(match)
                    Toast.makeText(this, getString(R.string.area_showing_codes_for, match.name), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, R.string.area_region_not_identified, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun hasLocationPermission() = LOCATION_PERMISSIONS.any {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_REQUEST && grantResults.any { it == PackageManager.PERMISSION_GRANTED }) useMyLocation()
        else if (requestCode == LOCATION_REQUEST) Toast.makeText(this, R.string.area_location_feature_permission, Toast.LENGTH_SHORT).show()
    }

    private fun detectDefaultCountry(): AreaCountry {
        val telephony = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        val iso = sequenceOf(telephony.networkCountryIso, telephony.simCountryIso, Locale.getDefault().country, "US")
            .firstOrNull { !it.isNullOrBlank() && it.uppercase(Locale.ROOT) in phoneUtil.supportedRegions }
            ?.uppercase(Locale.ROOT) ?: "US"
        return countries.firstOrNull { it.iso == iso } ?: countries.first { it.iso == "US" }
    }

    private fun buildCountryList(): List<AreaCountry> = phoneUtil.supportedRegions.mapNotNull { iso ->
        val callingCode = phoneUtil.getCountryCodeForRegion(iso)
        if (callingCode <= 0) null else AreaCountry(
            Locale("", iso).getDisplayCountry(Locale.getDefault()).ifBlank { iso },
            iso,
            "+$callingCode",
            isoToFlag(iso)
        )
    }.sortedBy { it.name }

    private fun isoToFlag(iso: String): String = buildString {
        iso.uppercase(Locale.ROOT).forEach { appendCodePoint(it.code + 127397) }
    }

    override fun onDestroy() {
        locationListener?.let { runCatching { locationManager.removeUpdates(it) } }
        locationHandler.removeCallbacksAndMessages(null)
        countryDialog?.dismiss()
        pendingSearchRender?.let(searchHandler::removeCallbacks)
        searchHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun label(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color)
        typeface = resources.getFont(if (bold) R.font.inter_semibold else R.font.inter_regular)
    }

    private fun rounded(color: Int, radius: Float) = android.graphics.drawable.GradientDrawable().apply {
        setColor(color); cornerRadius = radius
    }

    private fun borderedRounded(color: Int, stroke: Int, radius: Float) = android.graphics.drawable.GradientDrawable().apply {
        setColor(color); cornerRadius = radius; setStroke((resources.displayMetrics.density * 0.6f).roundToInt().coerceAtLeast(1), stroke)
    }

    private fun findViewWithTag(root: View, tag: Any): View? {
        if (root.tag == tag) return root
        if (root is android.view.ViewGroup) for (i in 0 until root.childCount) findViewWithTag(root.getChildAt(i), tag)?.let { return it }
        return null
    }

    private data class AreaCountry(val name: String, val iso: String, val dialCode: String, val flag: String)
    private data class AreaCodeEntry(val code: String, val city: String, val detail: String)

    companion object {
        private const val LOCATION_REQUEST = 82
        private const val LOCATION_TIMEOUT_MS = 12_000L
        private const val SEARCH_DEBOUNCE_MS = 120L
        private const val MAX_RENDERED_RESULTS = 40
        private val LOCATION_PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        private val POPULAR_CODES = mapOf(
            "US" to listOf(
                AreaCodeEntry("212", "New York", "New York · Eastern Time"),
                AreaCodeEntry("415", "San Francisco", "California · Pacific Time"),
                AreaCodeEntry("213", "Los Angeles", "California · Pacific Time"),
                AreaCodeEntry("312", "Chicago", "Illinois · Central Time")
            ),
            "CA" to listOf(
                AreaCodeEntry("416", "Toronto", "Ontario · Eastern Time"),
                AreaCodeEntry("604", "Vancouver", "British Columbia · Pacific Time"),
                AreaCodeEntry("514", "Montreal", "Quebec · Eastern Time"),
                AreaCodeEntry("403", "Calgary", "Alberta · Mountain Time")
            ),
            "GB" to listOf(
                AreaCodeEntry("020", "London", "England · United Kingdom"),
                AreaCodeEntry("0121", "Birmingham", "England · United Kingdom"),
                AreaCodeEntry("0161", "Manchester", "England · United Kingdom"),
                AreaCodeEntry("0131", "Edinburgh", "Scotland · United Kingdom")
            ),
            "AU" to listOf(
                AreaCodeEntry("02", "Sydney", "New South Wales · Australia"),
                AreaCodeEntry("03", "Melbourne", "Victoria · Australia"),
                AreaCodeEntry("07", "Brisbane", "Queensland · Australia"),
                AreaCodeEntry("08", "Perth", "Western Australia · Australia")
            ),
            "DE" to listOf(
                AreaCodeEntry("030", "Berlin", "Berlin · Germany"),
                AreaCodeEntry("040", "Hamburg", "Hamburg · Germany"),
                AreaCodeEntry("089", "Munich", "Bavaria · Germany"),
                AreaCodeEntry("069", "Frankfurt", "Hesse · Germany")
            ),
            "FR" to listOf(
                AreaCodeEntry("01", "Paris", "Île-de-France · France"),
                AreaCodeEntry("02", "Northwest France", "Northwest · France"),
                AreaCodeEntry("03", "Northeast France", "Northeast · France"),
                AreaCodeEntry("04", "Southeast France", "Southeast · France")
            ),
            "JP" to listOf(
                AreaCodeEntry("03", "Tokyo", "Tokyo · Japan"),
                AreaCodeEntry("06", "Osaka", "Osaka · Japan"),
                AreaCodeEntry("045", "Yokohama", "Kanagawa · Japan"),
                AreaCodeEntry("052", "Nagoya", "Aichi · Japan")
            ),
            "IN" to listOf(
                AreaCodeEntry("022", "Mumbai", "Maharashtra · Western India"),
                AreaCodeEntry("011", "New Delhi", "Delhi · Northern India"),
                AreaCodeEntry("080", "Bengaluru", "Karnataka · Southern India"),
                AreaCodeEntry("044", "Chennai", "Tamil Nadu · Southern India")
            )
        )
    }
}
