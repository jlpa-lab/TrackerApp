package com.mobile.trackerapp.ui.livelocation
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.google.android.gms.location.*
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
    private lateinit var realtime: RealtimeLocationClient
    private val friendMarkers = mutableMapOf<String, Marker>()
    private val geocoder by lazy { Geocoder(this, Locale.getDefault()) }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state); fused = LocationServices.getFusedLocationProviderClient(this)
        setContentView(locationLayout)
        realtime = RealtimeLocationClient(this) { payload -> runOnUiThread { showFriendLocation(payload) } }
        realtime.connect()
        if (!friendsOnly) { getSharedPreferences("tracker_location", MODE_PRIVATE).edit().putBoolean("enabled", true).apply(); androidx.core.content.ContextCompat.startForegroundService(this, Intent(this, LocationSharingService::class.java)) }
        intent.getStringExtra("screen_title")?.let { findViewById<TextView>(R.id.live_title).text = it }
        intent.getStringExtra("screen_subtitle")?.let { findViewById<TextView>(R.id.live_subtitle).text = it }
        findViewById<TextView>(R.id.live_back).setOnClickListener { finish() }
        if (friendsOnly) {
            findViewById<View>(R.id.self_location_controls).visibility = View.GONE
            findViewById<View>(R.id.friend_empty_state).visibility = View.GONE
            findViewById<View>(R.id.friend_people_button).setOnClickListener { toggleFriendsPanel() }
            findViewById<View>(R.id.friend_add_button).setOnClickListener { startActivity(Intent(this, TrackFriendRequestsActivity::class.java)) }
            findViewById<View>(R.id.friend_received_button).setOnClickListener { startActivity(Intent(this, TrackFriendRequestsActivity::class.java).putExtra("request_tab", "received")) }
            findViewById<View>(R.id.friend_sent_button).setOnClickListener { startActivity(Intent(this, TrackFriendRequestsActivity::class.java).putExtra("request_tab", "sent")) }
        }
        address = findViewById(R.id.live_address)
        findViewById<TextView>(R.id.share_location).setOnClickListener { shareLocation() }
        val layers = findViewById<LinearLayout>(R.id.map_layers_menu)
        findViewById<View>(R.id.map_layers_button).setOnClickListener { layers.visibility = if (layers.visibility == View.VISIBLE) View.GONE else View.VISIBLE }
        findViewById<TextView>(R.id.map_normal).setOnClickListener { selectMapType(GoogleMap.MAP_TYPE_NORMAL, R.id.map_normal, layers) }
        findViewById<TextView>(R.id.map_satellite).setOnClickListener { selectMapType(GoogleMap.MAP_TYPE_SATELLITE, R.id.map_satellite, layers) }
        findViewById<TextView>(R.id.map_terrain).setOnClickListener { selectMapType(GoogleMap.MAP_TYPE_TERRAIN, R.id.map_terrain, layers) }
        findViewById<TextView>(R.id.map_hybrid).setOnClickListener { selectMapType(GoogleMap.MAP_TYPE_HYBRID, R.id.map_hybrid, layers) }
        findViewById<TextView>(R.id.map_zoom_in).setOnClickListener { if (::map.isInitialized) map.animateCamera(CameraUpdateFactory.zoomIn()) }
        findViewById<TextView>(R.id.map_zoom_out).setOnClickListener { if (::map.isInitialized) map.animateCamera(CameraUpdateFactory.zoomOut()) }
        findViewById<View>(R.id.map_focus).setOnClickListener { lastLocation?.let { map.animateCamera(CameraUpdateFactory.newLatLng(it.let { LatLng(it.latitude, it.longitude) })) } }
        supportFragmentManager.beginTransaction().replace(R.id.live_map, SupportMapFragment.newInstance()).commit()
        supportFragmentManager.executePendingTransactions()
        (supportFragmentManager.findFragmentById(R.id.live_map) as? SupportMapFragment)?.getMapAsync(this)
        if (!friendsOnly) requestPermission()
    }

    private fun selectMapType(type: Int, selected: Int, menu: View) {
        if (!::map.isInitialized) return
        map.mapType = type
        listOf(R.id.map_normal, R.id.map_satellite, R.id.map_terrain, R.id.map_hybrid).forEach {
            findViewById<TextView>(it).setTypeface(null, if (it == selected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        }
        menu.visibility = View.GONE
    }
    override fun onMapReady(ready: GoogleMap) { map = ready; map.uiSettings.isZoomControlsEnabled = false; map.uiSettings.isMyLocationButtonEnabled = false; if (!friendsOnly) enableLocation() }
    private fun requestPermission() { if (!hasPermission()) ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), REQUEST_LOCATION) else enableLocation() }
    override fun onRequestPermissionsResult(r: Int, p: Array<out String>, g: IntArray) { super.onRequestPermissionsResult(r, p, g); if (r == REQUEST_LOCATION && g.any { it == PackageManager.PERMISSION_GRANTED }) enableLocation() else address.text = "Location permission is required to show your position" }
    private fun hasPermission() = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
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
    override fun onDestroy() { callback?.let { fused.removeLocationUpdates(it) }; if (::realtime.isInitialized) realtime.close(); super.onDestroy() }
    companion object { private const val REQUEST_LOCATION = 40 }
}
