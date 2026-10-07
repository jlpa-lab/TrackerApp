package com.mobile.trackerapp.ui.tools

import android.Manifest
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.mobile.trackerapp.R
import com.mobile.trackerapp.ui.DashboardActivity

/** Saved safe/danger zones and entry/exit monitoring controls. */
class ZoneAlertActivity : DashboardActivity(), OnMapReadyCallback {
    private val ink = Color.rgb(17, 24, 39)
    private val muted = Color.rgb(125, 137, 153)
    private val green = Color.rgb(0, 164, 76)
    private val danger = Color.rgb(229, 74, 70)
    private val store by lazy { ZoneStore.get(this) }
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private lateinit var mapView: MapView
    private lateinit var mapMessage: TextView
    private lateinit var zoneList: LinearLayout
    private lateinit var zoneCount: TextView
    private lateinit var statusTitle: TextView
    private lateinit var statusSubtitle: TextView
    private var map: GoogleMap? = null
    private var locationCallback: LocationCallback? = null
    private var cameraCentered = false
    private var didPromptBackground = false
    private var didAskNotifications = false
    private var lastGeofenceSignature: String? = null

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
        renderZones(store.all())
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
            setPadding(dp(16), dp(10), dp(16), dp(20))
        }
        scroll.addView(content, android.view.ViewGroup.LayoutParams(-1, -2))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val back = ImageView(this).apply {
            setImageResource(R.drawable.ic_phone_back)
            imageTintList = android.content.res.ColorStateList.valueOf(ink)
            contentDescription = "Back"
            isClickable = true; isFocusable = true
            setOnClickListener { finish() }
        }
        header.addView(back, LinearLayout.LayoutParams(dp(28), dp(34)).apply { marginEnd = dp(14) })
        val titleGroup = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleGroup.addView(text("Zone Alert", 25f, ink, true))
        titleGroup.addView(text("Know when loved ones arrive or leave", 14f, muted).apply { setPadding(0, dp(2), 0, 0) })
        header.addView(titleGroup, LinearLayout.LayoutParams(0, wrap(), 1f))
        content.addView(header)

        val summary = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(Color.rgb(237, 250, 245), dp(18)).also { it.setStroke(dp(1), Color.rgb(205, 240, 225)) }
        }
        val summaryText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        statusTitle = text("Set up your safe zones", 18f, ink, true)
        statusSubtitle = text("0 zones · Ready when you are", 13f, green)
        summaryText.addView(statusTitle)
        summaryText.addView(statusSubtitle, LinearLayout.LayoutParams(-1, wrap()).apply { topMargin = dp(3) })
        summary.addView(summaryText, LinearLayout.LayoutParams(0, wrap(), 1f))
        summary.addView(text("🛡️", 32f, green))
        content.addView(summary, LinearLayout.LayoutParams(-1, wrap()).apply { topMargin = dp(14) })

        val mapFrame = AspectMapFrame().apply {
            background = rounded(Color.rgb(230, 239, 232), dp(18))
            clipToOutline = true
        }
        mapView = MapView(this)
        mapFrame.addView(mapView, FrameLayout.LayoutParams(-1, -1))
        mapMessage = text("Enable location to see zones near you", 12f, ink, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(9), dp(12), dp(9))
            background = rounded(Color.WHITE, dp(24))
            elevation = dp(3).toFloat()
            isClickable = true
            isFocusable = true
            setOnClickListener { requestForegroundLocation() }
            visibility = if (hasForegroundLocation()) View.GONE else View.VISIBLE
        }
        mapFrame.addView(mapMessage, FrameLayout.LayoutParams(wrap(), wrap(), Gravity.BOTTOM or Gravity.END).apply {
            marginEnd = dp(12); bottomMargin = dp(12)
        })
        content.addView(mapFrame, LinearLayout.LayoutParams(-1, wrap()).apply { topMargin = dp(12) })

        val zonesHeader = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        zonesHeader.addView(text("My zones", 18f, ink, true), LinearLayout.LayoutParams(0, wrap(), 1f))
        zoneCount = text("0 zones", 13f, muted)
        zonesHeader.addView(zoneCount)
        content.addView(zonesHeader, LinearLayout.LayoutParams(-1, wrap()).apply { topMargin = dp(12); bottomMargin = dp(8) })

        zoneList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(zoneList, LinearLayout.LayoutParams(-1, wrap()))

        val addZone = text("＋  Add a new zone", 16f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = rounded(green, dp(28))
            isClickable = true; isFocusable = true
            setOnClickListener { startActivity(Intent(this@ZoneAlertActivity, CreateZoneActivity::class.java)) }
        }
        content.addView(addZone, LinearLayout.LayoutParams(-1, wrap()).apply { topMargin = dp(8) })

        val help = text("Entry and exit alerts are sent to this device when a monitored zone is crossed.", 12f, muted).apply {
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(10), dp(8), dp(4))
        }
        content.addView(help, LinearLayout.LayoutParams(-1, wrap()))
        return root
    }

    private fun renderZones(zones: List<ZoneRecord>) {
        if (!::zoneList.isInitialized) return
        zoneList.removeAllViews()
        zoneCount.text = "${zones.size} ${if (zones.size == 1) "zone" else "zones"}"
        val enabledCount = zones.count { it.enabled }
        statusTitle.text = when {
            zones.isEmpty() -> "Set up your safe zones"
            enabledCount == 0 -> "Your zones are paused"
            else -> "Your safe zones are active"
        }
        statusSubtitle.text = when {
            zones.isEmpty() -> "0 zones · Ready when you are"
            enabledCount == 0 -> "${zones.size} saved · Turn on a zone to get alerts"
            !hasForegroundLocation() -> "Location access is needed for arrival and exit alerts"
            !hasBackgroundLocation() -> "$enabledCount zones · Allow location all the time to enable alerts"
            else -> "$enabledCount zones · Watching over your family"
        }
        statusSubtitle.setTextColor(if (zones.isNotEmpty() && !hasBackgroundLocation()) danger else green)

        if (zones.isEmpty()) {
            val empty = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(20), dp(16), dp(20))
                background = bordered(Color.WHITE, Color.rgb(229, 237, 233), dp(16))
            }
            empty.addView(text("⌖", 30f, green, true))
            empty.addView(text("No zones yet", 17f, ink, true).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(-1, wrap()).apply { topMargin = dp(4) })
            empty.addView(text("Add Home, School, or another place to get alerts when you arrive or leave.", 13f, muted).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(-1, wrap()).apply { topMargin = dp(4) })
            zoneList.addView(empty)
        } else {
            zones.forEach { zone -> zoneList.addView(zoneCard(zone), LinearLayout.LayoutParams(-1, wrap()).apply { bottomMargin = dp(8) }) }
        }
        drawZones(zones)
        syncGeofences(zones)
        if (zones.any { it.enabled && (it.alertOnEnter || it.alertOnExit) }) {
            maybeRequestNotificationPermission()
            if (Build.VERSION.SDK_INT < 33 || hasNotificationPermission()) maybeExplainBackgroundAccess()
        }
    }

    private fun zoneCard(zone: ZoneRecord): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(13), dp(12), dp(13), dp(11))
        background = bordered(Color.WHITE, Color.rgb(229, 237, 233), dp(16))

        val top = LinearLayout(this@ZoneAlertActivity).apply { gravity = Gravity.CENTER_VERTICAL }
        val badge = text(if (zone.safe) "⌂" else "⚠", 20f, if (zone.safe) green else danger, true).apply { gravity = Gravity.CENTER }
        badge.background = rounded(if (zone.safe) Color.rgb(235, 249, 242) else Color.rgb(255, 240, 239), dp(11))
        top.addView(badge, LinearLayout.LayoutParams(dp(38), dp(38)).apply { marginEnd = dp(10) })
        val names = LinearLayout(this@ZoneAlertActivity).apply { orientation = LinearLayout.VERTICAL }
        names.addView(text(zone.name, 16f, ink, true))
        names.addView(text(zone.address.ifBlank { "${"%.5f".format(zone.latitude)}, ${"%.5f".format(zone.longitude)}" }, 12f, muted).apply {
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(-1, wrap()).apply { topMargin = dp(2) })
        top.addView(names, LinearLayout.LayoutParams(0, wrap(), 1f))
        val activeSwitch = Switch(this@ZoneAlertActivity).apply {
            isChecked = zone.enabled
            contentDescription = "Enable alerts for ${zone.name}"
            setOnCheckedChangeListener { _, enabled ->
                store.setEnabled(zone.id, enabled)
                renderZones(store.all())
            }
        }
        top.addView(activeSwitch)
        setOnClickListener { startActivity(Intent(this@ZoneAlertActivity, CreateZoneActivity::class.java).putExtra(CreateZoneActivity.EXTRA_ZONE_ID, zone.id)) }
        addView(top)

        val metadata = LinearLayout(this@ZoneAlertActivity).apply { gravity = Gravity.CENTER_VERTICAL }
        val color = if (zone.safe) green else danger
        metadata.addView(text("${zone.radiusMeters} m radius · ${if (zone.safe) "Safe zone" else "Danger zone"}", 12f, color, true), LinearLayout.LayoutParams(0, wrap(), 1f))
        metadata.addView(text("${if (zone.alertOnEnter) "Enter" else ""}${if (zone.alertOnEnter && zone.alertOnExit) " & " else ""}${if (zone.alertOnExit) "Exit" else ""} alerts", 11f, muted))
        addView(metadata, LinearLayout.LayoutParams(-1, wrap()).apply { topMargin = dp(9) })

        val actions = LinearLayout(this@ZoneAlertActivity).apply { gravity = Gravity.END }
        val edit = text("Edit", 12f, green, true).apply { isClickable = true; isFocusable = true }
        edit.setOnClickListener { startActivity(Intent(this@ZoneAlertActivity, CreateZoneActivity::class.java).putExtra(CreateZoneActivity.EXTRA_ZONE_ID, zone.id)) }
        val delete = text("Delete", 12f, danger, true).apply { setPadding(dp(18), 0, 0, 0); isClickable = true; isFocusable = true }
        delete.setOnClickListener { confirmDelete(zone) }
        actions.addView(edit); actions.addView(delete)
        addView(actions, LinearLayout.LayoutParams(-1, wrap()).apply { topMargin = dp(7) })
    }

    private fun confirmDelete(zone: ZoneRecord) {
        AlertDialog.Builder(this)
            .setTitle("Delete ${zone.name}?")
            .setMessage("This zone and its entry/exit alerts will be removed.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                store.delete(zone.id)
                renderZones(store.all())
            }
            .show()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap.apply {
            uiSettings.isZoomControlsEnabled = false
            uiSettings.isMyLocationButtonEnabled = false
            uiSettings.isCompassEnabled = true
            uiSettings.isMapToolbarEnabled = false
        }
        drawZones(store.all())
        if (hasForegroundLocation()) startLocationUpdates() else requestForegroundLocation()
    }

    private fun drawZones(zones: List<ZoneRecord>) {
        val currentMap = map ?: return
        currentMap.clear()
        zones.forEach { zone ->
            val center = LatLng(zone.latitude, zone.longitude)
            val color = if (zone.safe) green else danger
            currentMap.addCircle(CircleOptions().center(center).radius(zone.radiusMeters.toDouble())
                .strokeColor(color).strokeWidth(dp(2).toFloat()).fillColor(if (zone.safe) 0x2230B66A else 0x22E54A46))
            currentMap.addMarker(MarkerOptions().position(center).title(zone.name).snippet("${zone.radiusMeters} m radius"))
        }
    }

    private fun startLocationUpdates() {
        if (!hasForegroundLocation()) return
        try {
            map?.isMyLocationEnabled = true
            mapMessage.visibility = View.GONE
            fused.lastLocation.addOnSuccessListener(this) { location ->
                if (location != null) centerOnLocation(location, false)
            }
            locationCallback?.let(fused::removeLocationUpdates)
            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let { centerOnLocation(it, true) }
                }
            }
            locationCallback = callback
            fused.requestLocationUpdates(
                LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 8_000L)
                    .setMinUpdateDistanceMeters(15f).build(), callback, mainLooper
            )
        } catch (_: SecurityException) {
            mapMessage.visibility = View.VISIBLE
        }
    }

    private fun centerOnLocation(location: Location, animate: Boolean) {
        val target = LatLng(location.latitude, location.longitude)
        if (cameraCentered) return
        cameraCentered = true
        val update = CameraUpdateFactory.newLatLngZoom(target, 15f)
        if (animate) map?.animateCamera(update) else map?.moveCamera(update)
    }

    private fun hasForegroundLocation() = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun hasBackgroundLocation() = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun requestForegroundLocation() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), REQUEST_FOREGROUND_LOCATION)
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && !hasNotificationPermission() && !didAskNotifications) {
            didAskNotifications = true
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }
    }

    private fun hasNotificationPermission() = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun maybeExplainBackgroundAccess() {
        if (didPromptBackground || zonesConfiguredEmpty()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !hasBackgroundLocation()) {
            didPromptBackground = true
            AlertDialog.Builder(this)
                .setTitle("Enable arrival and exit alerts")
                .setMessage("Android needs location access set to “Allow all the time” to detect when you enter or leave a zone while the app is closed. You can still save zones without enabling this.")
                .setNegativeButton("Not now", null)
                .setPositiveButton("Open settings") { _, _ ->
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                }
                .show()
        }
    }

    private fun zonesConfiguredEmpty() = store.all().isEmpty()

    private fun syncGeofences(zones: List<ZoneRecord>) {
        val signature = buildString {
            append(hasForegroundLocation()).append('|').append(hasBackgroundLocation()).append('|')
            zones.sortedBy { it.id }.forEach { zone ->
                append(zone.id).append(':').append(zone.latitude).append(':').append(zone.longitude)
                    .append(':').append(zone.radiusMeters).append(':').append(zone.enabled)
                    .append(':').append(zone.alertOnEnter).append(':').append(zone.alertOnExit).append(';')
            }
        }
        if (signature == lastGeofenceSignature) return
        lastGeofenceSignature = signature
        val client = LocationServices.getGeofencingClient(this)
        val pendingIntent = PendingIntent.getBroadcast(
            this, GEOFENCE_REQUEST_CODE, Intent(this, ZoneGeofenceReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        client.removeGeofences(pendingIntent).addOnSuccessListener {
            val monitored = zones.filter { it.enabled && (it.alertOnEnter || it.alertOnExit) }
            if (monitored.isEmpty() || !hasForegroundLocation() || !hasBackgroundLocation()) return@addOnSuccessListener
            val fences = monitored.take(MAX_GEOFENCES).map { zone ->
                Geofence.Builder()
                    .setRequestId(zone.id.toString())
                    .setCircularRegion(zone.latitude, zone.longitude, zone.radiusMeters.toFloat())
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                    .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
                    .build()
            }
            val request = GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofences(fences)
                .build()
            try {
                client.addGeofences(request, pendingIntent).addOnFailureListener {
                    android.util.Log.e("ZoneAlert", "Could not register geofences", it)
                }
            } catch (error: SecurityException) {
                android.util.Log.e("ZoneAlert", "Location permission is missing for geofencing", error)
            }
        }.addOnFailureListener { error -> android.util.Log.e("ZoneAlert", "Could not refresh geofences", error) }
    }

    override fun onResume() {
        super.onResume()
        if (::mapView.isInitialized) mapView.onResume()
        if (::zoneList.isInitialized) renderZones(store.all())
        if (map != null && hasForegroundLocation()) startLocationUpdates()
    }

    override fun onStart() { super.onStart(); if (::mapView.isInitialized) mapView.onStart() }

    override fun onPause() {
        locationCallback?.let(fused::removeLocationUpdates)
        locationCallback = null
        if (::mapView.isInitialized) mapView.onPause()
        super.onPause()
    }

    override fun onStop() { if (::mapView.isInitialized) mapView.onStop(); super.onStop() }

    override fun onDestroy() {
        locationCallback?.let(fused::removeLocationUpdates)
        if (::mapView.isInitialized) mapView.onDestroy()
        super.onDestroy()
    }

    override fun onLowMemory() { super.onLowMemory(); if (::mapView.isInitialized) mapView.onLowMemory() }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::mapView.isInitialized) mapView.onSaveInstanceState(outState)
    }

    @Deprecated("Deprecated in Android")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_FOREGROUND_LOCATION -> {
                if (hasForegroundLocation()) startLocationUpdates() else mapMessage.visibility = View.VISIBLE
                lastGeofenceSignature = null
                renderZones(store.all())
            }
            REQUEST_NOTIFICATIONS -> {
                if (!hasNotificationPermission()) {
                    Toast.makeText(this, "Zone alerts need notification permission to show arrival and exit messages.", Toast.LENGTH_LONG).show()
                }
                maybeExplainBackgroundAccess()
                lastGeofenceSignature = null
                renderZones(store.all())
            }
        }
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color)
        if (bold) typeface = Typeface.create("sans-serif", Typeface.BOLD)
    }

    private fun rounded(color: Int, radiusDp: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radiusDp).toFloat() }
    private fun bordered(color: Int, stroke: Int, radiusDp: Int) = rounded(color, radiusDp).apply { setStroke(dp(1), stroke) }

    private inner class AspectMapFrame : FrameLayout(this@ZoneAlertActivity) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            val height = resolveSize((width * MAP_HEIGHT_RATIO).toInt(), heightMeasureSpec)
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
        }
    }

    companion object {
        private const val REQUEST_FOREGROUND_LOCATION = 6610
        private const val REQUEST_NOTIFICATIONS = 6611
        private const val GEOFENCE_REQUEST_CODE = 6612
        private const val MAX_GEOFENCES = 100
        private const val MAP_HEIGHT_RATIO = 0.58f
    }
}
