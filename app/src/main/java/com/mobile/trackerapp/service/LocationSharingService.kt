package com.mobile.trackerapp.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.mobile.trackerapp.R
import com.mobile.trackerapp.network.RealtimeLocationClient
import com.mobile.trackerapp.ui.tools.ZoneAlertNotifier
import com.mobile.trackerapp.ui.tools.ZoneStore

class LocationSharingService : Service() {
    private lateinit var fused: FusedLocationProviderClient
    private lateinit var realtime: RealtimeLocationClient
    private var callback: LocationCallback? = null
    private var sharingEnabled = false
    override fun onCreate() {
        super.onCreate()
        sharingEnabled = getSharedPreferences("tracker_location", MODE_PRIVATE).getBoolean("enabled", false)
        val monitorZones = ZoneStore.get(this).all().any { it.enabled && (it.alertOnEnter || it.alertOnExit) }
        createChannel()
        startForeground(NOTIFICATION_ID, notification(sharingEnabled))
        if (!sharingEnabled && !monitorZones) { stopSelf(); return }
        fused = LocationServices.getFusedLocationProviderClient(this)
        realtime = RealtimeLocationClient(this) { payload -> ZoneAlertNotifier.processFriendLocation(this, payload) }
        realtime.connect()
        if (sharingEnabled) startSharingUpdates()
    }
    private fun batteryPercentage(): Float = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let { it.getIntExtra("level", 0) * 100f / it.getIntExtra("scale", 100).coerceAtLeast(1) } ?: 0f
    private fun createChannel() { getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.notification_location_sharing), NotificationManager.IMPORTANCE_LOW)) }
    private fun notification(sharing: Boolean): Notification = NotificationCompat.Builder(this, CHANNEL)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(getString(if (sharing) R.string.notification_location_shared_title else R.string.notification_zones_active))
        .setContentText(getString(if (sharing) R.string.notification_location_shared_text else R.string.notification_watching_zones))
        .setOngoing(true)
        .build()
    private fun startSharingUpdates() {
        if (!::fused.isInitialized || !::realtime.isInitialized) return
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED && checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            stopSelf()
            return
        }
        callback?.let { fused.removeLocationUpdates(it) }
        callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { realtime.sendLocation(it.latitude, it.longitude, batteryPercentage()) }
            }
        }
        fused.requestLocationUpdates(LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L).setMinUpdateDistanceMeters(5f).build(), callback!!, Looper.getMainLooper())
        fused.lastLocation.addOnSuccessListener { location ->
            location?.let { realtime.sendLocation(it.latitude, it.longitude, batteryPercentage()) }
        }
    }

    override fun onDestroy() { callback?.let { fused.removeLocationUpdates(it) }; if (::realtime.isInitialized) realtime.close(); super.onDestroy() }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val requestedSharing = getSharedPreferences("tracker_location", MODE_PRIVATE).getBoolean("enabled", false)
        if (requestedSharing != sharingEnabled) {
            sharingEnabled = requestedSharing
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(sharingEnabled))
            if (sharingEnabled) startSharingUpdates() else callback?.let { fused.removeLocationUpdates(it) }.also { callback = null }
        }
        val monitorZones = ZoneStore.get(this).all().any { it.enabled && (it.alertOnEnter || it.alertOnExit) }
        if (!sharingEnabled && !monitorZones) stopSelf()
        if (sharingEnabled && ::fused.isInitialized && ::realtime.isInitialized) {
            fused.lastLocation.addOnSuccessListener { location ->
                location?.let { realtime.sendLocation(it.latitude, it.longitude, batteryPercentage()) }
            }
        }
        return START_STICKY
    }
    override fun onBind(intent: Intent?): IBinder? = null
    companion object { private const val CHANNEL = "location_sharing"; private const val NOTIFICATION_ID = 1001 }
}
