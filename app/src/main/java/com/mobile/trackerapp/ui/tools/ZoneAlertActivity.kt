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
import com.mobile.trackerapp.ads.AdRemoteConfig
import com.mobile.trackerapp.ads.AdsManager
import com.mobile.trackerapp.ads.banner_all
import com.mobile.trackerapp.databinding.ActivityTrafficAlertBinding
import com.mobile.trackerapp.databinding.ActivityZoneAlertBinding
import com.mobile.trackerapp.ui.DashboardActivity
import com.mobile.trackerapp.ui.ZoneToggleView
import com.mobile.trackerapp.network.RealtimeLocationClient
import com.mobile.trackerapp.network.TrackerBackendClient
import com.mobile.trackerapp.service.LocationSharingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Saved safe/danger zones and entry/exit monitoring controls. */
class ZoneAlertActivity : DashboardActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityZoneAlertBinding
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
    private lateinit var zoneAdd: TextView
    private lateinit var zoneLocate: ImageView
    private lateinit var zoneStatusIcon: ImageView
    private lateinit var statusTitle: TextView
    private lateinit var statusSubtitle: TextView
    private lateinit var alertNotice: TextView
    private var map: GoogleMap? = null
    private var locationCallback: LocationCallback? = null
    private var cameraCentered = false
    private var didPromptBackground = false
    private var didAskNotifications = false
    private var lastGeofenceSignature: String? = null
    private var manageMode = false
    private var realtime: RealtimeLocationClient? = null
    private var hasAcceptedFriends = false
    private var friendMemberLabel = "0 members"

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.statusBarColor = Color.rgb(246, 251, 248)
        window.navigationBarColor = Color.rgb(246, 251, 248)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        hideNavigationBar()
        binding = ActivityZoneAlertBinding.inflate(layoutInflater)
        setContentView(binding.root)
        AdsManager.loadInterHome(this, ignoreLimit = true)
        binding.zoneBack.setOnClickListener { onBackPressed() };
        loadZoneAlertBanner()
        binding.zoneAdd.setOnClickListener { startActivity(Intent(this, CreateZoneActivity::class.java)) }
        binding.zoneCount.setOnClickListener {
            if (store.all().isNotEmpty()) {
                manageMode = !manageMode
                renderZones(store.all())
            }
        }
        binding.zoneLocate.setOnClickListener { if (hasForegroundLocation()) moveMapToCurrentLocation() else requestForegroundLocation() }
        mapView = binding.zoneMap; mapMessage = binding.zoneMapMessage; zoneList = binding.zoneList; zoneCount = binding.zoneCount; zoneAdd = binding.zoneAdd; zoneLocate = binding.zoneLocate; zoneStatusIcon = binding.zoneStatusIcon; statusTitle = binding.zoneStatusTitle; statusSubtitle = binding.zoneStatusSubtitle; alertNotice = binding.zoneAlertNotice
        mapMessage.setOnClickListener { requestForegroundLocation() }; mapMessage.visibility = if (hasForegroundLocation()) View.GONE else View.VISIBLE
        mapView.onCreate(state)
        mapView.getMapAsync(this)
        renderZones(store.all())
        showLatestFriendAlert()
        connectFriendZoneMonitoring()
    }

    private fun renderZones(zones: List<ZoneRecord>) {
        if (!::zoneList.isInitialized) return
        zoneList.removeAllViews()
        if (zones.isEmpty()) manageMode = false
        zoneCount.text = when {
            zones.isEmpty() -> getString(R.string.zone_count_zero)
            manageMode -> getString(R.string.zone_done)
            else -> getString(R.string.zone_manage)
        }
        zoneCount.setTextColor(if (zones.isEmpty()) muted else green)
        zoneAdd.setText(if (zones.isEmpty()) R.string.zone_create_first else R.string.zone_add_new)
        val enabledCount = zones.count { it.enabled }
        statusTitle.text = when {
            zones.isEmpty() -> getString(R.string.zone_setup_title)
            enabledCount == 0 -> getString(R.string.zone_paused_title)
            else -> getString(R.string.zone_active_title)
        }
        statusSubtitle.text = when {
            zones.isEmpty() -> getString(R.string.zone_ready_subtitle)
            enabledCount == 0 -> getString(R.string.zone_saved_subtitle, zones.size)
            !hasForegroundLocation() -> getString(R.string.zone_location_needed)
            !hasBackgroundLocation() -> getString(R.string.zone_background_needed, enabledCount)
            else -> getString(R.string.zone_watching_family, enabledCount)
        }
        statusSubtitle.setTextColor(if (zones.isNotEmpty() && !hasBackgroundLocation()) danger else green)

        if (zones.isEmpty()) {
            val empty = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                minimumHeight = dp(176)
                setPadding(dp(20), dp(20), dp(20), dp(20))
                background = bordered(Color.WHITE, Color.rgb(229, 237, 233), dp(16))
            }
            empty.addView(ImageView(this).apply {
                setImageResource(R.drawable.ic_zone_pin)
                contentDescription = getString(R.string.zone_no_zones)
            }, LinearLayout.LayoutParams(dp(48), dp(48)))
            empty.addView(text(getString(R.string.zone_no_zones_yet), 16f, ink, true).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(-1, wrap()).apply { topMargin = dp(10) })
            empty.addView(text(getString(R.string.zone_empty_message), 12f, muted).apply {
                gravity = Gravity.CENTER
                textAlignment = View.TEXT_ALIGNMENT_CENTER
            }, LinearLayout.LayoutParams(-1, wrap()).apply { topMargin = dp(8) })
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
        ensureFriendZoneMonitor(zones, hasAcceptedFriends)
    }
    private fun loadZoneAlertBanner() {
        val bannerConfig = AdRemoteConfig.banner_all
        AdsManager.loadBanner(
            this, bannerConfig, binding.frBanner, isCollapse = false
        )
    }
    private fun zoneCard(zone: ZoneRecord): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), dp(10), dp(12), dp(10))
        background = bordered(Color.WHITE, Color.rgb(229, 237, 233), dp(16))

        val top = LinearLayout(this@ZoneAlertActivity).apply { gravity = Gravity.CENTER_VERTICAL }
        val badge = text(if (zone.safe) "⌂" else "⚠", 20f, if (zone.safe) green else danger, true).apply { gravity = Gravity.CENTER }
        badge.background = rounded(if (zone.safe) Color.rgb(235, 249, 242) else Color.rgb(255, 240, 239), dp(11))
        top.addView(badge, LinearLayout.LayoutParams(dp(32), dp(32)).apply { marginEnd = dp(10) })
        val names = LinearLayout(this@ZoneAlertActivity).apply { orientation = LinearLayout.VERTICAL }
        names.addView(text(zone.name, 14f, ink, true))
        names.addView(text(zone.address.ifBlank { "${"%.5f".format(zone.latitude)}, ${"%.5f".format(zone.longitude)}" }, 11f, muted).apply {
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(-1, wrap()).apply { topMargin = dp(2) })
        top.addView(names, LinearLayout.LayoutParams(0, wrap(), 1f))
        val activeSwitch = ZoneToggleView(this@ZoneAlertActivity).apply {
            isChecked = zone.enabled
            contentDescription = getString(R.string.zone_enable_alerts, zone.name)
            setOnCheckedChangeListener { _, enabled ->
                store.setEnabled(zone.id, enabled)
                renderZones(store.all())
            }
        }
        top.addView(activeSwitch, LinearLayout.LayoutParams(wrap(), wrap()))
        setOnClickListener { startActivity(Intent(this@ZoneAlertActivity, CreateZoneActivity::class.java).putExtra(CreateZoneActivity.EXTRA_ZONE_ID, zone.id)) }
        addView(top)

        val metadata = LinearLayout(this@ZoneAlertActivity).apply { gravity = Gravity.CENTER_VERTICAL }
        val color = if (zone.safe) green else danger
        metadata.addView(text("${zone.radiusMeters} m radius · $friendMemberLabel", 11f, color, true), LinearLayout.LayoutParams(0, wrap(), 1f))
        val alertTypes = buildString {
            if (zone.alertOnEnter) append(getString(R.string.zone_enter))
            if (zone.alertOnEnter && zone.alertOnExit) append(getString(R.string.zone_both_separator))
            if (zone.alertOnExit) append(getString(R.string.zone_exit))
        }
        metadata.addView(text(getString(R.string.zone_enter_exit_alerts, alertTypes), 11f, muted))
        addView(metadata, LinearLayout.LayoutParams(-1, wrap()).apply { topMargin = dp(9) })

        if (manageMode) {
            val actions = LinearLayout(this@ZoneAlertActivity).apply { gravity = Gravity.END }
            val edit = text(getString(R.string.zone_edit), 12f, green, true).apply { isClickable = true; isFocusable = true }
            edit.setOnClickListener { startActivity(Intent(this@ZoneAlertActivity, CreateZoneActivity::class.java).putExtra(CreateZoneActivity.EXTRA_ZONE_ID, zone.id)) }
            val delete = text(getString(R.string.common_delete), 12f, danger, true).apply { setPadding(dp(18), 0, 0, 0); isClickable = true; isFocusable = true }
            delete.setOnClickListener { confirmDelete(zone) }
            actions.addView(edit); actions.addView(delete)
            addView(actions, LinearLayout.LayoutParams(-1, wrap()).apply { topMargin = dp(5) })
        }
        minimumHeight = dp(86)
    }

    private fun confirmDelete(zone: ZoneRecord) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.zone_delete_title, zone.name))
            .setMessage(getString(R.string.zone_delete_message))
            .setNegativeButton(R.string.common_cancel, null)
            .setPositiveButton(R.string.common_delete) { _, _ ->
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

    private fun moveMapToCurrentLocation() {
        if (!hasForegroundLocation()) {
            requestForegroundLocation()
            return
        }
        fused.lastLocation.addOnSuccessListener(this) { location ->
            if (location != null) {
                cameraCentered = true
                map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(location.latitude, location.longitude), 15f))
            } else {
                startLocationUpdates()
            }
        }
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
                .setTitle(getString(R.string.zone_background_title))
                .setMessage(getString(R.string.zone_background_message))
                .setNegativeButton(R.string.common_not_now, null)
                .setPositiveButton(R.string.common_open_settings) { _, _ ->
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

    private fun connectFriendZoneMonitoring() {
        CoroutineScope(Dispatchers.Main).launch {
            val backend = TrackerBackendClient(this@ZoneAlertActivity)
            val friendList = runCatching {
                backend.register()
                backend.listFriendsOrLegacy().optJSONArray("friends")
            }.getOrNull()
            val friendModels = buildList {
                if (friendList != null) {
                    for (index in 0 until friendList.length()) {
                        val friend = friendList.optJSONObject(index) ?: continue
                        val model = friend.optString("device_model")
                            .ifBlank { friend.optString("name") }
                            .trim()
                        if (model.isNotBlank() && !contains(model)) add(model)
                    }
                }
            }
            hasAcceptedFriends = if (friendList != null) friendList.length() > 0
            else runCatching { backend.hasAcceptedFriends() }.getOrDefault(false)
            friendMemberLabel = when {
                friendModels.size == 1 -> friendModels.first()
                friendModels.size > 1 -> "${friendModels.size} members"
                hasAcceptedFriends -> "1 member"
                else -> "0 members"
            }
            renderZones(store.all())
            if (hasAcceptedFriends) {
                realtime = RealtimeLocationClient(this@ZoneAlertActivity) { payload ->
                    val event = ZoneAlertNotifier.processFriendLocation(this@ZoneAlertActivity, payload)
                    if (event != null) {
                        runOnUiThread {
                            showLatestFriendAlert()
                            Toast.makeText(this@ZoneAlertActivity, event.title(this@ZoneAlertActivity), Toast.LENGTH_LONG).show()
                        }
                    } else {
                        runOnUiThread { showLatestFriendAlert() }
                    }
                }.also { it.connect() }
            } else {
                alertNotice.visibility = View.GONE
                getSharedPreferences("tracker_location", MODE_PRIVATE).edit().putBoolean("enabled", false).apply()
                stopService(Intent(this@ZoneAlertActivity, LocationSharingService::class.java))
            }
            ensureFriendZoneMonitor(store.all(), hasAcceptedFriends)
        }
    }

    private fun ensureFriendZoneMonitor(zones: List<ZoneRecord>, hasAcceptedFriends: Boolean) {
        val hasMonitoredZones = zones.any { it.enabled && (it.alertOnEnter || it.alertOnExit) }
        val canMonitor = hasAcceptedFriends && hasMonitoredZones && hasForegroundLocation() && hasBackgroundLocation() && hasNotificationPermission()
        if (canMonitor) {
            ContextCompat.startForegroundService(this, Intent(this, LocationSharingService::class.java))
        } else if (!getSharedPreferences("tracker_location", MODE_PRIVATE).getBoolean("enabled", false) || !hasAcceptedFriends) {
            stopService(Intent(this, LocationSharingService::class.java))
        }
    }

    private fun showLatestFriendAlert() {
        if (!::alertNotice.isInitialized) return
        ZoneAlertNotifier.latestMessage(this)?.let {
            alertNotice.text = it
            alertNotice.visibility = View.VISIBLE
        }
    }

    override fun onResume() {
        super.onResume()
        if (::mapView.isInitialized) mapView.onResume()
        if (::zoneList.isInitialized) renderZones(store.all())
        showLatestFriendAlert()
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
        realtime?.close()
        if (::mapView.isInitialized) mapView.onDestroy()
        super.onDestroy()
    }

    override fun onLowMemory() { super.onLowMemory(); if (::mapView.isInitialized) mapView.onLowMemory() }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::mapView.isInitialized) mapView.onSaveInstanceState(outState)
    }

    override fun onBackPressed() {
        AdsManager.showInterHomeBack(this) {
            finish()
        }
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
                    Toast.makeText(this, getString(R.string.zone_notification_permission), Toast.LENGTH_LONG).show()
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
    private fun bordered(color: Int, stroke: Int, radiusDp: Int) = rounded(color, radiusDp).apply { setStroke(dp(0.6f), stroke) }

    private fun dp(value: Float) = (value * resources.displayMetrics.density).toInt().coerceAtLeast(1)

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
