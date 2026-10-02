package com.mobile.trackerapp.notification

/**
 * Configuration constants for notifications
 */
object NotificationConfig {
    // OneSignal App ID - configured for production
    const val ONESIGNAL_APP_ID = "77db9255-b035-4690-8b63-8a6b8375f12b"

    // Notification Channel IDs
    const val CHANNEL_ID_HIGH = "lumora_high_priority"
    const val CHANNEL_ID_MEDIUM = "lumora_medium_priority"
    const val CHANNEL_ID_LOW = "lumora_low_priority"

    // Notification Channel Names
    const val CHANNEL_NAME_HIGH = "Urgent Notifications"
    const val CHANNEL_NAME_MEDIUM = "Important Notifications"
    const val CHANNEL_NAME_LOW = "Other Notifications"

    // Notification Channel Descriptions
    const val CHANNEL_DESC_HIGH = "Urgent and critical notifications"
    const val CHANNEL_DESC_MEDIUM = "Important notifications and updates"
    const val CHANNEL_DESC_LOW = "General information and tips"

    // Notification importance levels
    const val IMPORTANCE_HIGH = 4
    const val IMPORTANCE_MEDIUM = 3
    const val IMPORTANCE_LOW = 2

    // Notification type keys
    const val NOTIFICATION_TYPE_TASK_COMPLETION = "TASK_COMPLETION"
    const val NOTIFICATION_TYPE_ENGAGEMENT = "ENGAGEMENT"
    const val NOTIFICATION_TYPE_FEATURE_ANNOUNCEMENT = "FEATURE_ANNOUNCEMENT"

    // Notification priority keys
    const val NOTIFICATION_PRIORITY_HIGH = "HIGH"
    const val NOTIFICATION_PRIORITY_MEDIUM = "MEDIUM"
    const val NOTIFICATION_PRIORITY_LOW = "LOW"

    // Deep link schemes
    const val DEEP_LINK_SCHEME = "lumora://"
    const val DEEP_LINK_TASK = "lumora://task"
    const val DEEP_LINK_FEATURE = "lumora://feature"
    const val DEEP_LINK_PROFILE = "lumora://profile"
    const val DEEP_LINK_NOTIFICATIONS = "lumora://notifications"

    // Notification retention (in days)
    const val NOTIFICATION_RETENTION_DAYS = 30

    // Notification sound options
    const val SOUND_NONE = "none"
    const val SOUND_DEFAULT = "default"
    const val SOUND_ALERT = "alert"
    const val SOUND_NOTIFICATION = "notification"

    // Vibration patterns (in milliseconds)
    val VIBRATION_LIGHT = longArrayOf(0, 100, 100, 100)
    val VIBRATION_MEDIUM = longArrayOf(0, 150, 100, 150)
    val VIBRATION_STRONG = longArrayOf(0, 200, 100, 200, 100, 200)

    // Notification grouping
    const val NOTIFICATION_GROUP_TASK = "task_completion"
    const val NOTIFICATION_GROUP_ENGAGEMENT = "engagement"
    const val NOTIFICATION_GROUP_FEATURE = "feature"
}
