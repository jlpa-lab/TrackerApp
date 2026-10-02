package com.mobile.trackerapp.domain.model

/**
 * User notification preferences
 */
data class NotificationPreferences(
    // Main toggle
    val notificationsEnabled: Boolean = true,

    // Notification type toggles
    val taskCompletionNotifications: Boolean = true,
    val engagementNotifications: Boolean = true,
    val featureAnnouncementNotifications: Boolean = true,

    // Sound and vibration
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val notificationSound: String = "default", // "none", "default", "alert", "notification"

    // Do Not Disturb
    val doNotDisturbEnabled: Boolean = false,
    val doNotDisturbStartHour: Int = 22, // 10 PM
    val doNotDisturbEndHour: Int = 8, // 8 AM

    // Frequency and limits
    val notificationFrequency: String = "instant", // "instant", "daily", "weekly"
    val maxNotificationsPerDay: Int = 20
)

/**
 * Notification sound options
 */
object NotificationSounds {
    const val NONE = "none"
    const val DEFAULT = "default"
    const val ALERT = "alert"
    const val NOTIFICATION = "notification"

    fun isValid(sound: String): Boolean {
        return sound in listOf(NONE, DEFAULT, ALERT, NOTIFICATION)
    }

    fun getDisplayName(sound: String): String {
        return when (sound) {
            NONE -> "None"
            DEFAULT -> "Default"
            ALERT -> "Alert"
            NOTIFICATION -> "Notification"
            else -> "Unknown"
        }
    }
}

/**
 * Notification frequency options
 */
object NotificationFrequencies {
    const val INSTANT = "instant"
    const val DAILY = "daily"
    const val WEEKLY = "weekly"

    fun isValid(frequency: String): Boolean {
        return frequency in listOf(INSTANT, DAILY, WEEKLY)
    }

    fun getDisplayName(frequency: String): String {
        return when (frequency) {
            INSTANT -> "Instant"
            DAILY -> "Daily Summary"
            WEEKLY -> "Weekly Summary"
            else -> "Unknown"
        }
    }
}
