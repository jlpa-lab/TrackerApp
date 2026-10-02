package com.mobile.trackerapp.domain.model

/**
 * Domain model for notifications
 */
data class Notification(
    val id: String,
    val title: String,
    val message: String,
    val type: NotificationType,
    val priority: NotificationPriority,
    val imageUrl: String? = null,
    val actionUrl: String? = null,
    val isRead: Boolean = false,
    val createdAt: Long,
    val oneSignalId: String? = null
)

enum class NotificationType {
    TASK_COMPLETION,    // AI task finished, generation complete
    ENGAGEMENT,         // Usage reminders, keep-alive alerts
    FEATURE_ANNOUNCEMENT // New features, app updates
}

enum class NotificationPriority {
    HIGH,               // Urgent: task completion, critical alerts
    MEDIUM,             // Important: feature announcements
    LOW                 // Informational: tips, suggestions
}

/**
 * Request model for sending notifications
 */
data class SendNotificationRequest(
    val userId: String,
    val title: String,
    val message: String,
    val type: String,
    val priority: String,
    val imageUrl: String? = null,
    val actionUrl: String? = null,
    val data: Map<String, String>? = null
)

/**
 * Response model for notification endpoints
 */
data class NotificationResponse(
    val status: String,
    val message: String,
    val notificationId: String? = null
)
