package com.mobile.trackerapp.notification

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.Log
import com.mobile.trackerapp.notification.NotificationConfig.NOTIFICATION_RETENTION_DAYS
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles app lifecycle events related to notifications
 * Performs cleanup and maintenance tasks
 */
@Singleton
class NotificationLifecycleHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationService: NotificationService
) : Application.ActivityLifecycleCallbacks {

    companion object {
        private const val TAG = "NotificationLifecycleHandler"
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private var activeActivityCount = 0

    fun initialize() {
        (context as? Application)?.registerActivityLifecycleCallbacks(this)
        Log.d(TAG, "Notification lifecycle handler initialized")
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

    override fun onActivityStarted(activity: Activity) {
        activeActivityCount++
        Log.d(TAG, "Activity started. Active count: $activeActivityCount")
    }

    override fun onActivityResumed(activity: Activity) {}

    override fun onActivityPaused(activity: Activity) {}

    override fun onActivityStopped(activity: Activity) {
        activeActivityCount--
        Log.d(TAG, "Activity stopped. Active count: $activeActivityCount")

        // App goes to background when all activities are stopped
        if (activeActivityCount <= 0) {
            activeActivityCount = 0
            handleAppBackgrounded()
        }
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    override fun onActivityDestroyed(activity: Activity) {}

    /**
     * Handle app backgrounding - cleanup and maintenance
     */
    private fun handleAppBackgrounded() {
        scope.launch {
            try {
                Log.d(TAG, "App backgrounded - performing cleanup")

                // Clean up old notifications
              //  notificationService.deleteOldNotifications(NOTIFICATION_RETENTION_DAYS)

                Log.d(TAG, "Notification cleanup completed")
            } catch (e: Exception) {
                Log.e(TAG, "Error during app background handling: ${e.message}", e)
            }
        }
    }
}
