package com.mobile.trackerapp.ui.connection

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.mobile.trackerapp.network.TrackerBackendClient
import com.mobile.trackerapp.service.LocationSharingCoordinator
import com.mobile.trackerapp.service.LocationSharingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.Manifest
import android.content.pm.PackageManager
import com.mobile.trackerapp.R
import com.mobile.trackerapp.ads.AdRemoteConfig
import com.mobile.trackerapp.ads.AdsManager
import com.mobile.trackerapp.ads.banner_all
import com.mobile.trackerapp.databinding.ActivityConnectionBinding
import com.mobile.trackerapp.ui.DashboardActivity
import com.mobile.trackerapp.ui.tools.ZoneStore
import com.mobile.trackerapp.ui.rating.RateAppManager
import androidx.activity.OnBackPressedCallback

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
                else disableSharing(getString(R.string.live_location_permission_required))
            }
            NOTIFICATION_PERMISSION_REQUEST -> {
                if (!hasNotificationPermission()) Toast.makeText(this, R.string.connection_notifications_needed, Toast.LENGTH_LONG).show()
                attemptEnableSharing()
            }
        }
    }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val content = layoutInflater.inflate(R.layout.activity_connection, root, false)
        binding = ActivityConnectionBinding.bind(content)
        val contentBanner = binding.frBanner
        (content as? LinearLayout)?.removeView(contentBanner)
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(nav(getString(R.string.common_connection)), LinearLayout.LayoutParams(-1, wrap()))
        contentBanner?.let { root.addView(it, LinearLayout.LayoutParams(-1, wrap())) }
        setContentView(root)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                AdsManager.showInterHomeBack(this@ConnectionActivity) {
                    finish()
                }
            }
        })
        loadConnectionBanner()
        AdsManager.loadInterHome(this, ignoreLimit = true)
        binding.connectionShareInvite.setOnClickListener { startActivity(Intent(this, ShareConnectionActivity::class.java)) }
        binding.connectionShowQr.setOnClickListener { startActivity(Intent(this, MyQrActivity::class.java)) }
        binding.connectionManageRequests.setOnClickListener { startActivity(Intent(this, FriendRequestsActivity::class.java)) }
        val prefs = getSharedPreferences(LocationSharingCoordinator.PREFS, MODE_PRIVATE)
        val sharing = binding.connectionLocationSwitch
        sharing.isChecked = prefs.getBoolean("enabled", false)
        if (sharing.isChecked && !hasBackgroundLocation()) {
            prefs.edit().putBoolean("enabled", false).apply()
            sharing.isChecked = false
            Toast.makeText(this, R.string.connection_background_needed, Toast.LENGTH_LONG).show()
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
        LocationSharingCoordinator.syncAcceptedFriends(this)
        if (backgroundSettingsOpen) {
            backgroundSettingsOpen = false
            if (pendingSharingEnable && hasBackgroundLocation()) attemptEnableSharing()
            else if (pendingSharingEnable) disableSharing(getString(R.string.connection_background_access_required))
        }
        if (::backend.isInitialized) loadPendingCount()
    }

    private fun attemptEnableSharing() {
        val sharing = binding.connectionLocationSwitch
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
                getSharedPreferences(LocationSharingCoordinator.PREFS, MODE_PRIVATE)
                    .edit().putBoolean(LocationSharingCoordinator.ENABLED, false).apply()
                stopService(Intent(this@ConnectionActivity, LocationSharingService::class.java))
                setSharingChecked(false)
                updateSharingSummary(false)
                Toast.makeText(this@ConnectionActivity, R.string.live_accept_friend_before_sharing, Toast.LENGTH_LONG).show()
                return@launch
            }
            pendingSharingEnable = false
            getSharedPreferences(LocationSharingCoordinator.PREFS, MODE_PRIVATE)
                .edit().putBoolean(LocationSharingCoordinator.ENABLED, true).apply()
            setSharingChecked(true)
            updateSharingSummary(true)
            Toast.makeText(this@ConnectionActivity, R.string.live_sharing_started, Toast.LENGTH_SHORT).show()
            RateAppManager.markConnectionAdded()
            LocationSharingCoordinator.startIfPermitted(this@ConnectionActivity)
        }
    }

    private fun disableSharing(message: String?) {
        getSharedPreferences(LocationSharingCoordinator.PREFS, MODE_PRIVATE)
            .edit().putBoolean(LocationSharingCoordinator.ENABLED, false).apply()
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
            .setTitle(R.string.live_background_title)
            .setMessage(R.string.connection_background_sharing_message)
            .setNegativeButton(R.string.common_not_now) { _, _ -> backgroundSettingsOpen = false; pendingSharingEnable = false }
            .setPositiveButton(R.string.common_open_settings) { _, _ ->
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            }
            .show()
    }

    private fun updateSharingSummary(enabled: Boolean) { binding.connectionLocationSummary.text = getString(if (enabled) R.string.connection_location_shared_summary else R.string.connection_sharing_off) }

    private fun setSharingChecked(checked: Boolean) {
        suppressSharingChange = true
        binding.connectionLocationSwitch.isChecked = checked
        suppressSharingChange = false
    }

    private fun hasForegroundLocation() = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    private fun hasBackgroundLocation() = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
    private fun hasNotificationPermission() = Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    private fun loadPendingCount() {
        if (!::backend.isInitialized) backend = TrackerBackendClient(this)
        CoroutineScope(Dispatchers.Main).launch { runCatching { backend.register(); backend.listRequests() }.onSuccess { result ->
            val count = result.optJSONArray("requests")?.let { array -> (0 until array.length()).count { array.optJSONObject(it)?.optString("status", "pending") == "pending" } } ?: 0
            binding.connectionRequestCount.apply { text = count.toString(); visibility = if (count > 0) View.VISIBLE else View.GONE }
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

    companion object {
        private const val LOCATION_PERMISSION_REQUEST = 84
        private const val NOTIFICATION_PERMISSION_REQUEST = 85
    }
}
