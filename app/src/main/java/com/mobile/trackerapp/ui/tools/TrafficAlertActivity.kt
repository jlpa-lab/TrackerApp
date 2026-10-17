package com.mobile.trackerapp.ui.tools

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.TextView
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
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
import com.mobile.trackerapp.R
import com.mobile.trackerapp.databinding.ActivityTrafficAlertBinding

/** Live Google Maps traffic overlay centered on the user's current location. */
class TrafficAlertActivity : AppCompatActivity(), OnMapReadyCallback {
    private val fusedLocation by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private lateinit var binding: ActivityTrafficAlertBinding
    private lateinit var locationHint: TextView
    private var map: GoogleMap? = null
    private var locationCallback: LocationCallback? = null
    private var cameraCentered = false
    private var lastLocation: Location? = null
    private var returningFromAppSettings = false
    private var checkingLocationSettings = false
    private var resolvingLocationSettings = false
    private var locationUpdatesActive = false

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            ensureLocationSettings()
        } else {
            showLocationMessage("Location permission is required to show live traffic near you")
            showPermissionHelp()
        }
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
        window.statusBarColor = Color.rgb(246, 251, 248)
        window.navigationBarColor = Color.rgb(246, 251, 248)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        hideSystemNavigation()
        binding = ActivityTrafficAlertBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.trafficBack.setOnClickListener { finish() }
        locationHint = binding.trafficLocationHint
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
            uiSettings.isZoomControlsEnabled = false
            uiSettings.isMyLocationButtonEnabled = false
            uiSettings.isCompassEnabled = true
        }
        requestLocationAccess()
    }

    private fun requestLocationAccess() {
        if (hasLocationPermission()) {
            ensureLocationSettings()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
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
        } catch (_: SecurityException) {
            locationUpdatesActive = false
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

    private fun showPermissionHelp() {
        val canRetry = ActivityCompat.shouldShowRequestPermissionRationale(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) || ActivityCompat.shouldShowRequestPermissionRationale(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        AlertDialog.Builder(this)
            .setTitle("Location access needed")
            .setMessage("Traffic Alert needs location access and device Location turned on to show live traffic around you.")
            .setNegativeButton("Not now", null)
            .setPositiveButton(if (canRetry) "Allow" else "Open settings") { _, _ ->
                if (canRetry) {
                    requestLocationAccess()
                } else {
                    returningFromAppSettings = true
                    startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:$packageName")
                        )
                    )
                }
            }
            .show()
    }

    private fun showLocationMessage(message: String) {
        locationHint.text = message
        locationHint.visibility = View.VISIBLE
    }

    override fun onResume() {
        super.onResume()
        if (returningFromAppSettings) {
            returningFromAppSettings = false
            requestLocationAccess()
        } else if (map != null && hasLocationPermission() && !resolvingLocationSettings) {
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
    }
}
