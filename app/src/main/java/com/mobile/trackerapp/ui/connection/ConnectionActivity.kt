package com.mobile.trackerapp.ui.connection

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Switch
import android.widget.Toast
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
import com.mobile.trackerapp.ads.banner_splash
import com.mobile.trackerapp.ads.banner_splash_uninstall
import com.mobile.trackerapp.ads.bindNativeAllAd
import com.mobile.trackerapp.ads.populateNativeAdView
import com.mobile.trackerapp.bases.ext.isNetwork
import com.mobile.trackerapp.bases.visibleView
import com.mobile.trackerapp.ui.DashboardActivity

class ConnectionActivity : DashboardActivity() {
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode != LOCATION_PERMISSION_REQUEST) return
        val granted = results.any { it == PackageManager.PERMISSION_GRANTED }
        val sharing = findViewById<Switch>(R.id.connection_location_switch)
        if (granted) {
            getSharedPreferences("tracker_location", MODE_PRIVATE).edit().putBoolean("enabled", true).apply()
            sharing.isChecked = true
            ContextCompat.startForegroundService(this, Intent(this, LocationSharingService::class.java))
        } else {
            getSharedPreferences("tracker_location", MODE_PRIVATE).edit().putBoolean("enabled", false).apply()
            sharing.isChecked = false
            Toast.makeText(this, "Location permission is required for background sharing", Toast.LENGTH_LONG).show()
        }
    }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val content = layoutInflater.inflate(R.layout.activity_connection, root, false)
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(layoutInflater.inflate(R.layout.layout_native_all, root, false))
        root.addView(nav("Connection"), LinearLayout.LayoutParams(-1, wrap()))
        setContentView(root)
        AdsManager.loadNativeAll(this, R.layout.layout_native_ad_small)
        bindNativeAllAd(this, findViewById(R.id.native_all_container))
        findViewById<View>(R.id.connection_share_invite).setOnClickListener { startActivity(Intent(this, ShareConnectionActivity::class.java)) }
        findViewById<View>(R.id.connection_show_qr).setOnClickListener { startActivity(Intent(this, MyQrActivity::class.java)) }
        findViewById<View>(R.id.connection_manage_requests).setOnClickListener { startActivity(Intent(this, FriendRequestsActivity::class.java)) }
        val prefs = getSharedPreferences("tracker_location", MODE_PRIVATE)
        val sharing = findViewById<SwitchCompat>(R.id.connection_location_switch)
        sharing.isChecked = prefs.getBoolean("enabled", false)
        updateSharingSummary(sharing.isChecked)
        sharing.setOnCheckedChangeListener { _, enabled ->
            if (!enabled) {
                prefs.edit().putBoolean("enabled", false).apply(); updateSharingSummary(false); stopService(Intent(this, LocationSharingService::class.java))
            } else if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                prefs.edit().putBoolean("enabled", true).apply(); updateSharingSummary(true); ContextCompat.startForegroundService(this, Intent(this, LocationSharingService::class.java))
            } else {
                sharing.isChecked = false
                requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), LOCATION_PERMISSION_REQUEST)
            }
        }
        loadPendingCount()
    }

    private lateinit var backend: TrackerBackendClient
    override fun onResume() { super.onResume(); if (::backend.isInitialized) loadPendingCount() }
    private fun updateSharingSummary(enabled: Boolean) { findViewById<TextView>(R.id.connection_location_summary).text = if (enabled) "Your approved connections can see your live location." else "Location sharing is off" }
    private fun loadPendingCount() {
        if (!::backend.isInitialized) backend = TrackerBackendClient(this)
        CoroutineScope(Dispatchers.Main).launch { runCatching { backend.register(); backend.listRequests() }.onSuccess { result ->
            val count = result.optJSONArray("requests")?.let { array -> (0 until array.length()).count { array.optJSONObject(it)?.optString("status", "pending") == "pending" } } ?: 0
            findViewById<TextView>(R.id.connection_request_count).apply { text = count.toString(); visibility = if (count > 0) View.VISIBLE else View.GONE }
        } }
    }

    private fun shareInvite() {
        val code = ConnectionInvite.code(this)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Connect with me on GPS Tracker. My invite code is $code")
        }
        startActivity(Intent.createChooser(send, "Share friend invite"))
    }

    companion object { private const val LOCATION_PERMISSION_REQUEST = 84 }
}
