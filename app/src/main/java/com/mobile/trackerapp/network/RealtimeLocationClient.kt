package com.mobile.trackerapp.network

import android.content.Context
import com.mobile.trackerapp.BuildConfig
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject
import com.mobile.trackerapp.data.LocalTrackerDb

class RealtimeLocationClient(context: Context, private val onLocation: (JSONObject) -> Unit) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("tracker_backend", Context.MODE_PRIVATE)
    private val deviceId = android.provider.Settings.Secure.getString(appContext.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
    private val socket: Socket = IO.socket(BuildConfig.TRACKER_BACKEND_URL.trimEnd('/'), IO.Options().apply { transports = arrayOf("websocket"); reconnection = true })

    fun connect() {
        socket.on(Socket.EVENT_CONNECT) {
            socket.emit("authenticate", JSONObject().put("device_id", deviceId).put("app_id", "phone-tracker-0012").put("session_token", prefs.getString("session_token", "")))
        }
        socket.on("location_updated") { args -> (args.firstOrNull() as? JSONObject)?.let { payload -> LocalTrackerDb(appContext).write("friend_location_${payload.optString("device_id")}", payload.toString()); onLocation(payload) } }
        socket.connect()
    }

    fun sendLocation(latitude: Double, longitude: Double, battery: Float) {
        if (!appContext.getSharedPreferences("tracker_location", Context.MODE_PRIVATE).getBoolean("enabled", false)) return
        if (socket.connected()) socket.emit("location_update", JSONObject().put("latitude", latitude).put("longitude", longitude).put("battery_percentage", battery.coerceIn(0f, 100f)))
    }

    fun close() { socket.disconnect(); socket.off() }
}
