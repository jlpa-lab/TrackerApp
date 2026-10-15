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

class LocationSharingService : Service() {
    private lateinit var fused: FusedLocationProviderClient
    private lateinit var realtime: RealtimeLocationClient
    private var callback: LocationCallback? = null
    override fun onCreate() {
        super.onCreate(); createChannel(); startForeground(NOTIFICATION_ID, notification())
        if (!getSharedPreferences("tracker_location", MODE_PRIVATE).getBoolean("enabled", false)) { stopSelf(); return }
        fused = LocationServices.getFusedLocationProviderClient(this)
        realtime = RealtimeLocationClient(this) { }
        realtime.connect()
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED && checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) { stopSelf(); return }
        callback = object : LocationCallback() { override fun onLocationResult(result: LocationResult) { result.lastLocation?.let { realtime.sendLocation(it.latitude, it.longitude, batteryPercentage()) } } }
        fused.requestLocationUpdates(LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L).setMinUpdateDistanceMeters(5f).build(), callback!!, Looper.getMainLooper())
    }
    private fun batteryPercentage(): Float = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let { it.getIntExtra("level", 0) * 100f / it.getIntExtra("scale", 100).coerceAtLeast(1) } ?: 0f
    private fun createChannel() { getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL, "Location sharing", NotificationManager.IMPORTANCE_LOW)) }
    private fun notification(): Notification = NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.mipmap.ic_launcher).setContentTitle("Location sharing active").setContentText("Sharing your live location with accepted friends").setOngoing(true).build()
    override fun onDestroy() { callback?.let { fused.removeLocationUpdates(it) }; if (::realtime.isInitialized) realtime.close(); super.onDestroy() }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null
    companion object { private const val CHANNEL = "location_sharing"; private const val NOTIFICATION_ID = 1001 }
}
