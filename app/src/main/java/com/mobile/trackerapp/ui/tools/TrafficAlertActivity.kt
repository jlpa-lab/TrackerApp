package com.location.and.phone.tracker.locationnphonetracker.ui.tools

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.location.and.phone.tracker.locationnphonetracker.R
import com.location.and.phone.tracker.locationnphonetracker.ads.AdRemoteConfig
import com.location.and.phone.tracker.locationnphonetracker.ads.AdsManager
import com.location.and.phone.tracker.locationnphonetracker.ads.banner_all
import com.location.and.phone.tracker.locationnphonetracker.databinding.ActivityNearbyBinding
import com.location.and.phone.tracker.locationnphonetracker.databinding.ActivityTrafficAlertBinding
import com.location.and.phone.tracker.locationnphonetracker.localization.LocaleManager
import com.location.and.phone.tracker.locationnphonetracker.ui.permission.LocationPermissionFlow
import kotlin.math.ceil
import kotlin.math.max

/** Live Google Maps traffic overlay centered on the user's current location. */
class TrafficAlertActivity : AppCompatActivity(), OnMapReadyCallback {

    private val fusedLocation by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private lateinit var binding: ActivityTrafficAlertBinding
    private lateinit var locationHint: TextView
    private lateinit var statusTitle: TextView
    private lateinit var statusDetail: TextView
    private lateinit var liveTrafficLabel: TextView
    private var map: GoogleMap? = null
    private var locationCallback: LocationCallback? = null
    private var cameraCentered = false
    private var lastLocation: Location? = null
    private var checkingLocationSettings = false
    private var resolvingLocationSettings = false
    private var locationUpdatesActive = false

    private val locationPermissionFlow by lazy {
        LocationPermissionFlow(
            activity = this,
            onGranted = { ensureLocationSettings() },
            onDenied = { showLocationMessage("Location permission is required to show live traffic near you") }
        )
    }

