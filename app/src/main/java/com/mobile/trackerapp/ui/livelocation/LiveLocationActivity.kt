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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.google.android.gms.location.*
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import com.mobile.trackerapp.R
import java.util.Locale

class LiveLocationActivity : FragmentActivity(), OnMapReadyCallback {
    private lateinit var map: GoogleMap
    private lateinit var fused: FusedLocationProviderClient
    private lateinit var address: TextView
    private var marker: Marker? = null
    private var callback: LocationCallback? = null
    private var lastLocation: Location? = null
    private val geocoder by lazy { Geocoder(this, Locale.getDefault()) }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state); fused = LocationServices.getFusedLocationProviderClient(this)
        setContentView(R.layout.activity_live_location)
        intent.getStringExtra("screen_title")?.let { findViewById<TextView>(R.id.live_title).text = it }
        intent.getStringExtra("screen_subtitle")?.let { findViewById<TextView>(R.id.live_subtitle).text = it }
        findViewById<TextView>(R.id.live_back).setOnClickListener { finish() }
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
        findViewById<View>(R.id.map_friends)?.setOnClickListener { startActivity(Intent(this, com.mobile.trackerapp.ui.connection.FriendRequestsActivity::class.java)) }
        supportFragmentManager.beginTransaction().replace(R.id.live_map, SupportMapFragment.newInstance()).commit()
        supportFragmentManager.executePendingTransactions()
        (supportFragmentManager.findFragmentById(R.id.live_map) as? SupportMapFragment)?.getMapAsync(this)
        requestPermission()
    }

    private fun selectMapType(type: Int, selected: Int, menu: View) {
        if (!::map.isInitialized) return
        map.mapType = type
        listOf(R.id.map_normal, R.id.map_satellite, R.id.map_terrain, R.id.map_hybrid).forEach {
            findViewById<TextView>(it).setTypeface(null, if (it == selected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        }
        menu.visibility = View.GONE
    }
    override fun onMapReady(ready: GoogleMap) { map = ready; map.uiSettings.isZoomControlsEnabled = false; map.uiSettings.isMyLocationButtonEnabled = false; enableLocation() }
    private fun requestPermission() { if (!hasPermission()) ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), REQUEST_LOCATION) else enableLocation() }
    override fun onRequestPermissionsResult(r: Int, p: Array<out String>, g: IntArray) { super.onRequestPermissionsResult(r, p, g); if (r == REQUEST_LOCATION && g.any { it == PackageManager.PERMISSION_GRANTED }) enableLocation() else address.text = "Location permission is required to show your position" }
    private fun hasPermission() = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    private fun enableLocation() { if (!::map.isInitialized || !hasPermission()) return; startLocationUpdates() }
    private fun startLocationUpdates() { callback?.let { fused.removeLocationUpdates(it) }; callback = object : LocationCallback() { override fun onLocationResult(r: LocationResult) { r.lastLocation?.let { updateLocation(it) } } }; val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L).setMinUpdateDistanceMeters(5f).build(); fused.requestLocationUpdates(request, callback!!, Looper.getMainLooper()); fused.lastLocation.addOnSuccessListener { it?.let { updateLocation(it) } } }
    private fun updateLocation(location: Location) { lastLocation = location; val point = LatLng(location.latitude, location.longitude); if (marker == null) { marker = map.addMarker(MarkerOptions().position(point).title("You are here")); map.animateCamera(CameraUpdateFactory.newLatLngZoom(point, 16f)) } else marker?.position = point; address.text = resolveAddress(location) }
    private fun resolveAddress(l: Location) = try { geocoder.getFromLocation(l.latitude, l.longitude, 1)?.firstOrNull()?.getAddressLine(0) ?: "Location sharing is active" } catch (_: Exception) { "Location sharing is active" }
    private fun shareLocation() { val l = lastLocation; val text = if (l == null) "My live location is being updated from GPS Tracker." else "My location: https://maps.google.com/?q=" + l.latitude + "," + l.longitude; startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }, "Share location")) }
    override fun onDestroy() { callback?.let { fused.removeLocationUpdates(it) }; super.onDestroy() }
    companion object { private const val REQUEST_LOCATION = 40 }
}
