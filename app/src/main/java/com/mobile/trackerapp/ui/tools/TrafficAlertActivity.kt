package com.mobile.trackerapp.ui.tools

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.LatLng
import com.mobile.trackerapp.R
import com.mobile.trackerapp.ui.DashboardActivity

/** Live Google Maps traffic overlay centered on the user's current location. */
class TrafficAlertActivity : DashboardActivity(), OnMapReadyCallback {
    private val ink = Color.rgb(13, 22, 39)
    private val muted = Color.rgb(119, 130, 148)
    private val green = Color.rgb(0, 164, 76)
    private val fusedLocation by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private lateinit var mapView: MapView
    private lateinit var locationHint: TextView
    private var map: GoogleMap? = null
    private var locationCallback: LocationCallback? = null
    private var cameraCentered = false
    private var lastLocation: Location? = null

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.statusBarColor = Color.rgb(246, 251, 248)
        window.navigationBarColor = Color.rgb(246, 251, 248)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        hideNavigationBar()
        val binding = com.mobile.trackerapp.databinding.ActivityTrafficAlertBinding.inflate(layoutInflater)
        setContentView(binding.root); binding.trafficBack.setOnClickListener { finish() }
        mapView = binding.trafficMap; locationHint = binding.trafficLocationHint
        locationHint.setOnClickListener { requestLocationPermission() }; locationHint.visibility = if (hasLocationPermission()) View.GONE else View.VISIBLE
        mapView.onCreate(state)
        mapView.getMapAsync(this)
    }

    private fun addLegendItem(parent: LinearLayout, color: Int, title: String) {
        val item = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        item.addView(dot(color), LinearLayout.LayoutParams(dp(12), dp(12)).apply { marginEnd = dp(5) })
        item.addView(label(title, 13f, muted))
        parent.addView(item, LinearLayout.LayoutParams(wrap(), wrap()).apply {
            if (parent.childCount > 0) marginStart = dp(14)
        })
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap.apply {
            isTrafficEnabled = true
            uiSettings.isZoomControlsEnabled = false
            uiSettings.isMyLocationButtonEnabled = false
            uiSettings.isCompassEnabled = true
        }
        if (hasLocationPermission()) startLocationUpdates() else requestLocationPermission()
    }

    private fun startLocationUpdates() {
        val currentMap = map ?: return
        if (!hasLocationPermission()) return
        try {
            currentMap.isMyLocationEnabled = true
            locationHint.visibility = View.GONE
            fusedLocation.lastLocation.addOnSuccessListener(this) { location ->
                if (location != null) showLocation(location, animate = false)
            }
            locationCallback?.let(fusedLocation::removeLocationUpdates)
            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let { showLocation(it, animate = true) }
                }
            }
            locationCallback = callback
            val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, LOCATION_INTERVAL_MS)
                .setMinUpdateDistanceMeters(LOCATION_DISTANCE_METERS)
                .build()
            fusedLocation.requestLocationUpdates(request, callback, mainLooper)
        } catch (_: SecurityException) {
            currentMap.isMyLocationEnabled = false
            locationHint.visibility = View.VISIBLE
        }
    }

    private fun showLocation(location: Location, animate: Boolean) {
        lastLocation = location
        if (cameraCentered) return
        cameraCentered = true
        val point = LatLng(location.latitude, location.longitude)
        val update = CameraUpdateFactory.newLatLngZoom(point, CAMERA_ZOOM)
        if (animate) map?.animateCamera(update) else map?.moveCamera(update)
    }

    private fun hasLocationPermission() = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), LOCATION_PERMISSION_REQUEST)
    }

    override fun onResume() {
        super.onResume()
        if (::mapView.isInitialized) mapView.onResume()
        if (map != null && hasLocationPermission()) startLocationUpdates()
    }

    override fun onStart() {
        super.onStart()
        if (::mapView.isInitialized) mapView.onStart()
    }

    override fun onPause() {
        locationCallback?.let(fusedLocation::removeLocationUpdates)
        locationCallback = null
        if (::mapView.isInitialized) mapView.onPause()
        super.onPause()
    }

    override fun onStop() {
        if (::mapView.isInitialized) mapView.onStop()
        super.onStop()
    }

    override fun onDestroy() {
        locationCallback?.let(fusedLocation::removeLocationUpdates)
        locationCallback = null
        if (::mapView.isInitialized) mapView.onDestroy()
        super.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        if (::mapView.isInitialized) mapView.onLowMemory()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::mapView.isInitialized) mapView.onSaveInstanceState(outState)
    }

    @Deprecated("Deprecated in Android")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (hasLocationPermission()) startLocationUpdates() else locationHint.visibility = View.VISIBLE
        }
    }

    private fun label(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.create("sans-serif", Typeface.BOLD)
    }

    private fun dot(color: Int) = View(this).apply {
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
    }

    private fun rounded(color: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
    }

    private inner class AspectMapFrame : FrameLayout(this@TrafficAlertActivity) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            val desiredHeight = (width * MAP_HEIGHT_RATIO).toInt()
            val height = resolveSize(desiredHeight, heightMeasureSpec)
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
        }
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST = 9401
        private const val LOCATION_INTERVAL_MS = 5_000L
        private const val LOCATION_DISTANCE_METERS = 10f
        private const val CAMERA_ZOOM = 15.5f
        private const val MAP_HEIGHT_RATIO = 1.55f
    }
}
