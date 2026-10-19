package com.mobile.trackerapp.ui.tools

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.ColorDrawable
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
import android.content.res.ColorStateList
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.mobile.trackerapp.R
import com.mobile.trackerapp.BuildConfig
import com.mobile.trackerapp.ads.AdRemoteConfig
import com.mobile.trackerapp.ads.AdsManager
import com.mobile.trackerapp.ads.banner_all
import com.mobile.trackerapp.databinding.ActivityNearbyBinding
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
    val distanceMeters: Float,
    val imageUrl: String?,
    val rating: String?,
    val reviewCount: String?,
    val openingHours: String?
)

/** Nearby search backed by live OpenStreetMap data, with routes opened in the installed maps app. */
class NearbyActivity : DashboardActivity() {

    private lateinit var binding: ActivityNearbyBinding
    private val ink = Color.rgb(17, 24, 39)
    private val muted = Color.rgb(126, 137, 153)
    private val green = Color.rgb(0, 164, 76)
    private val pale = Color.rgb(237, 249, 244)
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private lateinit var search: EditText
    private lateinit var clearSearch: ImageView
    private lateinit var chips: LinearLayout
    private lateinit var title: TextView
    private lateinit var count: TextView
    private lateinit var results: LinearLayout
    private lateinit var statePanel: LinearLayout
    private lateinit var stateIcon: ImageView
    private lateinit var stateTitle: TextView
    private lateinit var stateDescription: TextView
    private lateinit var stateAction: TextView
    private lateinit var loading: ProgressBar
    private lateinit var nearbyRoot: LinearLayout
    private var selectedCategory = "Coffee"
    private var lastLocation: Location? = null
    private var requestVersion = 0
    private var searchTask: Runnable? = null
    private var suppressTextListener = false
    private var currentQuery = ""
    private var showAllResults = false
    private var latestPlaces: List<NearbyPlace> = emptyList()
    @Volatile private var activeConnection: HttpURLConnection? = null

