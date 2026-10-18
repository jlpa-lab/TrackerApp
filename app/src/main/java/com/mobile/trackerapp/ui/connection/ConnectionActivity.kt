package com.mobile.trackerapp.ui.connection

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.mobile.trackerapp.network.TrackerBackendClient
import com.mobile.trackerapp.service.LocationSharingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.Manifest
import android.content.pm.PackageManager
import androidx.appcompat.widget.SwitchCompat
import com.ads.module.ads.wrapper.ApNativeAd
import com.mobile.trackerapp.R
import com.mobile.trackerapp.ads.AdRemoteConfig
import com.mobile.trackerapp.ads.AdsManager
import com.mobile.trackerapp.ads.banner_all
import com.mobile.trackerapp.ads.bindNativeAllAd
import com.mobile.trackerapp.databinding.ActivityConnectionBinding
import com.mobile.trackerapp.databinding.ActivityToolsBinding
import com.mobile.trackerapp.ui.DashboardActivity
import com.mobile.trackerapp.ui.tools.ZoneStore

class ConnectionActivity : DashboardActivity() {
    private lateinit var binding: ActivityConnectionBinding
    private var pendingSharingEnable = false
    private var backgroundSettingsOpen = false
    private var suppressSharingChange = false
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST -> {
                if (results.any { it == PackageManager.PERMISSION_GRANTED }) attemptEnableSharing()
                else disableSharing("Location permission is required for background sharing")
            }
            NOTIFICATION_PERMISSION_REQUEST -> {
                if (!hasNotificationPermission()) Toast.makeText(this, "Enable notifications to see sharing status in the notification bar.", Toast.LENGTH_LONG).show()
                attemptEnableSharing()
            }
        }
    }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val content = layoutInflater.inflate(R.layout.activity_connection, root, false)
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(layoutInflater.inflate(R.layout.layout_native_all, root, false))
        root.addView(nav("Connection"), LinearLayout.LayoutParams(-1, wrap()))
        binding = ActivityConnectionBinding.inflate(layoutInflater)
        setContentView(root)
        loadConnectionBanner()
        AdsManager.loadInterHome(this, ignoreLimit = true)
        AdsManager.loadNativeAll(this, R.layout.layout_native_ad_small)
        bindNativeAllAd(this, findViewById(R.id.native_all_container))
        findViewById<View>(R.id.connection_share_invite).setOnClickListener { startActivity(Intent(this, ShareConnectionActivity::class.java)) }
        findViewById<View>(R.id.connection_show_qr).setOnClickListener { startActivity(Intent(this, MyQrActivity::class.java)) }
        findViewById<View>(R.id.connection_manage_requests).setOnClickListener { startActivity(Intent(this, FriendRequestsActivity::class.java)) }
        val prefs = getSharedPreferences("tracker_location", MODE_PRIVATE)
        val sharing = findViewById<SwitchCompat>(R.id.connection_location_switch)
        sharing.isChecked = prefs.getBoolean("enabled", false)
        if (sharing.isChecked && !hasBackgroundLocation()) {
            prefs.edit().putBoolean("enabled", false).apply()
            sharing.isChecked = false
            Toast.makeText(this, "Allow location all the time to continue sharing.", Toast.LENGTH_LONG).show()
        }
        updateSharingSummary(sharing.isChecked)
        sharing.setOnCheckedChangeListener { _, enabled ->
            if (suppressSharingChange) return@setOnCheckedChangeListener
            if (!enabled) {
                pendingSharingEnable = false
                disableSharing(null)
            } else {
                pendingSharingEnable = true
                attemptEnableSharing()
            }
        }
        loadPendingCount()
    }

    private lateinit var backend: TrackerBackendClient
    override fun onResume() {
        super.onResume()
        if (backgroundSettingsOpen) {
            backgroundSettingsOpen = false
            if (pendingSharingEnable && hasBackgroundLocation()) attemptEnableSharing()
            else if (pendingSharingEnable) disableSharing("Allow location access all the time to use friend location sharing")
        }
        if (::backend.isInitialized) loadPendingCount()
    }

    private fun attemptEnableSharing() {
        val sharing = findViewById<SwitchCompat>(R.id.connection_location_switch)
        if (!hasForegroundLocation()) {
            setSharingChecked(false)
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), LOCATION_PERMISSION_REQUEST)
            return
        }
        if (!hasBackgroundLocation()) {
            setSharingChecked(false)
            showBackgroundLocationDialog()
            return
        }
        if (Build.VERSION.SDK_INT >= 33 && !hasNotificationPermission()) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST)
            return
        }
        CoroutineScope(Dispatchers.Main).launch {
            if (!::backend.isInitialized) backend = TrackerBackendClient(this@ConnectionActivity)
            val hasFriends = runCatching { backend.hasAcceptedFriends() }.getOrDefault(false)
            if (!hasFriends) {
                pendingSharingEnable = false
                getSharedPreferences("tracker_location", MODE_PRIVATE).edit().putBoolean("enabled", false).apply()
                stopService(Intent(this@ConnectionActivity, LocationSharingService::class.java))
                setSharingChecked(false)
                updateSharingSummary(false)
                Toast.makeText(this@ConnectionActivity, "Accept a friend request before sharing your location", Toast.LENGTH_LONG).show()
                return@launch
            }
            pendingSharingEnable = false
            getSharedPreferences("tracker_location", MODE_PRIVATE).edit().putBoolean("enabled", true).apply()
            setSharingChecked(true)
            updateSharingSummary(true)
            Toast.makeText(this@ConnectionActivity, "Your location is being shared with your friends", Toast.LENGTH_SHORT).show()
            ContextCompat.startForegroundService(this@ConnectionActivity, Intent(this@ConnectionActivity, LocationSharingService::class.java))
        }
    }

    private fun disableSharing(message: String?) {
        getSharedPreferences("tracker_location", MODE_PRIVATE).edit().putBoolean("enabled", false).apply()
        setSharingChecked(false)
        updateSharingSummary(false)
        val zonesNeedMonitoring = ZoneStore.get(this).all().any { it.enabled && (it.alertOnEnter || it.alertOnExit) }
        if (zonesNeedMonitoring && hasForegroundLocation() && hasBackgroundLocation()) {
            ContextCompat.startForegroundService(this, Intent(this, LocationSharingService::class.java))
        } else {
            stopService(Intent(this, LocationSharingService::class.java))
        }
        message?.let { Toast.makeText(this, it, Toast.LENGTH_LONG).show() }
    }

    private fun showBackgroundLocationDialog() {
        if (backgroundSettingsOpen) return
        backgroundSettingsOpen = true
        AlertDialog.Builder(this)
            .setTitle("Allow location all the time")
            .setMessage("Tracker Friends needs background location access so your approved friend can receive your live location while the app is closed.")
            .setNegativeButton("Not now") { _, _ -> backgroundSettingsOpen = false; pendingSharingEnable = false }
            .setPositiveButton("Open settings") { _, _ ->
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            }
            .show()
    }

    private fun updateSharingSummary(enabled: Boolean) { findViewById<TextView>(R.id.connection_location_summary).text = if (enabled) "Your location is being shared with your approved friends." else "Location sharing is off" }

    private fun setSharingChecked(checked: Boolean) {
        suppressSharingChange = true
        findViewById<SwitchCompat>(R.id.connection_location_switch).isChecked = checked
        suppressSharingChange = false
    }

    private fun hasForegroundLocation() = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    private fun hasBackgroundLocation() = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
    private fun hasNotificationPermission() = Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    private fun loadPendingCount() {
        if (!::backend.isInitialized) backend = TrackerBackendClient(this)
        CoroutineScope(Dispatchers.Main).launch { runCatching { backend.register(); backend.listRequests() }.onSuccess { result ->
            val count = result.optJSONArray("requests")?.let { array -> (0 until array.length()).count { array.optJSONObject(it)?.optString("status", "pending") == "pending" } } ?: 0
            findViewById<TextView>(R.id.connection_request_count).apply { text = count.toString(); visibility = if (count > 0) View.VISIBLE else View.GONE }
        } }
    }

    private fun loadConnectionBanner() {
        val bannerConfig = AdRemoteConfig.banner_all
        AdsManager.loadBanner(
            this, bannerConfig, binding.frBanner, isCollapse = false
        )
    }
    private fun shareInvite() {
        val code = ConnectionInvite.code(this)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Connect with me on GPS Tracker. My invite code is $code")
        }
        startActivity(Intent.createChooser(send, "Share friend invite"))
    }

    override fun onBackPressed() {
        AdsManager.showInterHomeBack(this) {
            finish()
        }
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST = 84
        private const val NOTIFICATION_PERMISSION_REQUEST = 85
    }
}
