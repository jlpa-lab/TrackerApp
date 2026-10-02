package com.mobile.trackerapp.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics

object AppAnalytics {
    fun screen(context: Context, screenName: String) {
        FirebaseAnalytics.getInstance(context).logEvent(
            FirebaseAnalytics.Event.SCREEN_VIEW,
            Bundle().apply {
                putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName.take(100))
                putString(FirebaseAnalytics.Param.SCREEN_CLASS, screenName.take(100))
            }
        )
        log(context, "${screenName}_open", "screen" to screenName)
    }

    fun action(context: Context, feature: String, action: String, vararg params: Pair<String, Any?>) {
        log(context, "${feature}_${action}", "feature" to feature, "action" to action, *params)
    }

    fun log(context: Context, eventName: String, vararg params: Pair<String, Any?>) {
        val bundle = Bundle()
        params.forEach { (key, value) ->
            val safeKey = safeName(key)
            when (value) {
                null -> Unit
                is String -> bundle.putString(safeKey, value.take(100))
                is Int -> bundle.putInt(safeKey, value)
                is Long -> bundle.putLong(safeKey, value)
                is Float -> bundle.putFloat(safeKey, value)
                is Double -> bundle.putDouble(safeKey, value)
                is Boolean -> bundle.putString(safeKey, value.toString())
                else -> bundle.putString(safeKey, value.toString().take(100))
            }
        }
        FirebaseAnalytics.getInstance(context).logEvent(safeName(eventName), bundle)
    }

    private fun safeName(value: String): String =
        value.replace(Regex("[^A-Za-z0-9_]"), "_").take(40).ifBlank { "app_event" }
}
