package com.mobile.trackerapp.notification

import android.content.Context
import android.util.Log
import com.onesignal.OneSignal
import com.onesignal.debug.LogLevel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralized OneSignal SDK manager
 * Handles all OneSignal interactions and initialization
 */
@Singleton
class OneSignalManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "OneSignalManager"
        // OneSignal App ID - configured for production
        private const val ONESIGNAL_APP_ID = "77db9255-b035-4690-8b63-8a6b8375f12b"
    }

    private var isInitialized = false
    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Initialize OneSignal SDK
     * Must be called during app startup (in Application.onCreate)
     */
    fun initialize() {
        if (isInitialized) {
            Log.d(TAG, "OneSignal already initialized")
            return
        }

        try {
            // Set log level for debugging (remove in production)
            OneSignal.Debug.logLevel = LogLevel.VERBOSE
            
            // Initialize OneSignal with the App ID
            OneSignal.initWithContext(context, ONESIGNAL_APP_ID)
            
            isInitialized = true
            Log.d(TAG, "OneSignal initialized successfully with App ID: $ONESIGNAL_APP_ID")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing OneSignal: ${e.message}", e)
        }
    }

    /**
     * Set the external user ID for tracking
     * Call this after user authentication
     */
    fun setExternalUserId(userId: String) {
        try {
            OneSignal.login(userId)
            Log.d(TAG, "Set external user ID: $userId")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting external user ID: ${e.message}", e)
        }
    }

    /**
     * Clear the external user ID on logout
     */
    fun clearExternalUserId() {
        try {
            OneSignal.logout()
            Log.d(TAG, "Cleared external user ID")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing external user ID: ${e.message}", e)
        }
    }

    /**
     * Get the OneSignal subscription ID (unique device ID)
     * Returns null or empty if not yet registered with OneSignal servers
     */
    fun getSubscriptionId(): String? {
        return try {
            OneSignal.User.pushSubscription.id
        } catch (e: Exception) {
            Log.e(TAG, "Error getting subscription ID: ${e.message}", e)
            null
        }
    }

    /**
     * Check if device has a real, server-assigned subscription ID
     * Returns false for local- placeholder IDs (pre-registration)
     */
    fun isRegisteredWithServer(): Boolean {
        val subscriptionId = getSubscriptionId()
        return !subscriptionId.isNullOrEmpty() && !subscriptionId.startsWith("local-")
    }

    /**
     * Add custom tags for segmentation
     */
    fun addTags(tags: Map<String, String>) {
        try {
            tags.forEach { (key, value) ->
                OneSignal.User.addTag(key, value)
            }
            Log.d(TAG, "Added tags: $tags")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding tags: ${e.message}", e)
        }
    }

    /**
     * Remove tags
     */
    fun removeTags(tagKeys: List<String>) {
        try {
            tagKeys.forEach { key ->
                OneSignal.User.removeTag(key)
            }
            Log.d(TAG, "Removed tags: $tagKeys")
        } catch (e: Exception) {
            Log.e(TAG, "Error removing tags: ${e.message}", e)
        }
    }

    /**
     * Add email for user subscription
     */
    fun addEmail(email: String) {
        try {
            OneSignal.User.addEmail(email)
            Log.d(TAG, "Added email: $email")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding email: ${e.message}", e)
        }
    }

    /**
     * Remove email from user subscription
     */
    fun removeEmail(email: String) {
        try {
            OneSignal.User.removeEmail(email)
            Log.d(TAG, "Removed email: $email")
        } catch (e: Exception) {
            Log.e(TAG, "Error removing email: ${e.message}", e)
        }
    }

    /**
     * Add SMS number for user subscription
     */
    fun addSms(number: String) {
        try {
            OneSignal.User.addSms(number)
            Log.d(TAG, "Added SMS: $number")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding SMS: ${e.message}", e)
        }
    }

    /**
     * Remove SMS number from user subscription
     */
    fun removeSms(number: String) {
        try {
            OneSignal.User.removeSms(number)
            Log.d(TAG, "Removed SMS: $number")
        } catch (e: Exception) {
            Log.e(TAG, "Error removing SMS: ${e.message}", e)
        }
    }

    /**
     * Request push notification permission from the user
     */
    suspend fun requestPushPermission() = withContext(Dispatchers.Main) {
        try {
            OneSignal.Notifications.requestPermission(true)
            Log.d(TAG, "Push permission requested")
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting push permission: ${e.message}", e)
        }
    }

    /**
     * Set notification subscription status
     */
    fun setNotificationSubscription(subscribed: Boolean) {
        try {
            Log.d(TAG, "Set notification subscription: $subscribed")
            // OneSignal SDK handles subscription state
        } catch (e: Exception) {
            Log.e(TAG, "Error setting notification subscription: ${e.message}", e)
        }
    }

    /**
     * Enable verbose logging (for development only)
     */
    fun enableVerboseLogging() {
        try {
            OneSignal.Debug.logLevel = LogLevel.VERBOSE
            Log.d(TAG, "Verbose logging enabled")
        } catch (e: Exception) {
            Log.e(TAG, "Error enabling verbose logging: ${e.message}", e)
        }
    }

    /**
     * Disable verbose logging
     */
    fun disableVerboseLogging() {
        try {
            OneSignal.Debug.logLevel = LogLevel.NONE
            Log.d(TAG, "Verbose logging disabled")
        } catch (e: Exception) {
            Log.e(TAG, "Error disabling verbose logging: ${e.message}", e)
        }
    }
}
