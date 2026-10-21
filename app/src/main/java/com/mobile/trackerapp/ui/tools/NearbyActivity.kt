package com.mobile.trackerapp.ui.tools

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.location.Location
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.mobile.trackerapp.BuildConfig
import com.mobile.trackerapp.R
import com.mobile.trackerapp.ads.AdRemoteConfig
import com.mobile.trackerapp.ads.AdsManager
import com.mobile.trackerapp.ads.banner_all
import com.mobile.trackerapp.databinding.ActivityNearbyBinding
import com.mobile.trackerapp.ui.DashboardActivity
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

internal class GooglePlacesHttpException(
    val statusCode: Int,
    val apiMessage: String
) : IOException("Google Places HTTP $statusCode: $apiMessage")

private class NearbyConfigurationException(message: String) : IOException(message)
private class NearbyLocationException(message: String) : IOException(message)

/** Nearby search backed by Google Places API (New), with an isolated optional OSM fallback. */
class NearbyActivity : DashboardActivity() {

    private lateinit var binding: ActivityNearbyBinding
    private val muted = Color.rgb(126, 137, 153)
    private val green = Color.rgb(0, 164, 76)
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private val cache = NearbyPlaceCache()
    private lateinit var search: EditText
    private lateinit var clearSearch: ImageView
    private lateinit var chips: LinearLayout
    private lateinit var title: TextView
    private lateinit var count: TextView
    private lateinit var results: RecyclerView
    private lateinit var statePanel: LinearLayout
    private lateinit var stateIcon: ImageView
    private lateinit var stateTitle: TextView
    private lateinit var stateDescription: TextView
    private lateinit var stateAction: TextView
    private lateinit var loading: ProgressBar
    private lateinit var nearbyRoot: LinearLayout
    private lateinit var adapter: NearbyPlaceAdapter
    private var selectedCategory = "Coffee"
    private var selectedRadiusKm = 5
    private var lastLocation: Location? = null
    private var searchJob: Job? = null
    private var debounceJob: Job? = null
    private val searchGeneration = NearbySearchGeneration()
    private var suppressTextListener = false
    private var currentQuery = ""
    private var showAllResults = false
    private var latestPlaces: List<NearbyPlace> = emptyList()

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        restoreNearbyWindow()
        selectedCategory = state?.getString(STATE_CATEGORY) ?: "Coffee"
        currentQuery = state?.getString(STATE_QUERY).orEmpty()
        if (currentQuery.isNotBlank()) selectedCategory = ""
        selectedRadiusKm = DEFAULT_RADIUS_KM
        binding = ActivityNearbyBinding.inflate(layoutInflater)
        setContentView(binding.root)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                AdsManager.showInterHomeBack(this@NearbyActivity) { finish() }
            }
        })
        AdsManager.loadInterHome(this, ignoreLimit = true)
        binding.nearbyBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        loadNearByBanner()
        nearbyRoot = binding.root
        search = binding.nearbySearch
        clearSearch = binding.nearbyClear
        chips = binding.nearbyChips
        title = binding.nearbyTitle
        count = binding.nearbyCount
        results = binding.nearbyResults
        statePanel = binding.nearbyStatePanel
        stateIcon = binding.nearbyStateIcon
        stateTitle = binding.nearbyStateTitle
        stateDescription = binding.nearbyStateDescription
        stateAction = binding.nearbyStateAction
        loading = binding.nearbyLoading
        adapter = NearbyPlaceAdapter(this, ::openDirections)
        results.layoutManager = LinearLayoutManager(this)
        results.adapter = adapter
        results.setHasFixedSize(false)
        results.isNestedScrollingEnabled = false

        listOf("Coffee", "Food", "Gas", "More").forEachIndexed { index, name ->
            chips.getChildAt(index).setOnClickListener {
                if (name == "More") openCategories() else chooseCategory(name)
            }
        }
        clearSearch.setOnClickListener {
            suppressTextListener = true
            search.text?.clear()
            suppressTextListener = false
            currentQuery = ""
            selectedCategory = "Coffee"
            updateClearVisibility()
            updateChips()
            if (hasLocationPermission()) queryCurrentSelection()
        }
        search.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_SEARCH) {
                runSearchNow()
                true
            } else false
        }
        search.addTextChangedListener(SimpleTextWatcher {
            if (suppressTextListener) return@SimpleTextWatcher
            currentQuery = search.text?.toString()?.trim().orEmpty()
            // Free-text search and quick-category search are mutually
            // exclusive. Clear the category as soon as the user types.
            if (currentQuery.isNotBlank()) selectedCategory = ""
            else if (selectedCategory.isBlank()) selectedCategory = "Coffee"
            // Invalidate an in-flight category/text request immediately. Without
            // this, a previous request (for example Coffee) can finish during
            // the debounce window and briefly replace the newly entered query.
            searchJob?.cancel()
            searchGeneration.begin()
            clearRenderedResultsForNewSearch()
            updateClearVisibility()
            updateChips()
            debounceJob?.cancel()
            debounceJob = lifecycleScope.launch {
                delay(SEARCH_DEBOUNCE_MS)
                if (!hasLocationPermission()) showLocationPermissionState()
                else if (currentQuery.length >= 2) requestPlaces(currentQuery)
                else if (currentQuery.isEmpty()) queryCurrentSelection()
            }
        })
        suppressTextListener = true
        search.setText(currentQuery)
        suppressTextListener = false
        updateChips()
        if (hasLocationPermission()) obtainLocationAndSearch() else showLocationPermissionState()
    }

    override fun onResume() {
        super.onResume()
        restoreNearbyWindow()
        if (::nearbyRoot.isInitialized) ViewCompat.requestApplyInsets(nearbyRoot)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
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
        // Keep the same system-bar fitting behavior as the Compass screen.
        WindowCompat.setDecorFitsSystemWindows(window, true)
        hideNavigationBar()
    }

    private fun chooseCategory(name: String) {
        debounceJob?.cancel()
        searchJob?.cancel()
        selectedCategory = name
        suppressTextListener = true
        search.text?.clear()
        suppressTextListener = false
        currentQuery = ""
        search.clearFocus()
        (getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
            .hideSoftInputFromWindow(search.windowToken, 0)
        clearRenderedResultsForNewSearch()
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
            data?.getStringExtra(NearbyCategoriesActivity.EXTRA_CATEGORY)?.let(::chooseCategory)
        }
    }

    private fun updateChips() {
        if (!::chips.isInitialized) return
        val freeTextSearchActive = currentQuery.isNotBlank()
        val quick = when (selectedCategory) {
            "Cafe" -> "Coffee"
            "Fuel station" -> "Gas"
            "Restaurant", "Fast food", "Food court" -> "Food"
            else -> selectedCategory
        }
        for (index in 0 until chips.childCount) {
            val chip = chips.getChildAt(index) as TextView
            val name = listOf("Coffee", "Food", "Gas", "More")[index]
            val selected = !freeTextSearchActive && quick == name
            chip.isEnabled = true          // always tappable
            chip.alpha = 1f                // or 0.7f if you want a subtle dim during text search
            chip.setTextColor(if (selected) Color.WHITE else muted)
            chip.compoundDrawableTintList = ColorStateList.valueOf(if (selected) Color.WHITE else muted)
            chip.background = if (selected) rounded(green, 100) else bordered(Color.WHITE, Color.rgb(221, 229, 225), 100)
        }
    }

    private fun updateClearVisibility() {
        if (::clearSearch.isInitialized) clearSearch.visibility = if (currentQuery.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun runSearchNow() {
        debounceJob?.cancel()
        currentQuery = search.text?.toString()?.trim().orEmpty()
        if (currentQuery.isNotBlank()) selectedCategory = ""
        else if (selectedCategory.isBlank()) selectedCategory = "Coffee"
        clearRenderedResultsForNewSearch()
        updateChips()
        if (currentQuery.isBlank()) queryCurrentSelection()
        else if (hasLocationPermission()) requestPlaces(currentQuery)
        else showLocationPermissionState()
    }

    /** Starts a category/radius search immediately; only free-text input is debounced. */
    private fun queryCurrentSelection(queryOverride: String? = null) {
        if (!hasLocationPermission()) {
            showLocationPermissionState()
            return
        }
        // Keep the query that initiated this search. When location resolution is
        // still in progress, currentQuery can change before the request starts.
        val query = queryOverride ?: currentQuery
        val version = beginSearch()
        showState(getString(R.string.nearby_finding_places), getString(R.string.nearby_using_recent_location), showLoading = true)
        val started = SystemClock.elapsedRealtime()
        searchJob = lifecycleScope.launch {
            try {
                val location = resolveLocation()
                ensureCurrent(version)
                lastLocation = location
                Log.d(TAG, "search=$version stage=location ms=${SystemClock.elapsedRealtime() - started} source=validated")
                performSearch(query, location, version, started)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                handleSearchError(error, version, started)
            }
        }
    }

    private fun requestPlaces(query: String) {
        val location = lastLocation?.takeIf { NearbyLocationPolicy.isUsable(it) }
        if (location == null) {
            queryCurrentSelection(query)
            return
        }
        val version = beginSearch()
        val started = SystemClock.elapsedRealtime()
        searchJob = lifecycleScope.launch {
            try {
                Log.d(TAG, "search=$version stage=location ms=${SystemClock.elapsedRealtime() - started} source=validated_cache")
                performSearch(query, location, version, started)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                handleSearchError(error, version, started)
            }
        }
    }

    private fun beginSearch(): Int {
        debounceJob?.cancel()
        searchJob?.cancel()
        return searchGeneration.begin()
    }

    private suspend fun ensureCurrent(version: Int) {
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        check(searchGeneration.isCurrent(version)) { "stale nearby search" }
    }

    private fun isQueryCurrent(version: Int, query: String): Boolean =
        searchGeneration.isCurrent(version) && currentQuery == query.trim()

    private suspend fun resolveLocation(): Location {
        lastLocation?.takeIf { NearbyLocationPolicy.isUsable(it) }?.let { return it }
        val providerLocation = awaitLastLocation()
        if (NearbyLocationPolicy.isUsable(providerLocation)) return providerLocation!!
        val current = withTimeoutOrNull(LOCATION_TIMEOUT_MS) { awaitCurrentLocation() }
            ?: throw NearbyLocationException("Location lookup timed out.")
        if (!NearbyLocationPolicy.isUsable(current)) throw NearbyLocationException("A recent accurate location was not available.")
        return current
    }

    private suspend fun awaitLastLocation(): Location? = suspendCancellableCoroutine { continuation ->
        try {
            fused.lastLocation
                .addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
                .addOnFailureListener { if (continuation.isActive) continuation.resume(null) }
        } catch (error: SecurityException) {
            if (continuation.isActive) continuation.resumeWithException(error)
        }
    }

    private suspend fun awaitCurrentLocation(): Location? = suspendCancellableCoroutine { continuation ->
        val cancellation = CancellationTokenSource()
        continuation.invokeOnCancellation { cancellation.cancel() }
        try {
            fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellation.token)
                .addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
                .addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
        } catch (error: SecurityException) {
            if (continuation.isActive) continuation.resumeWithException(error)
        }
    }

    private suspend fun performSearch(query: String, origin: Location, version: Int, started: Long) {
        val textQuery = query.trim().takeIf { it.isNotEmpty() }
        val category = if (textQuery == null) selectedCategoryDefinition() else null
        if (!isQueryCurrent(version, query)) return
        title.text = if (textQuery == null) getString(R.string.nearby_category_near_you, selectedCategory) else getString(R.string.area_search_results)
        val key = NearbyCacheKey(category?.googleType, textQuery?.lowercase(Locale.ROOT), selectedRadiusKm, NearbyLocationBucket.from(origin))
        val cached = cache.get(key)
        if (cached != null) {
            ensureCurrent(version)
            if (!isQueryCurrent(version, query)) return
            val label = if (cached.isFresh) getString(R.string.nearby_cached_refreshing) else getString(R.string.nearby_old_cache_refreshing)
            Log.d(TAG, "search=$version stage=cache ageMs=${cached.ageMillis} status=${if (cached.isFresh) "fresh" else "stale"} count=${cached.places.size}")
            showPlaces(cached.places, textQuery.orEmpty(), label)
        } else {
            showState(if (textQuery == null) getString(R.string.nearby_finding_places) else getString(R.string.nearby_searching), "", showLoading = true)
        }

        val networkStarted = SystemClock.elapsedRealtime()
        val places = fetchPlaces(category, textQuery, origin, version)
        ensureCurrent(version)
        if (!isQueryCurrent(version, query)) return
        Log.d(TAG, "search=$version stage=network ms=${SystemClock.elapsedRealtime() - networkStarted} results=${places.size}")
        cache.put(key, places)
        showPlaces(places, textQuery.orEmpty(), null)
        Log.d(TAG, "search=$version stage=render ms=${SystemClock.elapsedRealtime() - started} results=${places.size}")
    }

    private suspend fun fetchPlaces(category: NearbyCategory?, textQuery: String?, origin: Location, version: Int): List<NearbyPlace> {
        if (BuildConfig.GOOGLE_PLACES_API_KEY.isBlank()) {
            throw NearbyConfigurationException("Google Places API key is missing from local.properties.")
        }
        return try {
            if (category != null) fetchGoogleNearbyPlaces(origin, category)
            else fetchGoogleTextPlaces(origin, textQuery ?: throw IllegalArgumentException("Search text is missing"))
        } catch (error: GooglePlacesHttpException) {
            Log.e(TAG, "search=$version Google Places HTTP ${error.statusCode}: ${error.apiMessage}")
            throw error
        } catch (error: IOException) {
            Log.e(TAG, "search=$version Google Places network failure: ${error.javaClass.simpleName}", error)
            if (!NearbyNetworkConfig.ENABLE_OVERPASS_FALLBACK || !isNetworkFailure(error)) throw error
            Log.w(TAG, "search=$version using isolated OpenStreetMap fallback")
            fetchOverpassFallback(category, textQuery, origin)
        }
    }

    private fun isNetworkFailure(error: IOException): Boolean =
        error !is GooglePlacesHttpException && error !is NearbyConfigurationException

    private suspend fun fetchGoogleNearbyPlaces(origin: Location, category: NearbyCategory): List<NearbyPlace> {
        val body = JSONObject().apply {
            put("maxResultCount", MAX_GOOGLE_RESULTS)
            put("rankPreference", "DISTANCE")
            put("locationRestriction", JSONObject().put("circle", JSONObject().apply {
                put("center", JSONObject().put("latitude", origin.latitude).put("longitude", origin.longitude))
                put("radius", searchRadiusMeters().toDouble())
            }))
            // Exactly one Table A type per category click. No Overpass query is ever sent here.
            put("includedTypes", JSONArray().put(category.googleType))
        }
        val requestStarted = SystemClock.elapsedRealtime()
        val request = googleRequest("https://places.googleapis.com/v1/places:searchNearby", body)
        Log.d(TAG, "stage=request ms=${SystemClock.elapsedRealtime() - requestStarted} endpoint=nearby")
        NearbyHttpClient.client.await(request).use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw GooglePlacesHttpException(response.code, googleErrorMessage(responseBody))
            return parseGooglePlaces(JSONObject(responseBody).optJSONArray("places"), origin, category.name)
        }
    }

    private suspend fun fetchGoogleTextPlaces(origin: Location, query: String): List<NearbyPlace> {
        val body = JSONObject().apply {
            put("textQuery", query)
            put("maxResultCount", MAX_GOOGLE_RESULTS)
            put("rankPreference", "DISTANCE")
            put("languageCode", "en")
            put("locationBias", JSONObject().put("circle", JSONObject().apply {
                put("center", JSONObject().put("latitude", origin.latitude).put("longitude", origin.longitude))
                put("radius", searchRadiusMeters().toDouble())
            }))
        }
        Log.d(TAG, "textSearch query=${query.trim()} payload=$body")
        val requestStarted = SystemClock.elapsedRealtime()
        val request = googleRequest("https://places.googleapis.com/v1/places:searchText", body)
        Log.d(TAG, "stage=request ms=${SystemClock.elapsedRealtime() - requestStarted} endpoint=text")
        NearbyHttpClient.client.await(request).use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw GooglePlacesHttpException(response.code, googleErrorMessage(responseBody))
            // A free-text search must never inherit the selected quick chip as
            // its displayed category; that made Pizza results appear labelled
            // as Coffee when the API omitted primaryType.
            return parseGooglePlaces(JSONObject(responseBody).optJSONArray("places"), origin, query)
        }
    }

    private fun parseGooglePlaces(places: JSONArray?, origin: Location, fallbackKind: String): List<NearbyPlace> {
        val parseStarted = SystemClock.elapsedRealtime()
        if (places == null) return emptyList()
        val parsed = buildList {
            for (index in 0 until places.length()) {
                val item = places.optJSONObject(index) ?: continue
                val location = item.optJSONObject("location") ?: continue
                val latitude = location.optDouble("latitude", Double.NaN)
                val longitude = location.optDouble("longitude", Double.NaN)
                val name = item.optJSONObject("displayName")?.optString("text").orEmpty()
                if (name.isBlank() || !latitude.isFinite() || !longitude.isFinite()) continue
                val point = Location("google").apply { this.latitude = latitude; this.longitude = longitude }
                val primaryType = item.optString("primaryType")
                    .replace('_', ' ')
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                    .ifBlank { fallbackKind }
                val openingHours = item.optJSONObject("currentOpeningHours")
                    ?.takeIf { it.has("openNow") }
                    ?.let { if (it.optBoolean("openNow")) "Open now" else "Closed" }
                val photoName = item.optJSONArray("photos")?.optJSONObject(0)?.optString("name").orEmpty()
                add(NearbyPlace(
                    id = item.optString("id").ifBlank { "$latitude,$longitude" },
                    name = name,
                    kind = primaryType,
                    address = item.optString("formattedAddress").ifBlank { fallbackKind },
                    latitude = latitude,
                    longitude = longitude,
                    distanceMeters = origin.distanceTo(point),
                    imageUrl = photoName.takeIf { it.isNotBlank() }?.let {
                        "https://places.googleapis.com/v1/$it/media?maxWidthPx=160&key=${BuildConfig.GOOGLE_PLACES_API_KEY}"
                    },
                    rating = item.optDouble("rating", Double.NaN).takeIf { it.isFinite() }
                        ?.let { String.format(Locale.getDefault(), "%.1f", it) },
                    reviewCount = item.optInt("userRatingCount", 0).takeIf { it > 0 }?.toString(),
                    openingHours = openingHours
                ))
            }
        }
        val nearby = parsed.filter { it.distanceMeters <= searchRadiusMeters() }
        val result = (nearby.ifEmpty { parsed }).sortedBy { it.distanceMeters }.take(MAX_RESULTS)
        Log.d(TAG, "stage=parse ms=${SystemClock.elapsedRealtime() - parseStarted} results=${result.size}")
        return result
    }

    private suspend fun fetchOverpassFallback(category: NearbyCategory?, textQuery: String?, origin: Location): List<NearbyPlace> {
        val fallbackStarted = SystemClock.elapsedRealtime()
        val filters = category?.osmFilters.orEmpty()
        if (filters.isEmpty() && textQuery.isNullOrBlank()) throw IOException("No fallback category mapping is available.")
        val point = "around:${searchRadiusMeters()},${origin.latitude},${origin.longitude}"
        val query = if (filters.isNotEmpty()) {
            val selectors = filters.joinToString("") { "nwr($point)$it;" }
            "[out:json][timeout:20];($selectors);out center tags 40;"
        } else {
            val escaped = textQuery!!.replace(Regex("[^\\p{L}\\p{N} ]"), " ").trim().take(70)
                .replace("\\", "").replace("\"", "")
            "[out:json][timeout:20];nwr($point)[name~\"$escaped\",i];out center tags 40;"
        }
        var lastError: Exception? = null
        for (endpoint in OVERPASS_ENDPOINTS) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            try {
                val body = ("data=" + URLEncoder.encode(query, Charsets.UTF_8.name()))
                    .toRequestBody("application/x-www-form-urlencoded; charset=UTF-8".toMediaType())
                val request = Request.Builder().url(endpoint).post(body)
                    .header("Accept", "application/json")
                    .header("User-Agent", "TrackerApp-Nearby/1.0")
                    .build()
                NearbyHttpClient.client.await(request).use { response ->
                    if (!response.isSuccessful) throw IOException("OpenStreetMap returned HTTP ${response.code}")
                    val elements = JSONObject(response.body?.string().orEmpty()).optJSONArray("elements") ?: return emptyList()
                    return parseOverpass(elements, origin).also {
                        Log.d(TAG, "stage=fallback ms=${SystemClock.elapsedRealtime() - fallbackStarted} results=${it.size}")
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                lastError = error
                Log.w(TAG, "OpenStreetMap fallback failed for endpoint: ${error.javaClass.simpleName}")
            }
        }
        throw lastError ?: IOException("OpenStreetMap fallback is unavailable")
    }

    private fun parseOverpass(elements: JSONArray, origin: Location): List<NearbyPlace> = buildList {
        for (index in 0 until elements.length()) {
            val item = elements.optJSONObject(index) ?: continue
            val tags = item.optJSONObject("tags") ?: continue
            val name = tags.optString("name").trim().takeIf { it.isNotEmpty() } ?: continue
            val center = item.optJSONObject("center")
            val latitude = if (item.has("lat")) item.optDouble("lat", Double.NaN) else center?.optDouble("lat", Double.NaN) ?: Double.NaN
            val longitude = if (item.has("lon")) item.optDouble("lon", Double.NaN) else center?.optDouble("lon", Double.NaN) ?: Double.NaN
            if (!latitude.isFinite() || !longitude.isFinite()) continue
            val point = Location("osm").apply { this.latitude = latitude; this.longitude = longitude }
            val type = describeType(tags)
            val street = listOf(tags.optString("addr:housenumber"), tags.optString("addr:street"))
                .filter { it.isNotBlank() }.joinToString(" ")
            val address = listOf(street, tags.optString("addr:suburb"), tags.optString("addr:city"))
                .filter { it.isNotBlank() }.distinct().joinToString(", ")
                .ifBlank { tags.optString("addr:full").ifBlank { type } }
            add(NearbyPlace(
                id = "${item.optString("type")}_${item.optLong("id")}", name = name, kind = type,
                address = address, latitude = latitude, longitude = longitude,
                distanceMeters = origin.distanceTo(point), source = NearbySource.OPENSTREETMAP
            ))
        }
    }.distinctBy { it.id }.filter { it.distanceMeters <= searchRadiusMeters() }.sortedBy { it.distanceMeters }.take(MAX_RESULTS)

    private fun googleRequest(url: String, body: JSONObject): Request {
        val builder = Request.Builder().url(url)
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("Content-Type", "application/json")
            .header("X-Goog-Api-Key", BuildConfig.GOOGLE_PLACES_API_KEY)
            .header("X-Goog-FieldMask", GOOGLE_FIELD_MASK)
            .header("X-Android-Package", packageName)
        try {
            @Suppress("DEPRECATION")
            val packageInfo = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            @Suppress("DEPRECATION")
            val signature = packageInfo.signatures?.firstOrNull()
            if (signature != null) {
                val digest = MessageDigest.getInstance("SHA-1").digest(signature.toByteArray())
                builder.header("X-Android-Cert", digest.joinToString(":") { byte -> "%02X".format(byte) })
            }
        } catch (error: Exception) {
            Log.w(TAG, "Could not attach Android certificate header: ${error.javaClass.simpleName}")
        }
        return builder.build()
    }

    private fun googleErrorMessage(body: String): String =
        runCatching { JSONObject(body).optJSONObject("error")?.optString("message").orEmpty() }
            .getOrDefault("").ifBlank { "Google Places returned an error." }

    private fun handleSearchError(error: Exception, version: Int, started: Long) {
        if (!searchGeneration.isCurrent(version) || isFinishing || isDestroyed) return
        Log.e(TAG, "search=$version stage=failed totalMs=${SystemClock.elapsedRealtime() - started} type=${error.javaClass.simpleName}", error)
        val detail = when (error) {
            is NearbyConfigurationException -> error.message ?: "Google Places is not configured."
            is NearbyLocationException -> "Turn on Location and try again so we can find places near you."
            is GooglePlacesHttpException -> when (error.statusCode) {
                400 -> "Google rejected the search request. Check the selected category and search radius."
                401, 403 -> "Google Places access is not authorized. Check the API, billing, package name, and signing certificate restrictions."
                429 -> "Google Places quota has been reached. Try again later."
                in 500..599 -> "Google Places is temporarily unavailable. Try again shortly."
                else -> "Google Places returned HTTP ${error.statusCode}."
            }
            is SocketTimeoutException -> "The places service took too long to respond. Try again."
            is IOException -> if (isNetworkAvailable()) "The places service is unavailable. Check your connection and try again." else "You appear to be offline. Reconnect and try again."
            else -> "Could not load nearby places. Try again."
        }
        val hasVisibleResults = latestPlaces.isNotEmpty() && adapter.currentList.isNotEmpty()
        if (hasVisibleResults) showRefreshError(detail) else showError("Couldn’t load nearby places", detail)
    }

    private fun showPlaces(places: List<NearbyPlace>, query: String, status: String?) {
        latestPlaces = places
        showAllResults = false
        resetResultsHeight()
        loading.visibility = View.GONE
        statePanel.visibility = if (places.isEmpty()) View.VISIBLE else View.GONE
        count.visibility = View.VISIBLE
        count.text = status ?: if (query.isBlank() && places.isNotEmpty()) getString(R.string.nearby_view_all, places.size) else if (places.size == 1) getString(R.string.nearby_place_count_one) else getString(R.string.nearby_places_count, places.size)
        count.setTextColor(if (places.isEmpty()) muted else green)
        count.isClickable = status == null && query.isBlank() && places.size > INITIAL_RESULT_COUNT
        count.setOnClickListener {
            if (count.isClickable) {
                showAllResults = true
                count.visibility = View.GONE
                renderPlaces()
            }
        }
        title.text = if (query.isBlank()) getString(R.string.nearby_category_near_you, selectedCategory) else getString(R.string.area_search_results)
        if (places.isEmpty()) {
            stateIcon.setImageResource(R.drawable.ic_area_no_result)
            stateIcon.visibility = View.VISIBLE
            stateTitle.setText(R.string.nearby_no_places_found)
            stateDescription.text = if (query.isBlank()) getString(R.string.nearby_no_category_matches, categorySearchName(selectedCategory))
            else getString(R.string.nearby_no_search_matches, query)
            stateAction.text = if (query.isBlank()) getString(R.string.nearby_try_category) else getString(R.string.nearby_clear_search)
            stateAction.visibility = View.VISIBLE
            stateAction.setOnClickListener {
                if (query.isBlank()) {
                    openCategories()
                } else {
                    suppressTextListener = true
                    search.text?.clear()
                    suppressTextListener = false
                    currentQuery = ""
                    updateClearVisibility()
                    queryCurrentSelection()
                }
            }
        } else renderPlaces()
    }

    private fun renderPlaces() {
        val visiblePlaces = if (showAllResults) latestPlaces else latestPlaces.take(INITIAL_RESULT_COUNT)
        adapter.submitList(visiblePlaces) {
            // RecyclerView is nested in the screen ScrollView. Explicitly remeasure
            // after every async ListAdapter update so the first cards cannot remain
            // clipped at a zero-height measurement.
            expandResultsToContent()
        }
    }

    private fun resetResultsHeight() {
        val params = results.layoutParams ?: return
        if (params.height != android.view.ViewGroup.LayoutParams.WRAP_CONTENT) {
            params.height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            results.layoutParams = params
        }
        results.requestLayout()
    }

    private fun expandResultsToContent() {
        results.post {
            val width = results.width
            if (width <= 0) {
                results.post { expandResultsToContent() }
                return@post
            }
            results.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            val params = results.layoutParams ?: return@post
            params.height = results.measuredHeight
            results.layoutParams = params
            results.requestLayout()
        }
    }

    private fun showRefreshError(message: String) {
        loading.visibility = View.GONE
        statePanel.visibility = View.VISIBLE
        stateIcon.visibility = View.VISIBLE
        stateIcon.setImageResource(R.drawable.ic_phone_info)
        stateTitle.setText(R.string.nearby_refresh_failed)
        stateDescription.text = getString(R.string.nearby_cached_results, message)
        stateAction.setText(R.string.nearby_try_again)
        stateAction.visibility = View.VISIBLE
        stateAction.setOnClickListener { queryCurrentSelection() }
        count.setText(R.string.nearby_cached_refresh_failed)
        count.setTextColor(muted)
    }

    private fun loadNearByBanner() {
        AdsManager.loadBanner(this, AdRemoteConfig.banner_all, binding.frBanner, isCollapse = false)
    }

    private fun openDirections(place: NearbyPlace) {
        val search = Uri.encode("${place.name}, ${place.latitude}, ${place.longitude}")
        val location = Uri.parse("https://www.google.com/maps/search/?api=1&query=$search")
        try {
            val mapsIntent = Intent(Intent.ACTION_VIEW, location).setPackage("com.google.android.apps.maps")
            if (mapsIntent.resolveActivity(packageManager) != null) startActivity(mapsIntent)
            else startActivity(Intent(Intent.ACTION_VIEW, location))
        } catch (_: Exception) {
            Toast.makeText(this, R.string.nearby_no_maps, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showLocationPermissionState() {
        title.text = getString(R.string.nearby_category_near_you, selectedCategory)
        count.text = ""
        adapter.submitList(emptyList())
        latestPlaces = emptyList()
        showState(getString(R.string.nearby_location_needed), getString(R.string.nearby_location_message), false)
        stateIcon.setImageResource(R.drawable.ic_location_focus_green)
        stateAction.setText(R.string.common_use_my_location)
        stateAction.visibility = View.VISIBLE
        stateAction.setOnClickListener {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), REQUEST_LOCATION)
        }
    }

    private fun showError(heading: String, message: String) {
        adapter.submitList(emptyList())
        latestPlaces = emptyList()
        count.text = ""
        showState(heading, message, false)
        stateIcon.setImageResource(R.drawable.ic_phone_info)
        stateAction.setText(R.string.nearby_try_again)
        stateAction.visibility = View.VISIBLE
        stateAction.setOnClickListener { if (hasLocationPermission()) queryCurrentSelection() else showLocationPermissionState() }
    }

    private fun showState(heading: String, message: String, showLoading: Boolean) {
        resetResultsHeight()
        adapter.submitList(emptyList())
        statePanel.visibility = View.VISIBLE
        stateIcon.visibility = if (showLoading) View.GONE else View.VISIBLE
        stateTitle.text = heading
        stateDescription.text = message
        loading.visibility = if (showLoading) View.VISIBLE else View.GONE
        stateAction.visibility = View.GONE
    }

    private fun clearRenderedResultsForNewSearch() {
        latestPlaces = emptyList()
        showAllResults = false
        adapter.submitList(emptyList())
        resetResultsHeight()
        count.text = ""
        count.isClickable = false
    }

    private fun obtainLocationAndSearch() {
        if (currentQuery.length >= 2) requestPlaces(currentQuery) else queryCurrentSelection()
    }

    private fun hasLocationPermission() = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun isNetworkAvailable(): Boolean {
        val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    @Deprecated("Deprecated in Android")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LOCATION) {
            if (hasLocationPermission()) obtainLocationAndSearch() else showLocationPermissionState()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_CATEGORY, selectedCategory)
        outState.putString(STATE_QUERY, currentQuery)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        debounceJob?.cancel()
        searchJob?.cancel()
        super.onDestroy()
    }

    private fun selectedCategoryDefinition(): NearbyCategory =
        NearbyCategoryCatalog.find(selectedCategory)
            ?: error("No category selected") 

    private fun searchRadiusMeters(): Int = selectedRadiusKm * 1_000

    private fun describeType(tags: JSONObject): String {
        val tag = listOf("amenity", "shop", "leisure", "tourism", "railway", "aeroway", "craft").firstOrNull { tags.has(it) }
        return tag?.let { tags.optString(it) }.orEmpty().replace('_', ' ')
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            .ifBlank { "Place" }
    }

    private fun categorySearchName(category: String) =
        NearbyCategoryCatalog.find(category)?.name?.lowercase(Locale.getDefault()) ?: category.lowercase(Locale.getDefault())

    private fun rounded(color: Int, radiusDp: Int) = android.graphics.drawable.GradientDrawable().apply {
        setColor(color)
        cornerRadius = radiusDp * resources.displayMetrics.density
    }

    private fun bordered(color: Int, stroke: Int, radiusDp: Int) = rounded(color, radiusDp).apply {
        setStroke(resources.displayMetrics.density.roundToInt().coerceAtLeast(1), stroke)
    }

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
        private const val DEFAULT_RADIUS_KM = 5
        private const val MAX_RESULTS = 20
        private const val MAX_GOOGLE_RESULTS = 20
        private const val INITIAL_RESULT_COUNT = 5
        private const val SEARCH_DEBOUNCE_MS = 350L
        private const val LOCATION_TIMEOUT_MS = 8_000L
        private const val TAG = "NearbyActivity"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val GOOGLE_FIELD_MASK = "places.id,places.displayName,places.formattedAddress,places.location,places.primaryType,places.rating,places.userRatingCount,places.currentOpeningHours,places.photos"
        private val OVERPASS_ENDPOINTS = listOf(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter",
            "https://overpass.private.coffee/api/interpreter"
        )
    }
}