    private val categoriesLauncher = 7102

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        restoreNearbyWindow()
        selectedCategory = state?.getString(STATE_CATEGORY) ?: "Coffee"
        currentQuery = state?.getString(STATE_QUERY).orEmpty()
        binding = ActivityNearbyBinding.inflate(layoutInflater)
        setContentView(binding.root)
        AdsManager.loadInterHome(this, ignoreLimit = true)
        binding.nearbyBack.setOnClickListener { onBackPressed() }
        loadNearByBanner()
        nearbyRoot = binding.root
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val statusBarInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.setPadding(0, statusBarInset, 0, 0)
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
        search=binding.nearbySearch; clearSearch=binding.nearbyClear; chips=binding.nearbyChips; title=binding.nearbyTitle; count=binding.nearbyCount; results=binding.nearbyResults; statePanel=binding.nearbyStatePanel; stateIcon=binding.nearbyStateIcon; stateTitle=binding.nearbyStateTitle; stateDescription=binding.nearbyStateDescription; stateAction=binding.nearbyStateAction; loading=binding.nearbyLoading
        listOf("Coffee","Food","Gas","More").forEachIndexed { index, name -> chips.getChildAt(index).setOnClickListener { if(name=="More") openCategories() else chooseCategory(name) } }
        clearSearch.setOnClickListener { suppressTextListener=true; search.text?.clear(); suppressTextListener=false; currentQuery=""; updateClearVisibility(); if(hasLocationPermission()) queryCurrentSelection() }
        search.setOnEditorActionListener { _, action, _ -> if(action==EditorInfo.IME_ACTION_SEARCH){ runSearchNow(); true } else false }
        search.addTextChangedListener(SimpleTextWatcher { if(!suppressTextListener){ currentQuery=search.text?.toString()?.trim().orEmpty(); updateClearVisibility(); searchTask?.let(main::removeCallbacks); if(currentQuery.length>=2) searchTask=Runnable { if(hasLocationPermission()) requestPlaces(currentQuery) else showLocationPermissionState() }.also { main.postDelayed(it, SEARCH_DEBOUNCE_MS) } else if(currentQuery.isEmpty()&&hasLocationPermission()) searchTask=Runnable { queryCurrentSelection() }.also { main.postDelayed(it, SEARCH_DEBOUNCE_MS) } } })
        suppressTextListener = true
        search.setText(currentQuery)
        suppressTextListener = false
        updateChips()
        if (hasLocationPermission()) obtainLocationAndSearch() else showLocationPermissionState()
    }

    override fun onResume() {
        super.onResume()
        // Restore the activity window after returning from an external maps app.
        restoreNearbyWindow()
        if (::nearbyRoot.isInitialized) ViewCompat.requestApplyInsets(nearbyRoot)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            // Maps can temporarily replace the system-bar controller. Re-apply it
            // after the Nearby surface is visible again, not while Maps is open.
            restoreNearbyWindow()
            if (::nearbyRoot.isInitialized) ViewCompat.requestApplyInsets(nearbyRoot)
        }
    }

    private fun restoreNearbyWindow() {
        window.statusBarColor = Color.rgb(246, 251, 248)
        window.navigationBarColor = Color.rgb(246, 251, 248)
        window.setBackgroundDrawable(ColorDrawable(Color.rgb(246, 251, 248)))
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideNavigationBar()
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
    override fun onBackPressed() {
        AdsManager.showInterHomeBack(this) {
            finish()
        }
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
            chip.compoundDrawableTintList = ColorStateList.valueOf(if (selected) Color.WHITE else muted)
            chip.background = if (selected) rounded(green, 100) else bordered(Color.WHITE, Color.rgb(221, 229, 225), 100)
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
        val collected = LinkedHashMap<String, NearbyPlace>()
        // Photon is the quickest text-search provider and supports city/place names
        // directly. Try it first for typed searches instead of waiting through the
        // slower category/Overpass fallbacks.
        if (query.isNotBlank()) {
            runCatching { fetchPhotonPlaces(origin, query) }
                .getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        if (BuildConfig.GOOGLE_PLACES_API_KEY.isNotBlank()) {
            runCatching { fetchGooglePlaces(origin, query) }.getOrNull().orEmpty()
                .forEach { collected[placeKey(it)] = it }
            if (collected.size >= INITIAL_RESULT_COUNT) {
                return collected.values.sortedBy { it.distanceMeters }.take(MAX_RESULTS)
            }
        }
        var lastError: Exception? = null
        for (endpoint in OVERPASS_ENDPOINTS) {
            val connection = URL(endpoint).openConnection() as HttpURLConnection
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
            val elements = JSONObject(json).optJSONArray("elements") ?: continue
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
                places.add(NearbyPlace(
                    "${item.optString("type")}_${item.optLong("id")}", name, type, address, lat, lon, distance,
                    tags.optString("image").takeIf { it.startsWith("http") }
                        ?: tags.optString("wikimedia_commons").takeIf { it.startsWith("http") },
                    null, null, null
                ))
            }
                places.distinctBy { it.id }
                    .filter { it.distanceMeters <= SEARCH_RADIUS_METERS }
                    .forEach { collected[placeKey(it)] = it }
                if (collected.size >= INITIAL_RESULT_COUNT) {
                    return collected.values.sortedBy { it.distanceMeters }.take(MAX_RESULTS)
                }
            } catch (error: Exception) {
                lastError = error
            } finally {
                connection.disconnect()
                synchronized(networkLock) { if (activeConnection === connection) activeConnection = null }
            }
        }
        if (collected.isNotEmpty()) return collected.values.sortedBy { it.distanceMeters }.take(MAX_RESULTS)
        runCatching { fetchPhotonPlaces(origin, query.ifBlank { categorySearchName(selectedCategory) }) }
            .getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }
        throw lastError ?: IllegalStateException("Nearby places are unavailable")
    }

    private fun placeKey(place: NearbyPlace): String =
        "${place.name.trim().lowercase(Locale.getDefault())}|${String.format(Locale.US, "%.3f", place.latitude)}|${String.format(Locale.US, "%.3f", place.longitude)}"

    private fun fetchGooglePlaces(origin: Location, query: String): List<NearbyPlace> {
        val includedType = when (selectedCategory) {
            "Coffee" -> "cafe"
            "Food" -> "restaurant"
            "Gas" -> "gas_station"
            else -> null
        }
        val body = JSONObject().apply {
            put("maxResultCount", MAX_RESULTS)
            put("locationRestriction", JSONObject().put("circle", JSONObject().apply {
                put("center", JSONObject().put("latitude", origin.latitude).put("longitude", origin.longitude))
                put("radius", SEARCH_RADIUS_METERS.toDouble())
            }))
            includedType?.let { put("includedTypes", org.json.JSONArray().put(it)) }
            if (query.isNotBlank() && includedType == null) put("textQuery", query)
        }
        val connection = URL("https://places.googleapis.com/v1/places:searchNearby").openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("X-Goog-Api-Key", BuildConfig.GOOGLE_PLACES_API_KEY)
            connection.setRequestProperty("X-Goog-FieldMask", "places.id,places.displayName,places.location,places.primaryType,places.formattedAddress,places.rating,places.userRatingCount,places.currentOpeningHours.openNow,places.photos")
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            if (connection.responseCode !in 200..299) throw IllegalStateException("Google Places returned HTTP ${connection.responseCode}")
            val places = JSONObject(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }).optJSONArray("places") ?: return emptyList()
            buildList {
                for (index in 0 until places.length()) {
                    val item = places.optJSONObject(index) ?: continue
                    val location = item.optJSONObject("location") ?: continue
                    val lat = location.optDouble("latitude", Double.NaN)
                    val lon = location.optDouble("longitude", Double.NaN)
                    val name = item.optJSONObject("displayName")?.optString("text").orEmpty()
                    if (name.isBlank()) continue
                    if (!lat.isFinite() || !lon.isFinite()) continue
                    val point = Location("google").apply { latitude = lat; longitude = lon }
                    val photoName = item.optJSONArray("photos")?.optJSONObject(0)?.optString("name").orEmpty()
                    add(NearbyPlace(
                        item.optString("id"), name, item.optString("primaryType").replace('_', ' ').ifBlank { selectedCategory },
                        item.optString("formattedAddress").ifBlank { selectedCategory }, lat, lon, origin.distanceTo(point),
                        photoName.takeIf { it.isNotBlank() }?.let { "https://places.googleapis.com/v1/$it/media?maxWidthPx=160&key=${BuildConfig.GOOGLE_PLACES_API_KEY}" },
                        item.optDouble("rating", Double.NaN).takeIf { it.isFinite() }?.let { String.format(Locale.getDefault(), "%.1f", it) },
                        item.optInt("userRatingCount", 0).takeIf { it > 0 }?.toString(),
                        if (item.optJSONObject("currentOpeningHours")?.has("openNow") == true) if (item.optJSONObject("currentOpeningHours")?.optBoolean("openNow") == true) "Open now" else "Closed" else null
                    ))
                }
            }.filter { it.distanceMeters <= SEARCH_RADIUS_METERS }.sortedBy { it.distanceMeters }.take(MAX_RESULTS)
        } finally { connection.disconnect() }
    }

    private fun fetchPhotonPlaces(origin: Location, searchTerm: String): List<NearbyPlace> {
        val endpoint = URL("https://photon.komoot.io/api/?q=${URLEncoder.encode(searchTerm, Charsets.UTF_8.name())}&lat=${origin.latitude}&lon=${origin.longitude}&limit=$MAX_RESULTS")
        val connection = endpoint.openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "TrackerApp-Nearby/1.0")
            val features = JSONObject(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }).optJSONArray("features") ?: return emptyList()
            buildList {
                for (index in 0 until features.length()) {
                    val feature = features.optJSONObject(index) ?: continue
                    val geometry = feature.optJSONObject("geometry")?.optJSONArray("coordinates") ?: continue
                    if (geometry.length() < 2) continue
                    val lon = geometry.optDouble(0, Double.NaN)
                    val lat = geometry.optDouble(1, Double.NaN)
                    if (!lat.isFinite() || !lon.isFinite()) continue
                    val properties = feature.optJSONObject("properties") ?: continue
                    val name = properties.optString("name").trim().takeIf { it.isNotBlank() } ?: continue
                    val location = Location("photon").apply { latitude = lat; longitude = lon }
                    val type = properties.optString("type").replace('_', ' ').replaceFirstChar { it.uppercase() }.ifBlank { selectedCategory }
                    val address = listOf(properties.optString("street"), properties.optString("city"))
                        .filter { it.isNotBlank() }.joinToString(", ").ifBlank { type }
                    add(NearbyPlace("photon_$index", name, type, address, lat, lon, origin.distanceTo(location), null, null, null, null))
                }
            }.filter { it.distanceMeters <= SEARCH_RADIUS_METERS }.sortedBy { it.distanceMeters }.take(MAX_RESULTS)
        } finally {
            connection.disconnect()
        }
    }

    private fun describeType(tags: JSONObject): String {
        val tag = listOf("amenity", "shop", "leisure", "tourism", "railway", "aeroway", "craft").firstOrNull { tags.has(it) }
        val raw = tag?.let { tags.optString(it) }.orEmpty()
        return raw.replace('_', ' ').replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            .ifBlank { "Place" }
    }

    private fun showPlaces(places: List<NearbyPlace>, query: String) {
        latestPlaces = places
        showAllResults = false
        results.removeAllViews()
        loading.visibility = View.GONE
        statePanel.visibility = if (places.isEmpty()) View.VISIBLE else View.GONE
        count.text = if (query.isBlank() && places.isNotEmpty()) "View all (${places.size})" else "${places.size} ${if (places.size == 1) "place" else "places"} nearby"
        count.setTextColor(if (places.isEmpty()) muted else green)
        count.isClickable = query.isBlank() && places.size > INITIAL_RESULT_COUNT
        count.setOnClickListener { if (count.isClickable) { showAllResults = true; renderPlaces() } }
        title.text = if (query.isBlank()) "$selectedCategory near you" else "Search results"
        if (places.isEmpty()) {
            stateIcon.setImageResource(R.drawable.ic_area_no_result)
            stateIcon.visibility = View.VISIBLE
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
        renderPlaces()
    }

    private fun renderPlaces() {
        results.removeAllViews()
        val visiblePlaces = if (showAllResults) latestPlaces else latestPlaces.take(INITIAL_RESULT_COUNT)
        visiblePlaces.forEach { place ->
            results.addView(placeCard(place), LinearLayout.LayoutParams(-1, wrap()).apply { bottomMargin = dp(8) })
        }
    }
    private fun loadNearByBanner() {
        val bannerConfig = AdRemoteConfig.banner_all
        AdsManager.loadBanner(
            this, bannerConfig, binding.frBanner, isCollapse = false
        )
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
        val photo = ImageView(this@NearbyActivity).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(pale, dp(12))
            contentDescription = "Photo of ${place.name}"
        }
        val thumbnail = FrameLayout(this@NearbyActivity).apply {
            addView(photo, FrameLayout.LayoutParams(-1, -1))
            addView(glyph, FrameLayout.LayoutParams(-1, -1))
        }
        addView(thumbnail, LinearLayout.LayoutParams(dp(52), dp(60)).apply { marginEnd = dp(10) })
        place.imageUrl?.let { imageUrl ->
            worker.execute {
                runCatching {
                    (URL(imageUrl).openConnection() as HttpURLConnection).run {
                        connectTimeout = 5000; readTimeout = 8000
                        inputStream.use { BitmapFactory.decodeStream(it) }
                    }
                }.getOrNull()?.let { bitmap -> runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        photo.setImageBitmap(bitmap)
                        glyph.visibility = View.GONE
                    }
                } }
            }
        }
        val details = LinearLayout(this@NearbyActivity).apply { orientation = LinearLayout.VERTICAL }
        details.addView(label(place.name, 15f, ink, true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
        val rating = place.rating?.let { "★ $it${place.reviewCount?.let { count -> " ($count)" } ?: ""}" } ?: "Rating unavailable"
        val status = when {
            place.openingHours == "24/7" -> "Open now"
            place.openingHours != null -> "Hours listed"
            else -> "Hours unavailable"
        }
        if (place.rating != null || place.openingHours != null) {
            details.addView(label("$rating  ·  ${place.kind}", 12f, muted), LinearLayout.LayoutParams(-1, wrap()).apply { topMargin = dp(3) })
            details.addView(label("$status  ·  ${formatDistance(place.distanceMeters)}  ·  ${walkingTime(place.distanceMeters)} min walk", 12f, green, true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }, LinearLayout.LayoutParams(-1, wrap()).apply { topMargin = dp(3) })
        } else {
            details.addView(label("${place.kind}  ·  ${formatDistance(place.distanceMeters)}  ·  ${walkingTime(place.distanceMeters)} min walk", 12f, muted), LinearLayout.LayoutParams(-1, wrap()).apply { topMargin = dp(3) })
        }
        addView(details, LinearLayout.LayoutParams(0, wrap(), 1f))
        val direction = ImageView(this@NearbyActivity).apply {
            setImageResource(R.drawable.ic_nearby_direction)
            setPadding(dp(9), dp(9), dp(9), dp(9))
            background = rounded(pale, dp(12))
            isClickable = true; isFocusable = true
            contentDescription = "Directions to ${place.name}"
            setOnClickListener { openDirections(place) }
        }
        addView(direction, LinearLayout.LayoutParams(dp(42), dp(42)).apply { marginStart = dp(8) })
        setOnClickListener { openDirections(place) }
    }

    private fun openDirections(place: NearbyPlace) {
        val search = Uri.encode("${place.name}, ${place.latitude}, ${place.longitude}")
        val location = Uri.parse("https://www.google.com/maps/search/?api=1&query=$search")
        try {
            val mapsIntent = Intent(Intent.ACTION_VIEW, location).setPackage("com.google.android.apps.maps")
            if (mapsIntent.resolveActivity(packageManager) != null) {
                startActivity(mapsIntent)
            } else {
                startActivity(Intent(Intent.ACTION_VIEW, location))
            }
        } catch (_: Exception) {
            Toast.makeText(this, "No maps app is available.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun formatDistance(meters: Float): String = if (meters < 1000) "${meters.roundToInt()} m" else String.format(Locale.getDefault(), "%.1f km", meters / 1000f)
    private fun walkingTime(meters: Float): Int = maxOf(1, (meters / 80f).roundToInt())
    private fun categorySearchName(category: String) = when (category) { "Coffee" -> "cafes"; "Gas" -> "fuel stations"; else -> category.lowercase(Locale.getDefault()) }

    private fun showLocationPermissionState() {
        title.text = "$selectedCategory near you"
        count.text = ""
        results.removeAllViews()
        showState("Location needed", "Allow location while using the app to find and sort places near you.", showLoading = false)
        stateIcon.setImageResource(R.drawable.ic_location_focus_green)
        stateAction.text = "Use my location"
        stateAction.visibility = View.VISIBLE
        stateAction.setOnClickListener {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), REQUEST_LOCATION)
        }
    }

    private fun showError(heading: String, message: String) {
        results.removeAllViews(); count.text = ""
        showState(heading, message, showLoading = false)
        stateIcon.setImageResource(R.drawable.ic_phone_info)
        stateAction.text = "Try again"
        stateAction.visibility = View.VISIBLE
        stateAction.setOnClickListener { if (hasLocationPermission()) queryCurrentSelection() else showLocationPermissionState() }
    }

    private fun showState(heading: String, message: String, showLoading: Boolean) {
        results.removeAllViews()
        statePanel.visibility = View.VISIBLE
        stateIcon.visibility = if (showLoading) View.GONE else View.VISIBLE
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
    private fun bordered(color: Int, stroke: Int, radiusDp: Int) = rounded(color, radiusDp).apply { setStroke(dp(0.6f), stroke) }

    private fun dp(value: Float) = (value * resources.displayMetrics.density).roundToInt().coerceAtLeast(1)

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
        private const val INITIAL_RESULT_COUNT = 2
        private const val SEARCH_DEBOUNCE_MS = 300L
        private const val CONNECT_TIMEOUT_MS = 6_000
        private const val READ_TIMEOUT_MS = 12_000
        private val OVERPASS_ENDPOINTS = listOf(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter",
            "https://overpass.private.coffee/api/interpreter"
        )
        private val networkLock = Any()
    }
}
