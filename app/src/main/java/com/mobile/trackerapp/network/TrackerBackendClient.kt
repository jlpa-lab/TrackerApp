package com.mobile.trackerapp.network

import android.content.Context
import android.provider.Settings
import com.mobile.trackerapp.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class TrackerBackendClient(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("tracker_backend", Context.MODE_PRIVATE)
    private val deviceId = Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID)
    private val appId = "phone-tracker-0012"
    private suspend fun call(path: String, method: String, body: JSONObject? = null): JSONObject = withContext(Dispatchers.IO) {
        val connection = (URL(BuildConfig.TRACKER_BACKEND_URL.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method; connectTimeout = 10_000; readTimeout = 10_000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("X-Device-ID", deviceId); setRequestProperty("X-App-ID", appId)
            prefs.getString("session_token", null)?.let { setRequestProperty("X-Session-Token", it) }
            if (body != null) { doOutput = true; outputStream.use { it.write(body.toString().toByteArray()) } }
        }
        val text = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream).bufferedReader().use { it.readText() }
        if (connection.responseCode !in 200..299) error(JSONObject(text).optString("error", "backend request failed"))
        JSONObject(text)
    }
    suspend fun register(name: String? = null, avatar: String? = null) {
        val body = JSONObject().put("device_id", deviceId).put("app_id", appId)
        name?.takeIf { it.isNotBlank() }?.let { body.put("name", it) }
        avatar?.takeIf { it.isNotBlank() }?.let { body.put("avatar", it) }
        val result = call("/api/v1/installations", "POST", body)
        prefs.edit().putString("session_token", result.getString("session_token")).apply()
    }
    suspend fun updateProfile(name: String) = call("/api/v1/profile", "PUT", JSONObject().put("name", name))
    suspend fun createFriendInvite() = call("/api/v1/friends/invite", "POST")
    suspend fun sendFriendRequest(code: String) = call("/api/v1/friends/requests", "POST", JSONObject().put("code", code))
    suspend fun listRequests() = call("/api/v1/friends/requests", "GET")
    suspend fun listSentRequests() = call("/api/v1/friends/requests/sent", "GET")
    suspend fun decideRequest(id: String, action: String) = call("/api/v1/friends/requests/$id", "POST", JSONObject().put("action", action))
    suspend fun submitFeedback(rating: Int, feedback: String) = call("/api/v1/feedback", "POST", JSONObject().put("rating", rating).put("feedback", feedback))
}
