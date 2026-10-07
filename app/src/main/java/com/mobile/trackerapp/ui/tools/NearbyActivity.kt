package com.mobile.trackerapp.ui.tools

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.mobile.trackerapp.R
import com.mobile.trackerapp.ui.DashboardActivity
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.roundToInt

private data class NearbyPlace(
    val id: String,
    val name: String,
    val kind: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val distanceMeters: Float
)

/** Nearby search backed by live OpenStreetMap data, with routes opened in the installed maps app. */
class NearbyActivity : DashboardActivity() {
    private val ink = Color.rgb(17, 24, 39)
    private val muted = Color.rgb(126, 137, 153)
    private val green = Color.rgb(0, 164, 76)
    private val pale = Color.rgb(237, 249, 244)
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private lateinit var search: EditText
    private lateinit var clearSearch: TextView
    private lateinit var chips: LinearLayout
    private lateinit var title: TextView
    private lateinit var count: TextView
    private lateinit var results: LinearLayout
    private lateinit var statePanel: LinearLayout
    private lateinit var stateIcon: TextView
    private lateinit var stateTitle: TextView
    private lateinit var stateDescription: TextView
    private lateinit var stateAction: TextView
    private lateinit var loading: ProgressBar
    private var selectedCategory = "Coffee"
    private var lastLocation: Location? = null
    private var requestVersion = 0
    private var searchTask: Runnable? = null
    private var suppressTextListener = false
    private var currentQuery = ""
    @Volatile private var activeConnection: HttpURLConnection? = null

