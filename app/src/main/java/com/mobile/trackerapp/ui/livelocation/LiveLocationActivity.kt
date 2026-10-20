package com.mobile.trackerapp.ui.livelocation
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.app.Activity
import android.app.Dialog
import android.net.Uri
import android.provider.Settings
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.widget.TextView
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.ImageView
import android.widget.FrameLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentActivity
import com.google.android.gms.location.*
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import com.mobile.trackerapp.R
import com.mobile.trackerapp.ads.AdRemoteConfig
import com.mobile.trackerapp.ads.AdsManager
import com.mobile.trackerapp.ads.banner_all
import com.mobile.trackerapp.network.RealtimeLocationClient
import com.mobile.trackerapp.network.TrackerBackendClient
import com.mobile.trackerapp.service.LocationSharingCoordinator
import com.mobile.trackerapp.service.LocationSharingService
import com.mobile.trackerapp.ui.connection.TrackFriendRequestsActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

open class LiveLocationActivity : AppCompatActivity(), OnMapReadyCallback {
    private var friendsDialog: Dialog? = null
    private var friendsPanel: View? = null
    protected open val locationLayout: Int = R.layout.activity_live_location
    private val friendsOnly by lazy { intent.getBooleanExtra("friends_only", false) }
    private lateinit var map: GoogleMap
    private lateinit var fused: FusedLocationProviderClient
    private lateinit var address: TextView
    private var marker: Marker? = null
    private var callback: LocationCallback? = null
    private var lastLocation: Location? = null
    private var returningFromAppSettings = false
    private var pendingSharing = false
    private var pendingFocus = false
    private var backgroundSettingsOpen = false
    private var notificationRequestAttempted = false
    private lateinit var realtime: RealtimeLocationClient
    private val friendMarkers = mutableMapOf<String, Marker>()
    private val pendingFriendLocations = linkedMapOf<String, JSONObject>()
    private val acceptedFriends = linkedMapOf<String, String>()
    private val friendDeviceModels = mutableMapOf<String, String>()
    private val friendLastUpdated = mutableMapOf<String, Long>()
    private val friendPermissionGranted = mutableMapOf<String, Boolean>()
    private val friendSharingEnabled = mutableMapOf<String, Boolean>()
    private val friendLocationRequestStatus = mutableMapOf<String, String>()
    private var friendCameraFocused = false
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true || grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            ensureLocationSettings()
        } else if (::address.isInitialized) {
            pendingFocus = false
            address.setText(R.string.live_location_permission_required)
            showPermissionHelp()
        }
    }
    private val locationSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) continueAfterLocationSettings()
        else {
            pendingFocus = false
            if (::address.isInitialized) address.setText(R.string.live_turn_on_location)
        }
    }
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        notificationRequestAttempted = true
        startFriendSharingFeatures()
    }
    private val backgroundLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        backgroundSettingsOpen = false
        if (granted) startFriendSharingFeatures()
        else pendingSharing = false
    }
    private val geocoder by lazy { Geocoder(this, Locale.getDefault()) }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        AdsManager.loadInterBack(this, ignoreLimit = true)
        fused = LocationServices.getFusedLocationProviderClient(this)
        setContentView(layoutInflater.inflate(locationLayout, null))
        AdsManager.loadBanner(
            this,
            AdRemoteConfig.banner_all,
            findViewById(R.id.fr_banner),
            isCollapse = false
        )
        AdsManager.loadInterHome(this, ignoreLimit = true)
        if (friendsOnly) {
            realtime = RealtimeLocationClient(
                this,
                onLocation = { payload -> runOnUiThread { showFriendLocation(payload) } },
                onFriendshipAccepted = { payload ->
                    runOnUiThread {
                        val friendName = payload.optString("friend_name").ifBlank { payload.optString("name") }
                        LocationSharingCoordinator.markAcceptedFriend(this, friendName)
                    }
                }
            )
            realtime.connect()
        }
        intent.getStringExtra("screen_title")?.let { findViewById<TextView>(R.id.live_title).text = it }
        intent.getStringExtra("screen_subtitle")?.let { findViewById<TextView>(R.id.live_subtitle).text = it }
        findViewById<ImageView>(R.id.live_back).setOnClickListener {
            onBackPressed()
        }
        if (friendsOnly) {
            findViewById<View>(R.id.self_location_controls).visibility = View.GONE
            findViewById<View>(R.id.friend_empty_state).visibility = View.GONE
            findViewById<View>(R.id.friend_people_button).setOnClickListener { toggleFriendsPanel() }
            findViewById<View>(R.id.friend_panel_scrim).setOnClickListener { setFriendsPanelVisible(false) }
            findViewById<View>(R.id.friend_add_button).setOnClickListener { startActivity(Intent(this, TrackFriendRequestsActivity::class.java)) }
            findViewById<View>(R.id.friend_received_button).setOnClickListener {
                startActivity(Intent(this, TrackFriendRequestsActivity::class.java).putExtra("open_tab", "received"))
            }
            findViewById<View>(R.id.friend_sent_button).setOnClickListener {
                startActivity(Intent(this, TrackFriendRequestsActivity::class.java).putExtra("open_tab", "sent"))
            }
        }
        address = findViewById(R.id.live_address)
        findViewById<View>(R.id.share_location).setOnClickListener {
            if (lastLocation == null) requestPermission() else shareLocation()
        }
        val layers = findViewById<LinearLayout>(R.id.map_layers_menu)
        findViewById<View>(R.id.map_layers_button).setOnClickListener { layers.visibility = if (layers.visibility == View.VISIBLE) View.GONE else View.VISIBLE }
        findViewById<TextView>(R.id.map_normal).setOnClickListener { selectMapType(GoogleMap.MAP_TYPE_NORMAL, R.id.map_normal, layers) }
        findViewById<TextView>(R.id.map_satellite).setOnClickListener { selectMapType(GoogleMap.MAP_TYPE_SATELLITE, R.id.map_satellite, layers) }
        findViewById<TextView>(R.id.map_terrain).setOnClickListener { selectMapType(GoogleMap.MAP_TYPE_TERRAIN, R.id.map_terrain, layers) }
        findViewById<TextView>(R.id.map_hybrid).setOnClickListener { selectMapType(GoogleMap.MAP_TYPE_HYBRID, R.id.map_hybrid, layers) }
        findViewById<View>(R.id.map_zoom_in).setOnClickListener { if (::map.isInitialized) map.animateCamera(CameraUpdateFactory.zoomIn()) }
        findViewById<View>(R.id.map_zoom_out).setOnClickListener { if (::map.isInitialized) map.animateCamera(CameraUpdateFactory.zoomOut()) }
        findViewById<View>(R.id.map_focus).setOnClickListener {
            if (friendsOnly) focusOnFriendLocations() else focusOnCurrentLocation()
        }
        supportFragmentManager.beginTransaction().replace(R.id.live_map, SupportMapFragment.newInstance()).commit()
        supportFragmentManager.executePendingTransactions()
        (supportFragmentManager.findFragmentById(R.id.live_map) as? SupportMapFragment)?.getMapAsync(this)
    }

    private fun selectMapType(type: Int, selected: Int, menu: View) {
        if (!::map.isInitialized) return
        map.mapType = type
        listOf(R.id.map_normal, R.id.map_satellite, R.id.map_terrain, R.id.map_hybrid).forEach {
            findViewById<TextView>(it).setTypeface(null, if (it == selected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        }
        menu.visibility = View.GONE
    }
    override fun onMapReady(ready: GoogleMap) {
        map = ready
        map.uiSettings.isZoomControlsEnabled = false
        map.uiSettings.isMyLocationButtonEnabled = false
        pendingFriendLocations.values.toList().forEach { showFriendLocation(it) }
        pendingFriendLocations.clear()
        if (friendsOnly) loadAcceptedFriends()
        requestPermission()
    }
    private fun requestPermission() {
        if (hasPermission()) { ensureLocationSettings(); return }
        locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    private fun hasPermission() = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    private fun ensureLocationSettings() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L).build()
        val settingsRequest = LocationSettingsRequest.Builder().addLocationRequest(request).setAlwaysShow(true).build()
        LocationServices.getSettingsClient(this).checkLocationSettings(settingsRequest)
            .addOnSuccessListener { continueAfterLocationSettings() }
            .addOnFailureListener { error ->
                if (error is ResolvableApiException) {
                    runCatching { locationSettingsLauncher.launch(IntentSenderRequest.Builder(error.resolution).build()) }
                        .onFailure { address.setText(R.string.live_turn_on_location) }
                } else {
                    address.setText(R.string.live_turn_on_location)
                }
            }
    }
    private fun continueAfterLocationSettings() {
        if (pendingFocus) {
            pendingFocus = false
            if (friendsOnly) focusOnFriendLocations() else focusOnCurrentLocation()
        }
        if (friendsOnly) {
            startFriendSharingFeatures()
        } else {
            enableLocation()
        }
    }

    private fun startFriendSharingFeatures() {
        if (!friendsOnly || !hasPermission()) return
        pendingSharing = true
        if (!hasBackgroundLocation()) {
            showBackgroundLocationDialog()
            return
        }
        if (Build.VERSION.SDK_INT >= 33 && !hasNotificationPermission() && !notificationRequestAttempted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        CoroutineScope(Dispatchers.Main).launch {
            val hasFriends = runCatching { TrackerBackendClient(this@LiveLocationActivity).hasAcceptedFriends() }.getOrDefault(false)
            if (!hasFriends) {
                pendingSharing = false
                getSharedPreferences(LocationSharingCoordinator.PREFS, MODE_PRIVATE).edit()
                    .putBoolean(LocationSharingCoordinator.ENABLED, false)
                    .apply()
                stopService(Intent(this@LiveLocationActivity, LocationSharingService::class.java))
                android.widget.Toast.makeText(this@LiveLocationActivity, R.string.live_accept_friend_before_sharing, android.widget.Toast.LENGTH_LONG).show()
                return@launch
            }
            pendingSharing = false
            getSharedPreferences(LocationSharingCoordinator.PREFS, MODE_PRIVATE).edit()
                .putBoolean(LocationSharingCoordinator.ENABLED, true)
                .apply()
            LocationSharingCoordinator.startIfPermitted(this@LiveLocationActivity)
            android.widget.Toast.makeText(this@LiveLocationActivity, R.string.live_sharing_started, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun showBackgroundLocationDialog() {
        if (backgroundSettingsOpen) return
        backgroundSettingsOpen = true
        AlertDialog.Builder(this)
            .setTitle(R.string.live_background_title)
            .setMessage(R.string.live_background_message)
            .setNegativeButton(R.string.common_not_now) { _, _ -> backgroundSettingsOpen = false; pendingSharing = false }
            .setPositiveButton(R.string.common_open_settings) { _, _ ->
                if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                    backgroundLocationPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                } else {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                }
            }
            .show()
    }

    private fun hasBackgroundLocation() = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun hasNotificationPermission() = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    private fun showPermissionHelp() {
        val canRetry = ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION) ||
            ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        AlertDialog.Builder(this)
            .setTitle(R.string.traffic_location_access_needed)
            .setMessage(R.string.live_permission_message)
            .setNegativeButton(R.string.common_not_now, null)
            .setPositiveButton(if (canRetry) R.string.common_allow else R.string.common_open_settings) { _, _ ->
                if (canRetry) requestPermission() else {
                    returningFromAppSettings = true
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                }
            }
            .show()
    }
    private fun enableLocation() { if (!::map.isInitialized || !hasPermission()) return; startLocationUpdates() }
    private fun startLocationUpdates() { callback?.let { fused.removeLocationUpdates(it) }; callback = object : LocationCallback() { override fun onLocationResult(r: LocationResult) { r.lastLocation?.let { updateLocation(it) } } }; val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L).setMinUpdateDistanceMeters(5f).build(); fused.requestLocationUpdates(request, callback!!, Looper.getMainLooper()); fused.lastLocation.addOnSuccessListener { it?.let { updateLocation(it) } } }
    private fun updateLocation(location: Location) { lastLocation = location; val point = LatLng(location.latitude, location.longitude); if (marker == null) { marker = map.addMarker(MarkerOptions().position(point).title(getString(R.string.live_me))); map.animateCamera(CameraUpdateFactory.newLatLngZoom(point, 16f)) } else { marker?.position = point; marker?.title = getString(R.string.live_me) }; address.text = resolveAddress(location) }
    private fun focusOnCurrentLocation() {
        if (!::map.isInitialized) return
        val focus: (Location) -> Unit = { location ->
            lastLocation = location
            val point = LatLng(location.latitude, location.longitude)
            updateLocation(location)
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(point, 15.5f))
        }
        lastLocation?.let { focus(it); return }
        if (!hasPermission()) {
            pendingFocus = true
            requestPermission()
            return
        }
        fused.lastLocation
            .addOnSuccessListener { cached ->
                if (cached != null) {
                    focus(cached)
                } else {
                    fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                        .addOnSuccessListener { current ->
                            if (current != null) focus(current)
                            else address.setText(R.string.live_location_unavailable)
                        }
                        .addOnFailureListener { address.setText(R.string.live_location_unavailable) }
                }
            }
            .addOnFailureListener { address.setText(R.string.live_location_unavailable) }
    }
    private fun batteryPercentage(): Float = registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))?.let { intent -> val level = intent.getIntExtra("level", 0); val scale = intent.getIntExtra("scale", 100); level * 100f / scale.coerceAtLeast(1) } ?: 0f
    private fun showFriendLocation(payload: JSONObject) {
        if (!friendsOnly) return
        val id = payload.optString("device_id")
        if (id.isBlank()) return
        val latitude = payload.optDouble("latitude", Double.NaN)
        val longitude = payload.optDouble("longitude", Double.NaN)
        if (latitude.isNaN() || longitude.isNaN()) return
        if (!::map.isInitialized) {
            pendingFriendLocations[id] = payload
            return
        }
        val point = LatLng(latitude, longitude)
        friendLastUpdated[id] = parseTimestamp(payload.optString("updated_at")) ?: System.currentTimeMillis()
        friendSharingEnabled[id] = payload.optBoolean("sharing_enabled", true)
        val displayName = acceptedFriends[id].orEmpty()
            .ifBlank { payload.optString("name") }
            .ifBlank { payload.optString("device_model") }
            .ifBlank { id }
        val existing = friendMarkers[id]
        if (existing == null) {
            friendMarkers[id] = map.addMarker(
                MarkerOptions()
                    .position(point)
                    .title(displayName)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
            )!!
        } else {
            existing.position = point
            existing.title = displayName
        }
        if (!friendCameraFocused) {
            friendCameraFocused = true
            focusOnFriendLocations(showInfoWindow = true)
        }
        if (findViewById<View>(R.id.friend_rows)?.visibility == View.VISIBLE) renderFriendRows()
    }

    private fun focusOnFriendLocations(showInfoWindow: Boolean = false) {
        if (!friendsOnly || !::map.isInitialized || friendMarkers.isEmpty()) return
        if (friendMarkers.size == 1) {
            val marker = friendMarkers.values.first()
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(marker.position, 16f))
            if (showInfoWindow) marker.showInfoWindow()
            return
        }
        val bounds = LatLngBounds.Builder()
        friendMarkers.values.forEach { bounds.include(it.position) }
        map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), dp(72)))
    }
    private fun toggleFriendsPanel() {
        if (friendsDialog?.isShowing == true) {
            friendsDialog?.dismiss()
            return
        }
        val panel = friendsPanel ?: findViewById<View>(R.id.friend_empty_state)
            ?: return
        friendsPanel = panel
        (panel.parent as? ViewGroup)?.removeView(panel)
        panel.visibility = View.VISIBLE
        val dialog = Dialog(this)
        dialog.setContentView(panel)
        dialog.setCanceledOnTouchOutside(true)
        dialog.setOnDismissListener { friendsDialog = null }
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setDimAmount(0.32f)
            addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setGravity(Gravity.BOTTOM)
        }
        friendsDialog = dialog
        dialog.show()
        dialog.window?.setLayout(
            resources.displayMetrics.widthPixels - dp(24),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.window?.attributes = dialog.window?.attributes?.apply { y = dp(10) }
        panel.findViewById<TextView>(R.id.friend_empty_message).setText(R.string.live_loading_friends)
        panel.findViewById<TextView>(R.id.friend_empty_message).visibility = View.VISIBLE
        loadAcceptedFriends()
    }

    private fun setFriendsPanelVisible(visible: Boolean) {
        if (visible) {
            if (friendsDialog?.isShowing != true) toggleFriendsPanel()
        } else {
            friendsDialog?.dismiss()
        }
    }

    private fun loadAcceptedFriends() {
        CoroutineScope(Dispatchers.Main).launch {
            val result = runCatching {
                val backend = TrackerBackendClient(this@LiveLocationActivity)
                backend.register()
                val array = backend.listFriendsOrLegacy().optJSONArray("friends") ?: org.json.JSONArray()
                val friends = linkedMapOf<String, String>()
                val models = linkedMapOf<String, String>()
                val permissions = linkedMapOf<String, Boolean>()
                val sharing = linkedMapOf<String, Boolean>()
                val requests = linkedMapOf<String, String>()
                val locations = linkedMapOf<String, JSONObject>()
                for (index in 0 until array.length()) {
                    val friend = array.optJSONObject(index) ?: continue
                    val id = friend.optString("friend_id").trim()
                    if (id.isBlank()) continue
                    friends[id] = friend.optString("name").ifBlank { friend.optString("device_model") }.ifBlank { id }
                    models[id] = friend.optString("device_model").ifBlank { getString(R.string.live_android_device) }
                    permissions[id] = friend.optBoolean("permission_granted", false)
                    sharing[id] = friend.optBoolean("sharing_enabled", false)
                    requests[id] = friend.optString("location_request_status", "none")
                    friend.optJSONObject("last_location")?.let { locations[id] = it }
                }
                AcceptedFriendSnapshot(friends, models, permissions, sharing, requests, locations)
            }.getOrElse { AcceptedFriendSnapshot(linkedMapOf(), linkedMapOf(), linkedMapOf(), linkedMapOf(), linkedMapOf(), linkedMapOf()) }

            val removed = friendMarkers.keys - result.friends.keys
            removed.forEach { friendMarkers.remove(it)?.remove() }
            acceptedFriends.clear(); acceptedFriends.putAll(result.friends)
            friendDeviceModels.clear(); friendDeviceModels.putAll(result.models)
            friendPermissionGranted.clear(); friendPermissionGranted.putAll(result.permissions)
            friendSharingEnabled.clear(); friendSharingEnabled.putAll(result.sharing)
            friendLocationRequestStatus.clear(); friendLocationRequestStatus.putAll(result.requestStatus)
            result.locations.values.forEach { location ->
                val id = location.optString("device_id")
                if (id.isNotBlank()) showFriendLocation(location)
            }
            renderFriendRows()
        }
    }

    private data class AcceptedFriendSnapshot(
        val friends: LinkedHashMap<String, String>,
        val models: LinkedHashMap<String, String>,
        val permissions: LinkedHashMap<String, Boolean>,
        val sharing: LinkedHashMap<String, Boolean>,
        val requestStatus: LinkedHashMap<String, String>,
        val locations: LinkedHashMap<String, JSONObject>
    )

    private fun renderFriendRows() {
        val panel = friendsPanel ?: return
        val rows = panel.findViewById<LinearLayout>(R.id.friend_rows)
        val empty = panel.findViewById<TextView>(R.id.friend_empty_message)
        val count = panel.findViewById<TextView>(R.id.friend_count)
        rows.removeAllViews()
        count.text = if (acceptedFriends.size == 1) getString(R.string.live_friend_count_one) else getString(R.string.live_friend_count, acceptedFriends.size)
        empty.text = when {
            acceptedFriends.isEmpty() -> getString(R.string.live_no_accepted_friends)
            friendMarkers.isEmpty() && acceptedFriends.keys.all { id -> !friendPermissionGranted[id].orFalse() } -> getString(R.string.live_request_friend_location)
            friendMarkers.isEmpty() -> getString(R.string.live_waiting_friends)
            else -> ""
        }
        empty.visibility = if (acceptedFriends.isEmpty() || friendMarkers.isEmpty()) View.VISIBLE else View.GONE
        acceptedFriends.forEach { (id, name) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(12), dp(16), dp(12))
                setBackgroundColor(Color.TRANSPARENT)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    val friendMarker = friendMarkers[id]
                    if (friendMarker != null) {
                        setFriendsPanelVisible(false)
                        friendMarker.title = name.trim().ifBlank { id }
                        map.animateCamera(CameraUpdateFactory.newLatLngZoom(friendMarker.position, 16f))
                        friendMarker.showInfoWindow()
                    } else {
                        android.widget.Toast.makeText(
                            this@LiveLocationActivity,
                            if (friendPermissionGranted[id].orFalse()) getString(R.string.live_waiting_for_friend, name.trim()) else getString(R.string.live_request_from_friend, name.trim()),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            row.addView(TextView(this).apply {
                text = name.trim().firstOrNull()?.uppercase(Locale.getDefault()) ?: "?"
                textSize = 16f
                setTextColor(Color.rgb(12, 20, 38))
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                background = getDrawable(R.drawable.bg_friend_icon_soft)
            }, LinearLayout.LayoutParams(dp(20), dp(20)))

            val details = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            details.addView(TextView(this@LiveLocationActivity).apply {
                text = name
                textSize = 16f
                setTextColor(Color.rgb(12, 20, 38))
                setTypeface(null, Typeface.BOLD)
            })
            details.addView(TextView(this@LiveLocationActivity).apply {
                val lastUpdated = friendLastUpdated[id]
                val state = when {
                    lastUpdated != null -> getString(R.string.live_updated, relativeUpdate(lastUpdated))
                    friendSharingEnabled[id].orFalse() -> getString(R.string.live_sharing_waiting)
                    friendPermissionGranted[id].orFalse() -> getString(R.string.live_sharing_off)
                    else -> getString(R.string.live_access_not_granted)
                }
                text = "${friendDeviceModels[id].orEmpty().ifBlank { getString(R.string.live_android_device) }} · $state"
                textSize = 12f
                setTextColor(Color.rgb(117, 126, 143))
            }, LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 3 })
            row.addView(details, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(12) })
            val right = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
            }
            right.addView(FrameLayout(this).apply {
                val online = friendMarkers.containsKey(id)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.rgb(232, 243, 232))
                }
                addView(View(this@LiveLocationActivity).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(if (online) Color.rgb(0, 166, 70) else Color.rgb(210, 55, 55))
                    }
                }, FrameLayout.LayoutParams(dp(6), dp(6), Gravity.CENTER))
            }, LinearLayout.LayoutParams(dp(20), dp(20)))
            if (!friendPermissionGranted[id].orFalse()) {
                val pending = friendLocationRequestStatus[id] == "pending"
                right.addView(TextView(this).apply {
                    text = if (pending) getString(R.string.live_requested) else getString(R.string.live_request)
                    textSize = 10f
                    setTextColor(if (pending) Color.rgb(117, 126, 143) else Color.rgb(0, 150, 60))
                    gravity = Gravity.CENTER
                    setPadding(dp(6), dp(3), dp(6), dp(3))
                    background = getDrawable(R.drawable.bg_friend_action_secondary)
                    isEnabled = !pending
                    setOnClickListener { requestFriendLocation(id) }
                }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(28)))
            }
            row.addView(right, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            rows.addView(row, LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
        }
    }
    private fun Boolean?.orFalse() = this == true
    private fun requestFriendLocation(friendId: String) {
        CoroutineScope(Dispatchers.Main).launch {
            runCatching { TrackerBackendClient(this@LiveLocationActivity).requestLocation(friendId) }
                .onSuccess {
                    friendLocationRequestStatus[friendId] = "pending"
                    renderFriendRows()
                    android.widget.Toast.makeText(this@LiveLocationActivity, R.string.live_location_request_sent, android.widget.Toast.LENGTH_SHORT).show()
                }
                .onFailure { android.widget.Toast.makeText(this@LiveLocationActivity, it.message ?: getString(R.string.live_location_request_failed), android.widget.Toast.LENGTH_LONG).show() }
        }
    }
    private fun parseTimestamp(raw: String): Long? {
        if (raw.isBlank()) return null
        raw.toLongOrNull()?.let { return if (it < 10_000_000_000L) it * 1000 else it }
        val patterns = listOf("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", "yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd HH:mm:ssXXX")
        return patterns.firstNotNullOfOrNull { pattern -> runCatching { SimpleDateFormat(pattern, Locale.US).parse(raw)?.time }.getOrNull() }
    }
    private fun relativeUpdate(timestamp: Long): String {
        val minutes = ((System.currentTimeMillis() - timestamp).coerceAtLeast(0L) / 60_000L)
        return when {
            minutes < 1 -> getString(R.string.live_just_now)
            minutes == 1L -> getString(R.string.live_minute_ago)
            minutes < 60 -> getString(R.string.live_minutes_ago, minutes)
            minutes / 60 == 1L -> getString(R.string.live_hour_ago)
            minutes < 1_440 -> getString(R.string.live_hours_ago, minutes / 60)
            minutes / 1_440 == 1L -> getString(R.string.live_yesterday)
            else -> getString(R.string.live_days_ago, minutes / 1_440)
        }
    }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
    private fun resolveAddress(l: Location) = try { geocoder.getFromLocation(l.latitude, l.longitude, 1)?.firstOrNull()?.getAddressLine(0) ?: getString(R.string.live_location_active) } catch (_: Exception) { getString(R.string.live_location_active) }
    private fun shareLocation() { val l = lastLocation; val text = if (l == null) getString(R.string.live_share_message) else "My location: https://maps.google.com/?q=" + l.latitude + "," + l.longitude; startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }, getString(R.string.live_share_location))) }
    override fun onResume() {
        super.onResume()
        if (backgroundSettingsOpen) {
            backgroundSettingsOpen = false
            if (pendingSharing && hasBackgroundLocation()) startFriendSharingFeatures()
            else if (pendingSharing) pendingSharing = false
        }
        if (returningFromAppSettings && !friendsOnly) {
            returningFromAppSettings = false
            requestPermission()
        }
    }
    override fun onDestroy() { callback?.let { fused.removeLocationUpdates(it) }; if (::realtime.isInitialized) realtime.close(); super.onDestroy() }

    override fun onBackPressed() {
        AdsManager.showInterHomeBack(this) {
            finish()
        }
    }
}

