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
        setContentView(buildScreen())
        mapView.onCreate(state)
        mapView.getMapAsync(this)
    }

    private fun buildScreen(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(246, 251, 248))
            fitsSystemWindows = true
        }
        val scroll = ScrollView(this).apply { isFillViewport = true; clipToPadding = false }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(14))
        }
        scroll.addView(content, android.view.ViewGroup.LayoutParams(-1, -2))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val back = ImageView(this).apply {
            setImageResource(R.drawable.ic_phone_back)
            imageTintList = android.content.res.ColorStateList.valueOf(ink)
            contentDescription = "Back"
            isClickable = true
            isFocusable = true
            setOnClickListener { finish() }
        }
        header.addView(back, LinearLayout.LayoutParams(dp(28), dp(34)).apply { marginEnd = dp(14) })
        val titleBlock = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleBlock.addView(label("Traffic Alert", 28f, ink, true))
        titleBlock.addView(label("Stay one step ahead on the road", 15f, muted).apply { setPadding(0, dp(3), 0, 0) })
        header.addView(titleBlock, LinearLayout.LayoutParams(0, wrap(), 1f))
        content.addView(header)

        val summary = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(14), dp(14))
            background = rounded(Color.rgb(255, 241, 230), dp(22))
        }
        val summaryText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        summaryText.addView(label("Live traffic around you", 20f, ink, true))
        summaryText.addView(label("Current road conditions are shown on the map", 14f, Color.rgb(190, 83, 29)).apply { setPadding(0, dp(4), 0, 0) })
        summary.addView(summaryText, LinearLayout.LayoutParams(0, wrap(), 1f))
        summary.addView(label("🚗", 36f, ink), LinearLayout.LayoutParams(wrap(), wrap()).apply { marginStart = dp(8) })
        content.addView(summary, LinearLayout.LayoutParams(-1, wrap()).apply { topMargin = dp(14) })

        val mapFrame = AspectMapFrame().apply {
            clipToOutline = true
            background = rounded(Color.rgb(236, 241, 240), dp(18))
            elevation = dp(1).toFloat()
        }
        mapView = MapView(this)
        mapFrame.addView(mapView, FrameLayout.LayoutParams(-1, -1))

        val trafficTag = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = rounded(Color.WHITE, dp(24))
            elevation = dp(3).toFloat()
            addView(dot(green), LinearLayout.LayoutParams(dp(8), dp(8)).apply { marginEnd = dp(7) })
            addView(label("Live traffic", 13f, ink, true))
        }
        mapFrame.addView(trafficTag, FrameLayout.LayoutParams(wrap(), wrap(), Gravity.TOP or Gravity.START).apply {
            topMargin = dp(12); marginStart = dp(12)
        })

        locationHint = label("Enable location to center the traffic map", 12f, ink, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(13), dp(10), dp(13), dp(10))
            background = rounded(Color.WHITE, dp(24))
            elevation = dp(3).toFloat()
            isClickable = true
            isFocusable = true
            setOnClickListener { requestLocationPermission() }
            visibility = if (hasLocationPermission()) View.GONE else View.VISIBLE
        }
        mapFrame.addView(locationHint, FrameLayout.LayoutParams(wrap(), wrap(), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = dp(14)
        })
        content.addView(mapFrame, LinearLayout.LayoutParams(-1, wrap()).apply { topMargin = dp(12) })

        val legend = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(2))
        }
        addLegendItem(legend, Color.rgb(0, 166, 76), "Clear")
        addLegendItem(legend, Color.rgb(239, 164, 45), "Moderate")
        addLegendItem(legend, Color.rgb(239, 98, 82), "Heavy")
        content.addView(legend, LinearLayout.LayoutParams(-1, wrap()))
        return root
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
