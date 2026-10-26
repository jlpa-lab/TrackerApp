package com.location.and.phone.tracker.locationnphonetracker.ui.tools

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewParent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.Circle
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.location.and.phone.tracker.locationnphonetracker.R
import com.location.and.phone.tracker.locationnphonetracker.ads.AdRemoteConfig
import com.location.and.phone.tracker.locationnphonetracker.ads.AdsManager
import com.location.and.phone.tracker.locationnphonetracker.ads.banner_all
import com.location.and.phone.tracker.locationnphonetracker.databinding.ActivityCreateZoneBinding
import com.location.and.phone.tracker.locationnphonetracker.databinding.ActivityZoneAlertBinding
import com.location.and.phone.tracker.locationnphonetracker.ui.DashboardActivity
import com.location.and.phone.tracker.locationnphonetracker.ui.ZoneToggleView
import com.location.and.phone.tracker.locationnphonetracker.ui.permission.LocationPermissionFlow
import java.util.Locale
import java.util.concurrent.Executors

/** Map-based create/edit screen for a saved geofence. */
class CreateZoneActivity : DashboardActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityCreateZoneBinding
    private val ink = Color.rgb(17, 24, 39)
    private val muted = Color.rgb(125, 137, 153)
    private val green = Color.rgb(0, 164, 76)
    private val red = Color.rgb(225, 76, 70)
    private val worker = Executors.newSingleThreadExecutor()
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private lateinit var mapView: MapView
    private lateinit var zoneName: EditText
    private lateinit var searchInput: EditText
    private lateinit var addressText: TextView
    private lateinit var radiusText: TextView
    private lateinit var radiusBar: SeekBar
    private lateinit var safeButton: LinearLayout
    private lateinit var dangerButton: LinearLayout
    private lateinit var safeButtonLabel: TextView
    private lateinit var dangerButtonLabel: TextView
    private lateinit var saveButton: TextView
    private lateinit var enterCheck: ZoneToggleView
    private lateinit var exitCheck: ZoneToggleView
    private lateinit var locationHint: TextView
    private var map: GoogleMap? = null
    private var circle: Circle? = null
    private var locationCallback: LocationCallback? = null
    private var selectedLocation: LatLng? = null
    private var selectedAddress = ""
    private var radiusMeters = 100
    private var isSafe = true
    private var zoneId = NO_ZONE
    private var userMovedMap = false
    private var cameraCentered = false
    private val locationPermissionFlow by lazy {
        LocationPermissionFlow(
            activity = this,
            onGranted = {
                if (::locationHint.isInitialized) locationHint.visibility = View.GONE
                moveToCurrentLocation()
            },
            onDenied = {
                if (::locationHint.isInitialized) locationHint.visibility = View.VISIBLE
            }
        )
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        locationPermissionFlow
        window.statusBarColor = Color.rgb(246, 251, 248)
        window.navigationBarColor = Color.rgb(246, 251, 248)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        hideNavigationBar()
        binding = ActivityCreateZoneBinding.inflate(layoutInflater)
        zoneId = intent.getLongExtra(EXTRA_ZONE_ID, NO_ZONE)
        setContentView(binding.root); binding.createZoneBack.setOnClickListener { finish() }
        loadCreateZoneBanner()
        mapView=binding.createZoneMap; zoneName=binding.createZoneName; searchInput=binding.createZoneSearch; addressText=binding.createZoneAddress; radiusText=binding.createZoneRadiusText; radiusBar=binding.createZoneRadius; safeButton=binding.createZoneSafe; dangerButton=binding.createZoneDanger; safeButtonLabel=binding.createZoneSafeLabel; dangerButtonLabel=binding.createZoneDangerLabel; saveButton=binding.createZoneSave; enterCheck=binding.createZoneEnter; exitCheck=binding.createZoneExit; enterCheck.isChecked = true; exitCheck.isChecked = true; locationHint=binding.createZoneLocationHint
        binding.createZoneSearchButton.setOnClickListener { searchLocation() }; binding.createZoneLocate.setOnClickListener { moveToCurrentLocation() }; safeButton.setOnClickListener { isSafe = true; renderTypeButtons() }; dangerButton.setOnClickListener { isSafe = false; renderTypeButtons() }; saveButton.setOnClickListener { saveZone() }
        searchInput.setOnEditorActionListener { _, action, _ -> if (action == EditorInfo.IME_ACTION_SEARCH) { searchLocation(); true } else false }
        radiusBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener { override fun onStartTrackingTouch(seekBar: SeekBar)=Unit; override fun onStopTrackingTouch(seekBar: SeekBar)=Unit; override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) { radiusMeters=(progress+1)*100; radiusText.text=if(radiusMeters==1000) "1,000m" else "${radiusMeters}m"; drawCircle() } })
        renderTypeButtons()
        mapView.onCreate(state)
        mapView.getMapAsync(this)
        if (zoneId != NO_ZONE) loadZone(zoneId)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            val focusedView = currentFocus
            if (focusedView is EditText && !isTouchInside(focusedView, event.rawX, event.rawY)) {
                hideKeyboard(focusedView)
            }
        }
        return super.dispatchTouchEvent(event)
    }

    private fun isTouchInside(view: View, rawX: Float, rawY: Float): Boolean {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return rawX >= location[0] && rawX <= location[0] + view.width &&
            rawY >= location[1] && rawY <= location[1] + view.height
    }

    private fun typeButton(title: String) = text(title, 13f, muted, true).apply {
        gravity = Gravity.CENTER; setPadding(dp(7), dp(10), dp(7), dp(10)); isClickable = true; isFocusable = true
    }
    private fun loadCreateZoneBanner() {
        val bannerConfig = AdRemoteConfig.banner_all
        AdsManager.loadBanner(
            this, bannerConfig, binding.frBanner, isCollapse = false
        )
    }
    private fun renderTypeButtons() {
        safeButton.background = typeBordered(if (isSafe) Color.rgb(237, 250, 245) else Color.WHITE, if (isSafe) green else Color.rgb(229, 237, 233), dp(12))
        safeButtonLabel.setTextColor(if (isSafe) green else muted)
        dangerButton.background = typeBordered(if (!isSafe) Color.rgb(255, 242, 241) else Color.WHITE, if (!isSafe) red else Color.rgb(229, 237, 233), dp(12))
        dangerButtonLabel.setTextColor(if (!isSafe) red else muted)
        drawCircle()
    }

    private fun loadZone(id: Long) {
        worker.execute {
            val existing = ZoneStore.get(this).get(id) ?: return@execute
            runOnUiThread {
                zoneName.setText(existing.name)
                searchInput.setText("")
                addressText.text = existing.address
                selectedAddress = existing.address
                selectedLocation = LatLng(existing.latitude, existing.longitude)
                radiusMeters = existing.radiusMeters.coerceIn(100, 1000)
                radiusBar.progress = radiusMeters / 100 - 1
                isSafe = existing.safe
                enterCheck.isChecked = existing.alertOnEnter
                exitCheck.isChecked = existing.alertOnExit
                saveButton.isEnabled = true; saveButton.alpha = 1f
                renderTypeButtons()
                selectedLocation?.let { map?.animateCamera(CameraUpdateFactory.newLatLngZoom(it, 15f)) }
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap.apply {
            uiSettings.isZoomControlsEnabled = false; uiSettings.isMyLocationButtonEnabled = false
            uiSettings.isCompassEnabled = true; uiSettings.isMapToolbarEnabled = false; uiSettings.isScrollGesturesEnabled = true; uiSettings.isZoomGesturesEnabled = true
            setOnCameraMoveStartedListener { reason -> if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) userMovedMap = true }
            setOnCameraIdleListener {
                if (userMovedMap) {
                    selectedLocation = cameraPosition.target
                    selectedAddress = "%.5f°, %.5f°".format(Locale.getDefault(), cameraPosition.target.latitude, cameraPosition.target.longitude)
                    addressText.text = selectedAddress
                    saveButton.isEnabled = true; saveButton.alpha = 1f
                    drawCircle()
                }
            }
        }
        if (zoneId != NO_ZONE) loadZone(zoneId)
        else if (hasForegroundLocation()) moveToCurrentLocation()
        else {
            locationHint.visibility = android.view.View.VISIBLE
            requestForegroundLocation()
        }
    }

    private fun moveToCurrentLocation() {
        if (!hasForegroundLocation()) { requestForegroundLocation(); return }
        fused.lastLocation.addOnSuccessListener(this) { location ->
            if (location != null) setSelectedLocation(LatLng(location.latitude, location.longitude), "Current location")
        }
    }

    private fun setSelectedLocation(location: LatLng, address: String) {
        selectedLocation = location
        selectedAddress = address
        addressText.text = address
        saveButton.isEnabled = true; saveButton.alpha = 1f
        cameraCentered = true
        userMovedMap = false
        map?.animateCamera(CameraUpdateFactory.newLatLngZoom(location, 16f))
        drawCircle()
    }

    private fun drawCircle() {
        val currentMap = map ?: return
        val center = selectedLocation ?: return
        circle?.remove()
        val stroke = if (isSafe) green else red
        circle = currentMap.addCircle(CircleOptions().center(center).radius(radiusMeters.toDouble())
            .strokeColor(stroke).strokeWidth(dp(2).toFloat())
            .fillColor(if (isSafe) 0x2230B66A else 0x22E54A46))
    }

    private fun searchLocation() {
        val query = searchInput.text?.toString()?.trim().orEmpty()
        if (query.isEmpty()) { searchInput.error = getString(R.string.zone_place_address_error); return }
        hideKeyboard()
        worker.execute {
            try {
                @Suppress("DEPRECATION")
                val result = Geocoder(this, Locale.getDefault()).getFromLocationName(query, 1)?.firstOrNull()
                runOnUiThread {
                    if (result == null) Toast.makeText(this, R.string.zone_location_not_found, Toast.LENGTH_SHORT).show()
                    else {
                        val prettyAddress = result.getAddressLine(0) ?: query
                        setSelectedLocation(LatLng(result.latitude, result.longitude), prettyAddress)
                    }
                }
            } catch (_: Exception) {
                runOnUiThread { Toast.makeText(this, R.string.zone_search_failed, Toast.LENGTH_SHORT).show() }
            }
        }
    }

    private fun saveZone() {
        val name = zoneName.text?.toString()?.trim().orEmpty()
        if (name.isBlank()) { zoneName.error = getString(R.string.zone_name_error); return }
        if (!enterCheck.isChecked && !exitCheck.isChecked) {
            Toast.makeText(this, R.string.zone_select_alert_type, Toast.LENGTH_SHORT).show()
            return
        }
        val point = selectedLocation
        if (point == null) { Toast.makeText(this, R.string.zone_choose_map_location, Toast.LENGTH_SHORT).show(); return }
        val old = if (zoneId == NO_ZONE) null else ZoneStore.get(this).get(zoneId)
        val zone = ZoneRecord(
            id = old?.id ?: 0L,
            name = name,
            address = selectedAddress.ifBlank { "%.5f°, %.5f°".format(Locale.getDefault(), point.latitude, point.longitude) },
            latitude = point.latitude,
            longitude = point.longitude,
            radiusMeters = radiusMeters,
            safe = isSafe,
            alertOnEnter = enterCheck.isChecked,
            alertOnExit = exitCheck.isChecked,
            enabled = old?.enabled ?: true
        )
        saveButton.isEnabled = false
        worker.execute {
            ZoneStore.get(this).save(zone)
            runOnUiThread {
                Toast.makeText(this, R.string.zone_saved, Toast.LENGTH_SHORT).show()
                setResult(Activity.RESULT_OK)
                finish()
            }
        }
    }

    private fun hideKeyboard() {
        hideKeyboard(currentFocus)
    }

    private fun hideKeyboard(target: View?) {
        val view = target ?: window.decorView
        (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.hideSoftInputFromWindow(view.windowToken, 0)
        target?.clearFocus()
    }

    private fun hasForegroundLocation() = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun requestForegroundLocation() {
        locationPermissionFlow.request()
    }

    override fun onResume() { super.onResume(); if (::mapView.isInitialized) mapView.onResume() }
    override fun onStart() { super.onStart(); if (::mapView.isInitialized) mapView.onStart() }

    override fun onPause() {
        locationCallback?.let(fused::removeLocationUpdates)
        locationCallback = null
        if (::mapView.isInitialized) mapView.onPause()
        super.onPause()
    }

    override fun onStop() { if (::mapView.isInitialized) mapView.onStop(); super.onStop() }
    override fun onDestroy() { worker.shutdownNow(); if (::mapView.isInitialized) mapView.onDestroy(); super.onDestroy() }
    override fun onLowMemory() { super.onLowMemory(); if (::mapView.isInitialized) mapView.onLowMemory() }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); if (::mapView.isInitialized) mapView.onSaveInstanceState(outState) }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color)
        if (bold) typeface = Typeface.create("sans-serif", Typeface.BOLD)
    }

    private fun rounded(color: Int, radiusDp: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radiusDp).toFloat() }
    private fun bordered(color: Int, stroke: Int, radiusDp: Int) = rounded(color, radiusDp).apply { setStroke(dp(0.6f), stroke) }
    private fun typeBordered(color: Int, stroke: Int, radiusDp: Int) = rounded(color, radiusDp).apply { setStroke(dp(1f), stroke) }

    private fun dp(value: Float) = (value * resources.displayMetrics.density).toInt().coerceAtLeast(1)

    private inner class AspectMapFrame : FrameLayout(this@CreateZoneActivity) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            val height = resolveSize((width * MAP_HEIGHT_RATIO).toInt(), heightMeasureSpec)
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
        }
    }

    companion object {
        const val EXTRA_ZONE_ID = "zone_id"
        private const val NO_ZONE = -1L
        private const val MAP_HEIGHT_RATIO = 0.62f
    }
}
