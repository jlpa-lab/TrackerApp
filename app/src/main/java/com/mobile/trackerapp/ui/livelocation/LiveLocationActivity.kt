package com.mobile.trackerapp.ui.livelocation
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.app.Activity
import android.net.Uri
import android.provider.Settings
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.widget.TextView
import android.view.View
import android.widget.LinearLayout
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.ImageView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import com.google.android.gms.location.*
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import com.mobile.trackerapp.R
import com.mobile.trackerapp.network.RealtimeLocationClient
import com.mobile.trackerapp.service.LocationSharingService
import com.mobile.trackerapp.ui.connection.TrackFriendRequestsActivity
import org.json.JSONObject
import java.util.Locale

open class LiveLocationActivity : FragmentActivity(), OnMapReadyCallback {
    protected open val locationLayout: Int = R.layout.activity_live_location
    private val friendsOnly by lazy { intent.getBooleanExtra("friends_only", false) }
    private lateinit var map: GoogleMap
    private lateinit var fused: FusedLocationProviderClient
    private lateinit var address: TextView
    private var marker: Marker? = null
    private var callback: LocationCallback? = null
    private var lastLocation: Location? = null
    private var returningFromAppSettings = false
    private lateinit var realtime: RealtimeLocationClient
    private val friendMarkers = mutableMapOf<String, Marker>()
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true || grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            ensureLocationSettings()
        } else if (::address.isInitialized) {
            address.text = "Location permission is required to show your position"
            showPermissionHelp()
        }
    }
    private val locationSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) startLocationFeatures()
        else if (::address.isInitialized) address.text = "Turn on device location to show your position"
    }
    private val geocoder by lazy { Geocoder(this, Locale.getDefault()) }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state); fused = LocationServices.getFusedLocationProviderClient(this)
        setContentView(locationLayout)
        realtime = RealtimeLocationClient(this) { payload -> runOnUiThread { showFriendLocation(payload) } }
        realtime.connect()
        intent.getStringExtra("screen_title")?.let { findViewById<TextView>(R.id.live_title).text = it }
        intent.getStringExtra("screen_subtitle")?.let { findViewById<TextView>(R.id.live_subtitle).text = it }
        findViewById<ImageView>(R.id.live_back).setOnClickListener { finish() }
        if (friendsOnly) {
            findViewById<View>(R.id.self_location_controls).visibility = View.GONE
            findViewById<View>(R.id.friend_empty_state).visibility = View.GONE
            findViewById<View>(R.id.friend_people_button).setOnClickListener { toggleFriendsPanel() }
            findViewById<View>(R.id.friend_add_button).setOnClickListener { startActivity(Intent(this, TrackFriendRequestsActivity::class.java)) }
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
            lastLocation?.let { map.animateCamera(CameraUpdateFactory.newLatLng(LatLng(it.latitude, it.longitude))) }
                ?: if (!friendsOnly) requestPermission() else Unit
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
        if (!friendsOnly) requestPermission()
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
            .addOnSuccessListener { startLocationFeatures() }
            .addOnFailureListener { error ->
                if (error is ResolvableApiException) {
                    runCatching { locationSettingsLauncher.launch(IntentSenderRequest.Builder(error.resolution).build()) }
                        .onFailure { address.text = "Turn on device location to show your position" }
                } else {
                    address.text = "Turn on device location to show your position"
                }
            }
    }
    private fun startLocationFeatures() {
        if (!hasPermission() || friendsOnly) return
        getSharedPreferences("tracker_location", MODE_PRIVATE).edit().putBoolean("enabled", true).apply()
        ContextCompat.startForegroundService(this, Intent(this, LocationSharingService::class.java))
        enableLocation()
    }
    private fun showPermissionHelp() {
        val canRetry = ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION) ||
            ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        AlertDialog.Builder(this)
            .setTitle("Location access needed")
            .setMessage("Live Location needs location access and device Location turned on to show and share your position.")
            .setNegativeButton("Not now", null)
            .setPositiveButton(if (canRetry) "Allow" else "Open settings") { _, _ ->
                if (canRetry) requestPermission() else {
                    returningFromAppSettings = true
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                }
            }
            .show()
    }
    private fun enableLocation() { if (!::map.isInitialized || !hasPermission()) return; startLocationUpdates() }
    private fun startLocationUpdates() { callback?.let { fused.removeLocationUpdates(it) }; callback = object : LocationCallback() { override fun onLocationResult(r: LocationResult) { r.lastLocation?.let { updateLocation(it) } } }; val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L).setMinUpdateDistanceMeters(5f).build(); fused.requestLocationUpdates(request, callback!!, Looper.getMainLooper()); fused.lastLocation.addOnSuccessListener { it?.let { updateLocation(it) } } }
    private fun updateLocation(location: Location) { lastLocation = location; val point = LatLng(location.latitude, location.longitude); if (marker == null) { marker = map.addMarker(MarkerOptions().position(point).title("You are here")); map.animateCamera(CameraUpdateFactory.newLatLngZoom(point, 16f)) } else marker?.position = point; address.text = resolveAddress(location); realtime.sendLocation(location.latitude, location.longitude, batteryPercentage()) }
    private fun batteryPercentage(): Float = registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))?.let { intent -> val level = intent.getIntExtra("level", 0); val scale = intent.getIntExtra("scale", 100); level * 100f / scale.coerceAtLeast(1) } ?: 0f
    private fun showFriendLocation(payload: JSONObject) { if (!friendsOnly || !::map.isInitialized) return; val id = payload.optString("device_id"); if (id.isBlank()) return; findViewById<View>(R.id.friend_empty_state).visibility = View.GONE; val point = LatLng(payload.optDouble("latitude"), payload.optDouble("longitude")); val existing = friendMarkers[id]; if (existing == null) friendMarkers[id] = map.addMarker(MarkerOptions().position(point).title("Friend location"))!! else existing.position = point }
    private fun toggleFriendsPanel() {
        val panel = findViewById<View>(R.id.friend_empty_state)
        panel.visibility = if (panel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        if (panel.visibility != View.VISIBLE) return
        val rows = findViewById<LinearLayout>(R.id.friend_rows)
        val empty = findViewById<TextView>(R.id.friend_empty_message)
        val count = findViewById<TextView>(R.id.friend_count)
        rows.removeAllViews()
        count.text = "${friendMarkers.size} ${if (friendMarkers.size == 1) "friend" else "friends"}"
        empty.visibility = if (friendMarkers.isEmpty()) View.VISIBLE else View.GONE
        friendMarkers.keys.forEachIndexed { index, id ->
            val row = TextView(this).apply {
                text = "${id.take(1).uppercase(Locale.getDefault())}    Friend ${index + 1}                         •"
                textSize = 18f
                setTextColor(Color.rgb(12, 20, 38))
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(16, 14, 16, 14)
                setBackgroundResource(R.drawable.bg_phone_input)
            }
            rows.addView(row, LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 8 })
        }
    }
    private fun resolveAddress(l: Location) = try { geocoder.getFromLocation(l.latitude, l.longitude, 1)?.firstOrNull()?.getAddressLine(0) ?: "Location sharing is active" } catch (_: Exception) { "Location sharing is active" }
    private fun shareLocation() { val l = lastLocation; val text = if (l == null) "My live location is being updated from GPS Tracker." else "My location: https://maps.google.com/?q=" + l.latitude + "," + l.longitude; startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }, "Share location")) }
    override fun onResume() {
        super.onResume()
        if (returningFromAppSettings && !friendsOnly) {
            returningFromAppSettings = false
            requestPermission()
        }
    }
    override fun onDestroy() { callback?.let { fused.removeLocationUpdates(it) }; if (::realtime.isInitialized) realtime.close(); super.onDestroy() }
}