    private val locationSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        resolvingLocationSettings = false
        if (result.resultCode == Activity.RESULT_OK) {
            startLocationUpdates()
        } else {
            showLocationMessage("Turn on device location to center the traffic map")
        }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        LocaleManager.applyPersisted(this)
        locationPermissionFlow
        window.statusBarColor = Color.rgb(246, 251, 248)
        window.navigationBarColor = Color.rgb(246, 251, 248)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        hideSystemNavigation()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityTrafficAlertBinding.inflate(layoutInflater)
        setContentView(binding.root)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                AdsManager.showInterHomeBack(this@TrafficAlertActivity) { _ ->
                    finish()
                }
            }
        })
        AdsManager.loadInterHome(this, ignoreLimit = true)
        binding.trafficBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        loadTrafficAlertBanner()
        locationHint = binding.trafficLocationHint
        statusTitle = binding.trafficStatusTitle
        statusDetail = binding.trafficStatusDetail
        liveTrafficLabel = binding.trafficLiveLabel
        updateTrafficSummary()
        locationHint.setOnClickListener { requestLocationAccess() }
        locationHint.visibility = View.VISIBLE
        val mapFragment = supportFragmentManager.findFragmentById(R.id.traffic_map) as? SupportMapFragment
        if (mapFragment == null) {
            showLocationMessage("Unable to load the traffic map")
        } else {
            mapFragment.getMapAsync(this)
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap.apply {
            isTrafficEnabled = true
            uiSettings.isZoomControlsEnabled = true
            uiSettings.isMyLocationButtonEnabled = false
            uiSettings.isCompassEnabled = true
            uiSettings.isScrollGesturesEnabled = true
            uiSettings.isZoomGesturesEnabled = true
            setOnCameraIdleListener { updateTrafficSummary() }
        }
        (supportFragmentManager.findFragmentById(R.id.traffic_map) as? SupportMapFragment)
            ?.view
            ?.let { mapView ->
                mapView.setOnTouchListener { view, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE ->
                            view.parent?.requestDisallowInterceptTouchEvent(true)
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                            view.parent?.requestDisallowInterceptTouchEvent(false)
                    }
                    false
                }
            }
        updateTrafficSummary()
        requestLocationAccess()
    }
    private fun loadTrafficAlertBanner() {
        val bannerConfig = AdRemoteConfig.banner_all
        AdsManager.loadBanner(
            this, bannerConfig, binding.frBanner, isCollapse = false
        )
    }
    private fun requestLocationAccess() {
        locationPermissionFlow.request()
    }

    private fun ensureLocationSettings() {
        if (checkingLocationSettings || resolvingLocationSettings || locationUpdatesActive) return
        checkingLocationSettings = true
        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            LOCATION_INTERVAL_MS
        ).setMinUpdateDistanceMeters(LOCATION_DISTANCE_METERS).build()
        val settingsRequest = LocationSettingsRequest.Builder()
            .addLocationRequest(request)
            .setAlwaysShow(true)
            .build()

        LocationServices.getSettingsClient(this)
            .checkLocationSettings(settingsRequest)
            .addOnSuccessListener {
                checkingLocationSettings = false
                startLocationUpdates()
            }
            .addOnFailureListener { error ->
                checkingLocationSettings = false
                if (error is ResolvableApiException) {
                    runCatching {
                        resolvingLocationSettings = true
                        locationSettingsLauncher.launch(
                            IntentSenderRequest.Builder(error.resolution).build()
                        )
                    }.onFailure {
                        resolvingLocationSettings = false
                        showLocationMessage("Turn on device location to center the traffic map")
                    }
                } else {
                    showLocationMessage("Turn on device location to center the traffic map")
                }
            }
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
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_INTERVAL_MS)
                .setMinUpdateDistanceMeters(LOCATION_DISTANCE_METERS)
                .build()
            fusedLocation.requestLocationUpdates(request, callback, mainLooper)
                .addOnSuccessListener { locationUpdatesActive = true }
                .addOnFailureListener {
                    locationUpdatesActive = false
                    showLocationMessage("Unable to read your location. Tap to try again")
                }
            .addOnCompleteListener { updateTrafficSummary() }
        } catch (_: SecurityException) {
            locationUpdatesActive = false
            currentMap.isMyLocationEnabled = false
            locationHint.visibility = View.VISIBLE
        }
    }

    private fun showLocation(location: Location, animate: Boolean) {
        lastLocation = location
        if (!cameraCentered) {
            cameraCentered = true
            val point = LatLng(location.latitude, location.longitude)
            val update = CameraUpdateFactory.newLatLngZoom(point, CAMERA_ZOOM)
            if (animate) map?.animateCamera(update) else map?.moveCamera(update)
        }
        updateTrafficSummary()
    }

    private fun hasLocationPermission() = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun showLocationMessage(message: String) {
        locationHint.text = message
        locationHint.visibility = View.VISIBLE
        updateTrafficSummary()
    }

    private fun updateTrafficSummary() {
        if (!::statusTitle.isInitialized) return
        val currentMap = map
        val locationReady = currentMap != null && lastLocation != null && locationUpdatesActive
        if (!locationReady) {
            statusTitle.setText(if (!hasLocationPermission()) R.string.traffic_location_needed else R.string.traffic_waiting)
            statusDetail.setText(if (!hasLocationPermission()) R.string.traffic_allow_location else R.string.traffic_centering)
            liveTrafficLabel.setText(R.string.traffic_location_pending)
            return
        }
        val radiusKm = visibleTrafficRadiusKm(currentMap!!)
        statusTitle.setText(R.string.traffic_available)
        statusDetail.text = getString(R.string.traffic_conditions_within, radiusKm)
        liveTrafficLabel.text = getString(R.string.traffic_live_radius, radiusKm)
    }

    private fun visibleTrafficRadiusKm(currentMap: GoogleMap): Int {
        val bounds = runCatching { currentMap.projection.visibleRegion.latLngBounds }.getOrNull() ?: return DEFAULT_RADIUS_KM
        val center = bounds.center
        val corners = listOf(bounds.northeast, bounds.southwest, LatLng(bounds.northeast.latitude, bounds.southwest.longitude), LatLng(bounds.southwest.latitude, bounds.northeast.longitude))
        val maxMeters = corners.maxOfOrNull { corner ->
            val result = FloatArray(1)
            Location.distanceBetween(center.latitude, center.longitude, corner.latitude, corner.longitude, result)
            result[0]
        } ?: return DEFAULT_RADIUS_KM
        return max(1, ceil(maxMeters / 1000f).toInt())
    }

    override fun onResume() {
        super.onResume()
        locationPermissionFlow.onHostResume()
        if (map != null && hasLocationPermission() && !resolvingLocationSettings) {
            ensureLocationSettings()
        }
    }

    override fun onPause() {
        locationCallback?.let(fusedLocation::removeLocationUpdates)
        locationCallback = null
        locationUpdatesActive = false
        super.onPause()
    }

    override fun onDestroy() {
        locationCallback?.let(fusedLocation::removeLocationUpdates)
        locationCallback = null
        locationUpdatesActive = false
        super.onDestroy()
    }

    private fun hideSystemNavigation() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.decorView.windowInsetsController?.apply {
                hide(android.view.WindowInsets.Type.navigationBars())
                systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }
    }

    companion object {
        private const val LOCATION_INTERVAL_MS = 5_000L
        private const val LOCATION_DISTANCE_METERS = 5f
        private const val CAMERA_ZOOM = 16f
        private const val DEFAULT_RADIUS_KM = 5
    }
}
