package com.mobile.trackerapp.ui.tools

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.mobile.trackerapp.R
import java.util.concurrent.Executors

/** Delivers entry/exit notifications for saved zones while the app is not visible. */
class ZoneGeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return
        val transition = event.geofenceTransition
        if (transition != Geofence.GEOFENCE_TRANSITION_ENTER && transition != Geofence.GEOFENCE_TRANSITION_EXIT) return
        val ids = event.triggeringGeofences.orEmpty().mapNotNull { it.requestId.toLongOrNull() }
        if (ids.isEmpty()) return

        val pendingResult = goAsync()
        worker.execute {
            try {
                val byId = ZoneStore.get(context).all().associateBy { it.id }
                ids.forEach { id ->
                    val zone = byId[id] ?: return@forEach
                    if (!zone.enabled) return@forEach
                    val entering = transition == Geofence.GEOFENCE_TRANSITION_ENTER
                    if (entering && !zone.alertOnEnter || !entering && !zone.alertOnExit) return@forEach
                    postNotification(context, zone, entering)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun postNotification(context: Context, zone: ZoneRecord, entering: Boolean) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, context.getString(R.string.notification_zone_alerts), NotificationManager.IMPORTANCE_DEFAULT))
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            zone.id.toInt(),
            Intent(context, ZoneAlertActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val category = context.getString(if (zone.safe) R.string.notification_safe_zone else R.string.notification_danger_zone)
        val contentText = context.getString(if (entering) R.string.notification_entered_category else R.string.notification_exited_category, category, zone.address.ifBlank { zone.name })
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_location_pin_white)
            .setColor(if (zone.safe) Color.GREEN else Color.RED)
            .setContentTitle(context.getString(if (entering) R.string.notification_arrived_at else R.string.notification_left_zone, zone.name))
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        manager.notify((zone.id xor (if (entering) 0x13579L else 0x24680L)).toInt(), notification)
    }

    companion object {
        const val CHANNEL_ID = "saved_zone_alerts"
        private val worker = Executors.newSingleThreadExecutor()
    }
}