    private val categoriesLauncher = 7102

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.statusBarColor = Color.rgb(246, 251, 248)
        window.navigationBarColor = Color.rgb(246, 251, 248)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        hideNavigationBar()
        selectedCategory = state?.getString(STATE_CATEGORY) ?: "Coffee"
        currentQuery = state?.getString(STATE_QUERY).orEmpty()
        setContentView(buildScreen())
        suppressTextListener = true
        search.setText(currentQuery)
        suppressTextListener = false
        updateChips()
        if (hasLocationPermission()) obtainLocationAndSearch() else showLocationPermissionState()
    }

    private fun buildScreen(): View {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(246, 251, 248)); fitsSystemWindows = true }
        val scroll = ScrollView(this).apply { isFillViewport = true; clipToPadding = false }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(10), dp(16), dp(22)) }
        scroll.addView(content, android.view.ViewGroup.LayoutParams(-1, -2))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val back = ImageView(this).apply {
            setImageResource(R.drawable.ic_phone_back)
            imageTintList = android.content.res.ColorStateList.valueOf(ink)
            contentDescription = "Back"
            isClickable = true; isFocusable = true
            setOnClickListener { finish() }
        }
        header.addView(back, LinearLayout.LayoutParams(dp(28), dp(34)).apply { marginEnd = dp(14) })
        val headings = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        headings.addView(label("Nearby", 25f, ink, true))
        headings.addView(label("Find the places you need, close by", 14f, muted).apply { setPadding(0, dp(2), 0, 0) })
        header.addView(headings, LinearLayout.LayoutParams(0, wrap(), 1f))
        content.addView(header)

        val searchBox = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(3), dp(8), dp(3))
            background = bordered(Color.WHITE, Color.rgb(229, 237, 233), dp(14))
        }
        searchBox.addView(label("⌕", 27f, muted, true).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dp(32), dp(44)))
        search = EditText(this).apply {
            hint = "Search places or an address"
            textSize = 15f
            setTextColor(ink); setHintTextColor(muted)
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setPadding(dp(4), 0, dp(4), 0)
            background = null
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_SEARCH) {
                    (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.hideSoftInputFromWindow(windowToken, 0)
                    runSearchNow()
                    true
                } else false
            }
        }
        searchBox.addView(search, LinearLayout.LayoutParams(0, dp(44), 1f))
        clearSearch = label("×", 23f, muted).apply {
            gravity = Gravity.CENTER
            contentDescription = "Clear search"
            visibility = View.GONE
            isClickable = true; isFocusable = true
            setOnClickListener {
                suppressTextListener = true
                search.text?.clear()
                suppressTextListener = false
                currentQuery = ""
                updateClearVisibility()
                if (hasLocationPermission()) queryCurrentSelection()
            }
        }
        searchBox.addView(clearSearch, LinearLayout.LayoutParams(dp(30), dp(44)))
        search.addTextChangedListener(SimpleTextWatcher {
            if (suppressTextListener) return@SimpleTextWatcher
            currentQuery = search.text?.toString()?.trim().orEmpty()
            updateClearVisibility()
            searchTask?.let(main::removeCallbacks)
            if (currentQuery.length >= 2) {
                searchTask = Runnable { if (hasLocationPermission()) requestPlaces(currentQuery) else showLocationPermissionState() }
                    .also { main.postDelayed(it, SEARCH_DEBOUNCE_MS) }
            } else if (currentQuery.isEmpty() && hasLocationPermission()) {
                searchTask = Runnable { queryCurrentSelection() }.also { main.postDelayed(it, SEARCH_DEBOUNCE_MS) }
            }
        })
        content.addView(searchBox, LinearLayout.LayoutParams(-1, wrap()).apply { topMargin = dp(16) })

        val chipScroll = android.widget.HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; clipToPadding = false }
        chips = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        listOf("Coffee", "Food", "Gas", "More").forEach { chipName ->
            val chip = label("${NearbyCategoryCatalog.iconFor(chipName)}  $chipName", 14f, muted, true).apply {
                gravity = Gravity.CENTER
                setPadding(dp(13), dp(10), dp(13), dp(10))
                isClickable = true; isFocusable = true
                setOnClickListener { if (chipName == "More") openCategories() else chooseCategory(chipName) }
            }
            chips.addView(chip, LinearLayout.LayoutParams(wrap(), wrap()).apply { marginEnd = dp(8) })
        }
        chipScroll.addView(chips)
        content.addView(chipScroll, LinearLayout.LayoutParams(-1, wrap()).apply { topMargin = dp(12) })

        val section = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        title = label("Coffee near you", 18f, ink, true)
        count = label("", 13f, green, true)
        section.addView(title, LinearLayout.LayoutParams(0, wrap(), 1f))
        section.addView(count)
        content.addView(section, LinearLayout.LayoutParams(-1, wrap()).apply { topMargin = dp(16); bottomMargin = dp(9) })

        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(results, LinearLayout.LayoutParams(-1, wrap()))
        statePanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(27), dp(20), dp(24))
            background = bordered(Color.WHITE, Color.rgb(229, 237, 233), dp(16))
        }
        stateIcon = label("⌖", 30f, green, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = rounded(pale, dp(32))
        }
        stateTitle = label("Finding places nearby…", 17f, ink, true).apply { gravity = Gravity.CENTER }
        stateDescription = label("Using your location to find nearby matches.", 13f, muted).apply { gravity = Gravity.CENTER }
        loading = ProgressBar(this).apply { indeterminateTintList = android.content.res.ColorStateList.valueOf(green) }
        stateAction = label("", 14f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(11), dp(18), dp(11))
            background = rounded(green, dp(24))
            isClickable = true; isFocusable = true
            visibility = View.GONE
        }
        statePanel.addView(stateIcon)
        statePanel.addView(stateTitle, LinearLayout.LayoutParams(-1, wrap()).apply { topMargin = dp(13) })
        statePanel.addView(stateDescription, LinearLayout.LayoutParams(-1, wrap()).apply { topMargin = dp(5) })
        statePanel.addView(loading, LinearLayout.LayoutParams(wrap(), wrap()).apply { topMargin = dp(14) })
        statePanel.addView(stateAction, LinearLayout.LayoutParams(-1, wrap()).apply { topMargin = dp(16) })
        content.addView(statePanel, LinearLayout.LayoutParams(-1, wrap()))

        content.addView(label("Distances are measured from your location.", 12f, muted), LinearLayout.LayoutParams(-1, wrap()).apply { topMargin = dp(12) })
        content.addView(label("© OpenStreetMap contributors", 11f, muted).apply {
            setPadding(0, dp(3), 0, dp(5))
            isClickable = true
            setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.openstreetmap.org/copyright"))) }
        })
        return root
    }

    private fun chooseCategory(name: String) {
        selectedCategory = name
        suppressTextListener = true
        search.text?.clear()
        suppressTextListener = false
        currentQuery = ""
        updateClearVisibility()
        updateChips()
        title.text = "$name near you"
        if (hasLocationPermission()) queryCurrentSelection() else showLocationPermissionState()
    }

    private fun openCategories() {
        startActivityForResult(Intent(this, NearbyCategoriesActivity::class.java), REQUEST_CATEGORIES)
    }

    @Deprecated("Deprecated in Android")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CATEGORIES && resultCode == Activity.RESULT_OK) {
            val category = data?.getStringExtra(NearbyCategoriesActivity.EXTRA_CATEGORY) ?: return
            chooseCategory(category)
        }
    }

    private fun updateChips() {
        if (!::chips.isInitialized) return
        val quick = when (selectedCategory) {
            "Cafe" -> "Coffee"
            "Fuel station" -> "Gas"
            "Restaurant", "Fast food", "Food court" -> "Food"
            else -> selectedCategory
        }
        for (index in 0 until chips.childCount) {
            val chip = chips.getChildAt(index) as TextView
            val name = listOf("Coffee", "Food", "Gas", "More")[index]
            val selected = quick == name
            chip.setTextColor(if (selected) Color.WHITE else muted)
            chip.background = rounded(if (selected) green else Color.WHITE, dp(24))
        }
    }

    private fun updateClearVisibility() {
        if (::clearSearch.isInitialized) clearSearch.visibility = if (currentQuery.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun runSearchNow() {
        searchTask?.let(main::removeCallbacks)
        currentQuery = search.text?.toString()?.trim().orEmpty()
        if (currentQuery.isBlank()) queryCurrentSelection() else if (hasLocationPermission()) requestPlaces(currentQuery) else showLocationPermissionState()
    }

    private fun queryCurrentSelection() {
        val query = currentQuery
        title.text = "$selectedCategory near you"
        if (!hasLocationPermission()) { showLocationPermissionState(); return }
        showState("Finding places nearby…", "Using your location to find nearby matches.", showLoading = true)
        val version = ++requestVersion
        try {
            fused.lastLocation.addOnSuccessListener(this) { location ->
                if (version != requestVersion) return@addOnSuccessListener
                if (location != null) {
                    lastLocation = location
                    requestPlaces(query, version)
                } else {
                    val cancellation = CancellationTokenSource()
                    fused.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancellation.token)
                        .addOnSuccessListener(this) { current ->
                            if (version != requestVersion) return@addOnSuccessListener
                            if (current == null) showError("Couldn’t get your location", "Check that Location is turned on, then try again.")
                            else { lastLocation = current; requestPlaces(query, version) }
                        }
                        .addOnFailureListener { if (version == requestVersion) showError("Couldn’t get your location", "Check that Location is turned on, then try again.") }
                }
            }.addOnFailureListener { if (version == requestVersion) showError("Couldn’t get your location", "Check that Location is turned on, then try again.") }
        } catch (_: SecurityException) { showLocationPermissionState() }
    }

    private fun requestPlaces(query: String, inheritedVersion: Int? = null) {
        val location = lastLocation
        if (location == null) { queryCurrentSelection(); return }
        val version = inheritedVersion ?: ++requestVersion
        synchronized(networkLock) { activeConnection?.disconnect(); activeConnection = null }
        title.text = if (query.isBlank()) "$selectedCategory near you" else "Search results"
        count.text = ""
        showState(if (query.isBlank()) "Finding places nearby…" else "Searching nearby…", "", showLoading = true)
        val ql = buildQuery(location.latitude, location.longitude, query)
        worker.execute {
            try {
                val places = fetchPlaces(ql, location)
                runOnUiThread {
                    if (version != requestVersion || isFinishing || isDestroyed) return@runOnUiThread
                    showPlaces(places, query)
                }
            } catch (error: Exception) {
                runOnUiThread {
                    if (version != requestVersion || isFinishing || isDestroyed) return@runOnUiThread
                    val detail = when (error) {
                        is java.net.SocketTimeoutException -> "The places service took too long to respond. Try again."
                        else -> "Check your internet connection and try again."
                    }
                    showError("Couldn’t load nearby places", detail)
                }
            }
        }
    }

    private fun buildQuery(latitude: Double, longitude: Double, query: String): String {
        val point = "around:$SEARCH_RADIUS_METERS,$latitude,$longitude"
        if (query.isNotBlank()) {
            val safeTerm = query.replace(Regex("[^\\p{L}\\p{N} ]"), " ").trim().take(70)
            val escaped = safeTerm.replace("\\", "").replace("\"", "")
            if (escaped.isBlank()) return "[out:json][timeout:20];nwr($point)[name];out center tags 40;"
            return "[out:json][timeout:20];nwr($point)[name~\"$escaped\",i];out center tags 40;"
        }
        val filters = categoryFilters(selectedCategory)
        val selectors = filters.joinToString("") { "nwr($point)$it;" }
        return "[out:json][timeout:20];($selectors);out center tags 40;"
    }

    private fun categoryFilters(category: String): List<String> = when (category) {
        "Coffee" -> NearbyCategoryCatalog.categoryForQuickChip(category)!!.filters
        "Food" -> NearbyCategoryCatalog.categoryForQuickChip(category)!!.filters
        "Gas" -> NearbyCategoryCatalog.categoryForQuickChip(category)!!.filters
        else -> NearbyCategoryCatalog.all.firstOrNull { it.name == category }?.filters ?: listOf("[name~\"${category.replace(Regex("[^\\p{L}\\p{N} ]"), " ")}\",i]")
    }

    private fun fetchPlaces(query: String, origin: Location): List<NearbyPlace> {
        val connection = URL(OVERPASS_ENDPOINT).openConnection() as HttpURLConnection
        synchronized(networkLock) { activeConnection = connection }
        connection.requestMethod = "POST"
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "TrackerApp-Nearby/1.0")
        try {
            val body = "data=" + URLEncoder.encode(query, Charsets.UTF_8.name())
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            if (status !in 200..299) throw IllegalStateException("Places service returned HTTP $status")
            val json = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val elements = JSONObject(json).optJSONArray("elements") ?: return emptyList()
            val places = ArrayList<NearbyPlace>()
            for (index in 0 until elements.length()) {
                val item = elements.optJSONObject(index) ?: continue
                val tags = item.optJSONObject("tags") ?: continue
                val name = tags.optString("name").trim().takeIf { it.isNotEmpty() } ?: continue
                val center = item.optJSONObject("center")
                val lat = if (item.has("lat")) item.optDouble("lat", Double.NaN) else center?.optDouble("lat", Double.NaN) ?: Double.NaN
                val lon = if (item.has("lon")) item.optDouble("lon", Double.NaN) else center?.optDouble("lon", Double.NaN) ?: Double.NaN
                if (!lat.isFinite() || !lon.isFinite()) continue
                val location = Location("osm").apply { latitude = lat; longitude = lon }
                val distance = origin.distanceTo(location)
                val type = describeType(tags)
                val street = listOf(tags.optString("addr:housenumber"), tags.optString("addr:street"))
                    .filter { it.isNotBlank() }.joinToString(" ")
                val address = listOf(street, tags.optString("addr:suburb"), tags.optString("addr:city"))
                    .filter { it.isNotBlank() }.distinct().joinToString(", ")
                    .ifBlank { tags.optString("addr:full").ifBlank { type } }
                places.add(NearbyPlace("${item.optString("type")}_${item.optLong("id")}", name, type, address, lat, lon, distance))
            }
            return places.distinctBy { it.id }.sortedBy { it.distanceMeters }.take(MAX_RESULTS)
        } finally {
            connection.disconnect()
            synchronized(networkLock) { if (activeConnection === connection) activeConnection = null }
        }
    }

    private fun describeType(tags: JSONObject): String {
        val tag = listOf("amenity", "shop", "leisure", "tourism", "railway", "aeroway", "craft").firstOrNull { tags.has(it) }
        val raw = tag?.let { tags.optString(it) }.orEmpty()
        return raw.replace('_', ' ').replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            .ifBlank { "Place" }
    }

    private fun showPlaces(places: List<NearbyPlace>, query: String) {
        results.removeAllViews()
        loading.visibility = View.GONE
        statePanel.visibility = if (places.isEmpty()) View.VISIBLE else View.GONE
        count.text = "${places.size} ${if (places.size == 1) "place" else "places"} nearby"
        title.text = if (query.isBlank()) "$selectedCategory near you" else "Search results"
        if (places.isEmpty()) {
            stateIcon.text = "⌕"
            stateTitle.text = if (query.isBlank()) "No places found" else "No places found"
            stateDescription.text = if (query.isBlank()) "No nearby ${categorySearchName(selectedCategory)} were found. Try another category."
                else "No nearby matches for “$query”. Try another name or place type."
            stateAction.text = "Clear search"
            stateAction.visibility = if (query.isBlank()) View.GONE else View.VISIBLE
            stateAction.setOnClickListener {
                suppressTextListener = true; search.text?.clear(); suppressTextListener = false
                currentQuery = ""; updateClearVisibility(); queryCurrentSelection()
            }
            return
        }
        places.forEach { place -> results.addView(placeCard(place), LinearLayout.LayoutParams(-1, wrap()).apply { bottomMargin = dp(8) }) }
    }

    private fun placeCard(place: NearbyPlace): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(11), dp(10), dp(10), dp(10))
        background = bordered(Color.WHITE, Color.rgb(229, 237, 233), dp(15))
        val glyph = TextView(this@NearbyActivity).apply {
            text = NearbyCategoryCatalog.iconFor(selectedCategory).takeIf { selectedCategory in listOf("Coffee", "Food", "Gas") }
                ?: NearbyCategoryCatalog.iconFor(place.kind)
            textSize = 23f; gravity = Gravity.CENTER
            background = rounded(pale, dp(12))
        }
        addView(glyph, LinearLayout.LayoutParams(dp(52), dp(58)).apply { marginEnd = dp(10) })
        val details = LinearLayout(this@NearbyActivity).apply { orientation = LinearLayout.VERTICAL }
        details.addView(label(place.name, 15f, ink, true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
        details.addView(label("${place.kind}  ·  ${formatDistance(place.distanceMeters)}", 12f, muted), LinearLayout.LayoutParams(-1, wrap()).apply { topMargin = dp(3) })
        details.addView(label(place.address, 12f, green, true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }, LinearLayout.LayoutParams(-1, wrap()).apply { topMargin = dp(3) })
        addView(details, LinearLayout.LayoutParams(0, wrap(), 1f))
        val direction = label("➤", 20f, green, true).apply {
            gravity = Gravity.CENTER
            background = rounded(pale, dp(12))
            isClickable = true; isFocusable = true
            contentDescription = "Directions to ${place.name}"
            setOnClickListener { openDirections(place) }
        }
        addView(direction, LinearLayout.LayoutParams(dp(42), dp(42)).apply { marginStart = dp(8) })
        setOnClickListener { openDirections(place) }
    }

    private fun openDirections(place: NearbyPlace) {
        val navigation = Uri.parse("google.navigation:q=${place.latitude},${place.longitude}")
        val mapsIntent = Intent(Intent.ACTION_VIEW, navigation).setPackage("com.google.android.apps.maps")
        try {
            if (mapsIntent.resolveActivity(packageManager) != null) startActivity(mapsIntent)
            else startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.openstreetmap.org/directions?to=${place.latitude}%2C${place.longitude}")))
        } catch (_: Exception) {
            Toast.makeText(this, "No maps app is available for directions.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun formatDistance(meters: Float): String = if (meters < 1000) "${meters.roundToInt()} m" else String.format(Locale.getDefault(), "%.1f km", meters / 1000f)
    private fun categorySearchName(category: String) = when (category) { "Coffee" -> "cafes"; "Gas" -> "fuel stations"; else -> category.lowercase(Locale.getDefault()) }

    private fun showLocationPermissionState() {
        title.text = "$selectedCategory near you"
        count.text = ""
        results.removeAllViews()
        showState("Location needed", "Allow location while using the app to find and sort places near you.", showLoading = false)
        stateIcon.text = "⌖"
        stateAction.text = "Use my location"
        stateAction.visibility = View.VISIBLE
        stateAction.setOnClickListener {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), REQUEST_LOCATION)
        }
    }

    private fun showError(heading: String, message: String) {
        results.removeAllViews(); count.text = ""
        showState(heading, message, showLoading = false)
        stateIcon.text = "!"
        stateAction.text = "Try again"
        stateAction.visibility = View.VISIBLE
        stateAction.setOnClickListener { if (hasLocationPermission()) queryCurrentSelection() else showLocationPermissionState() }
    }

    private fun showState(heading: String, message: String, showLoading: Boolean) {
        results.removeAllViews()
        statePanel.visibility = View.VISIBLE
        stateIcon.visibility = View.VISIBLE
        stateTitle.text = heading
        stateDescription.text = message
        loading.visibility = if (showLoading) View.VISIBLE else View.GONE
        stateAction.visibility = View.GONE
    }

    private fun obtainLocationAndSearch() {
        if (currentQuery.length >= 2) requestPlaces(currentQuery) else queryCurrentSelection()
    }

    private fun hasLocationPermission() = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @Deprecated("Deprecated in Android")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LOCATION) {
            if (hasLocationPermission()) obtainLocationAndSearch()
            else showLocationPermissionState()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_CATEGORY, selectedCategory)
        outState.putString(STATE_QUERY, currentQuery)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        searchTask?.let(main::removeCallbacks)
        synchronized(networkLock) { activeConnection?.disconnect(); activeConnection = null }
        worker.shutdownNow()
        super.onDestroy()
    }

    private fun label(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color)
        if (bold) typeface = Typeface.create("sans-serif", Typeface.BOLD)
    }

    private fun rounded(color: Int, radiusDp: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radiusDp).toFloat() }
    private fun bordered(color: Int, stroke: Int, radiusDp: Int) = rounded(color, radiusDp).apply { setStroke(dp(1), stroke) }

    private class SimpleTextWatcher(private val changed: () -> Unit) : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = changed()
        override fun afterTextChanged(s: android.text.Editable?) = Unit
    }

    companion object {
        private const val REQUEST_LOCATION = 7101
        private const val REQUEST_CATEGORIES = 7102
        private const val STATE_CATEGORY = "nearby_category"
        private const val STATE_QUERY = "nearby_query"
        private const val SEARCH_RADIUS_METERS = 5000
        private const val MAX_RESULTS = 30
        private const val SEARCH_DEBOUNCE_MS = 600L
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 25_000
        private const val OVERPASS_ENDPOINT = "https://overpass-api.de/api/interpreter"
        private val networkLock = Any()
    }
}
