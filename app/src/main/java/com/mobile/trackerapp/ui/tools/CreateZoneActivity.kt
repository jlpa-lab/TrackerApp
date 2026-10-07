package com.mobile.trackerapp.ui.tools

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
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
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
import com.mobile.trackerapp.R
import com.mobile.trackerapp.ui.DashboardActivity
import java.util.Locale
import java.util.concurrent.Executors

/** Map-based create/edit screen for a saved geofence. */
class CreateZoneActivity : DashboardActivity(), OnMapReadyCallback {
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
    private lateinit var safeButton: TextView
    private lateinit var dangerButton: TextView
    private lateinit var saveButton: TextView
    private lateinit var enterCheck: CheckBox
    private lateinit var exitCheck: CheckBox
    private lateinit var locationHint: TextView
    private var map: GoogleMap? = null
    private var circle: Circle? = null
    private var locationCallback: LocationCallback? = null
    private var selectedLocation: LatLng? = null
    private var selectedAddress = ""
    private var radiusMeters = 500
    private var isSafe = true
    private var zoneId = NO_ZONE
    private var userMovedMap = false
    private var cameraCentered = false

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.statusBarColor = Color.rgb(246, 251, 248)
        window.navigationBarColor = Color.rgb(246, 251, 248)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        hideNavigationBar()
        zoneId = intent.getLongExtra(EXTRA_ZONE_ID, NO_ZONE)
        setContentView(buildScreen())
        mapView.onCreate(state)
        mapView.getMapAsync(this)
        if (zoneId != NO_ZONE) loadZone(zoneId)
    }

    private fun buildScreen(): android.view.View {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(246, 251, 248)); fitsSystemWindows = true }
        val scroll = ScrollView(this).apply { isFillViewport = true; clipToPadding = false }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(10), dp(16), dp(20)) }
        scroll.addView(content, android.view.ViewGroup.LayoutParams(-1, -2))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val back = ImageView(this).apply {
            setImageResource(R.drawable.ic_phone_back)
            imageTintList = android.content.res.ColorStateList.valueOf(ink)
            contentDescription = "Back"; isClickable = true; isFocusable = true
            setOnClickListener { finish() }
        }
        header.addView(back, LinearLayout.LayoutParams(dp(28), dp(34)).apply { marginEnd = dp(14) })
        val titleBlock = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleBlock.addView(text(if (zoneId == NO_ZONE) "Add new zone" else "Edit zone", 24f, ink, true))
        titleBlock.addView(text("Choose a place to keep your family safe", 13f, muted).apply { setPadding(0, dp(2), 0, 0) })
        header.addView(titleBlock)
        content.addView(header)

        val searchRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(3), dp(8), dp(3))
            background = bordered(Color.WHITE, Color.rgb(229, 237, 233), dp(13))
        }
        searchInput = EditText(this).apply {
            hint = "Search location or address"
            textSize = 14f; setTextColor(ink); setHintTextColor(muted)
            background = null; setSingleLine(true); imeOptions = EditorInfo.IME_ACTION_SEARCH
            setPadding(dp(5), dp(10), dp(5), dp(10))
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_SEARCH) { searchLocation(); true } else false
            }
        }
        searchRow.addView(text("⌕", 23f, muted, true))
        searchRow.addView(searchInput, LinearLayout.LayoutParams(0, wrap(), 1f))
        val searchButton = text("Search", 13f, green, true).apply {
            setPadding(dp(8), dp(8), dp(8), dp(8)); isClickable = true; isFocusable = true
            setOnClickListener { searchLocation() }
        }
        searchRow.addView(searchButton)
        content.addView(searchRow, LinearLayout.LayoutParams(-1, wrap()).apply { topMargin = dp(12) })

        val mapFrame = AspectMapFrame().apply { background = rounded(Color.rgb(232, 239, 234), dp(17)); clipToOutline = true }
        mapView = MapView(this)
        mapFrame.addView(mapView, FrameLayout.LayoutParams(-1, -1))
        val centerPin = text("＋", 24f, green, true).apply {
            gravity = Gravity.CENTER
            background = rounded(Color.WHITE, dp(24))
            elevation = dp(4).toFloat()
        }
        mapFrame.addView(centerPin, FrameLayout.LayoutParams(dp(38), dp(38), Gravity.CENTER))
        val locate = text("◎", 22f, green, true).apply {
            gravity = Gravity.CENTER
            background = rounded(Color.WHITE, dp(12))
            elevation = dp(4).toFloat()
            isClickable = true; isFocusable = true
            setOnClickListener { moveToCurrentLocation() }
        }
        mapFrame.addView(locate, FrameLayout.LayoutParams(dp(44), dp(44), Gravity.BOTTOM or Gravity.END).apply { marginEnd = dp(12); bottomMargin = dp(12) })
        locationHint = text("Allow location or search for a place", 12f, ink, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = rounded(Color.WHITE, dp(22))
            visibility = View.GONE
        }
        mapFrame.addView(locationHint, FrameLayout.LayoutParams(wrap(), wrap(), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(12) })
        content.addView(mapFrame, LinearLayout.LayoutParams(-1, wrap()).apply { topMargin = dp(10) })

        addressText = text("Move the map to set your zone location", 12f, muted).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END }
        content.addView(addressText, LinearLayout.LayoutParams(-1, wrap()).apply { topMargin = dp(7); bottomMargin = dp(10) })

        content.addView(text("Zone name", 15f, ink, true))
        zoneName = EditText(this).apply {
            hint = "e.g. Home, School, Office"
            textSize = 14f; setTextColor(ink); setHintTextColor(muted)
            setSingleLine(true); setPadding(dp(12), dp(11), dp(12), dp(11))
            background = bordered(Color.WHITE, Color.rgb(229, 237, 233), dp(13))
        }
        content.addView(zoneName, LinearLayout.LayoutParams(-1, wrap()).apply { topMargin = dp(6) })

        val radiusHeader = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        radiusHeader.addView(text("Zone radius", 15f, ink, true), LinearLayout.LayoutParams(0, wrap(), 1f))
        radiusText = text("500 m", 14f, green, true)
        radiusHeader.addView(radiusText)
        content.addView(radiusHeader, LinearLayout.LayoutParams(-1, wrap()).apply { topMargin = dp(13) })
        radiusBar = SeekBar(this).apply { max = 9; progress = 4; progressTintList = android.content.res.ColorStateList.valueOf(green); thumbTintList = android.content.res.ColorStateList.valueOf(green) }
        radiusBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                radiusMeters = (progress + 1) * 100
                radiusText.text = if (radiusMeters == 1000) "1 km" else "$radiusMeters m"
                drawCircle()
            }
        })
        content.addView(radiusBar, LinearLayout.LayoutParams(-1, wrap()))
        val range = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        range.addView(text("100 m", 11f, muted), LinearLayout.LayoutParams(0, wrap(), 1f))
        range.addView(text("1,000 m", 11f, muted))
        content.addView(range)

        content.addView(text("Zone type", 15f, ink, true), LinearLayout.LayoutParams(-1, wrap()).apply { topMargin = dp(12) })
        val typeRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        safeButton = typeButton("♧  Safe Zone")
        dangerButton = typeButton("⚠  Danger Zone")
        typeRow.addView(safeButton, LinearLayout.LayoutParams(0, wrap(), 1f).apply { marginEnd = dp(5) })
        typeRow.addView(dangerButton, LinearLayout.LayoutParams(0, wrap(), 1f).apply { marginStart = dp(5) })
        content.addView(typeRow, LinearLayout.LayoutParams(-1, wrap()).apply { topMargin = dp(7) })
        safeButton.setOnClickListener { isSafe = true; renderTypeButtons() }
        dangerButton.setOnClickListener { isSafe = false; renderTypeButtons() }

        val alerts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(5), dp(12), dp(5)); background = bordered(Color.WHITE, Color.rgb(229, 237, 233), dp(14)) }
        enterCheck = CheckBox(this).apply { text = "Alert on Enter · Notify when someone arrives"; textSize = 13f; isChecked = true; buttonTintList = android.content.res.ColorStateList.valueOf(green); setTextColor(ink) }
        exitCheck = CheckBox(this).apply { text = "Alert on Exit · Notify when someone leaves"; textSize = 13f; isChecked = true; buttonTintList = android.content.res.ColorStateList.valueOf(green); setTextColor(ink) }
        alerts.addView(enterCheck, LinearLayout.LayoutParams(-1, wrap()))
        alerts.addView(exitCheck, LinearLayout.LayoutParams(-1, wrap()))
        content.addView(alerts, LinearLayout.LayoutParams(-1, wrap()).apply { topMargin = dp(12) })

        saveButton = text("✓  Save Zone", 16f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER; setPadding(dp(14), dp(14), dp(14), dp(14))
            background = rounded(green, dp(28)); alpha = 0.52f; isEnabled = false
            isClickable = true; isFocusable = true; setOnClickListener { saveZone() }
        }
        content.addView(saveButton, LinearLayout.LayoutParams(-1, wrap()).apply { topMargin = dp(14) })
        renderTypeButtons()
        return root
    }

    private fun typeButton(title: String) = text(title, 13f, muted, true).apply {
        gravity = Gravity.CENTER; setPadding(dp(7), dp(10), dp(7), dp(10)); isClickable = true; isFocusable = true
    }

    private fun renderTypeButtons() {
        safeButton.background = bordered(if (isSafe) Color.rgb(237, 250, 245) else Color.WHITE, if (isSafe) green else Color.rgb(229, 237, 233), dp(13))
        safeButton.setTextColor(if (isSafe) green else muted)
        dangerButton.background = bordered(if (!isSafe) Color.rgb(255, 242, 241) else Color.WHITE, if (!isSafe) red else Color.rgb(229, 237, 233), dp(13))
        dangerButton.setTextColor(if (!isSafe) red else muted)
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
            uiSettings.isCompassEnabled = true; uiSettings.isMapToolbarEnabled = false
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
        if (query.isEmpty()) { searchInput.error = "Enter a place or address"; return }
        hideKeyboard()
        worker.execute {
            try {
                @Suppress("DEPRECATION")
                val result = Geocoder(this, Locale.getDefault()).getFromLocationName(query, 1)?.firstOrNull()
                runOnUiThread {
                    if (result == null) Toast.makeText(this, "Location not found. Try another search.", Toast.LENGTH_SHORT).show()
                    else {
                        val prettyAddress = result.getAddressLine(0) ?: query
                        setSelectedLocation(LatLng(result.latitude, result.longitude), prettyAddress)
                    }
                }
            } catch (_: Exception) {
                runOnUiThread { Toast.makeText(this, "Could not search this location. Check your connection and try again.", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    private fun saveZone() {
        val name = zoneName.text?.toString()?.trim().orEmpty()
        if (name.isBlank()) { zoneName.error = "Enter a zone name"; return }
        val point = selectedLocation
        if (point == null) { Toast.makeText(this, "Choose a location on the map first.", Toast.LENGTH_SHORT).show(); return }
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
                Toast.makeText(this, "Zone saved", Toast.LENGTH_SHORT).show()
                setResult(Activity.RESULT_OK)
                finish()
            }
        }
    }

    private fun hideKeyboard() {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.hideSoftInputFromWindow(searchInput.windowToken, 0)
    }

    private fun hasForegroundLocation() = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun requestForegroundLocation() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), REQUEST_LOCATION)
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

    @Deprecated("Deprecated in Android")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LOCATION) {
            if (hasForegroundLocation()) { locationHint.visibility = android.view.View.GONE; moveToCurrentLocation() }
            else { locationHint.visibility = android.view.View.VISIBLE; Toast.makeText(this, "Location permission is needed to center the map. You can also search for a place.", Toast.LENGTH_LONG).show() }
        }
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color)
        if (bold) typeface = Typeface.create("sans-serif", Typeface.BOLD)
    }

    private fun rounded(color: Int, radiusDp: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radiusDp).toFloat() }
    private fun bordered(color: Int, stroke: Int, radiusDp: Int) = rounded(color, radiusDp).apply { setStroke(dp(1), stroke) }

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
        private const val REQUEST_LOCATION = 6601
        private const val MAP_HEIGHT_RATIO = 0.62f
    }
}
