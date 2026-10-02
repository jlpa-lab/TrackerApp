package com.mobile.trackerapp.notification

import android.content.Context
import android.util.Log
import com.mobile.trackerapp.data.repository.NotificationRepository
import com.mobile.trackerapp.domain.model.Notification
import com.mobile.trackerapp.domain.model.NotificationPriority
import com.mobile.trackerapp.domain.model.NotificationType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handler for different types of notifications
 */
@Singleton
class NotificationHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationRepository: NotificationRepository
) {
    companion object {
        private const val TAG = "NotificationHandler"
    }

    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Handle task completion notification
     * Called when AI task generation is complete
     */
    suspend fun handleTaskCompletion(
        taskId: String,
        taskTitle: String,
        taskDescription: String,
        imageUrl: String? = null
    ) {
        try {
            val notification = Notification(
                id = UUID.randomUUID().toString(),
                title = "✅ Task Complete",
                message = taskDescription,
                type = NotificationType.TASK_COMPLETION,
                priority = NotificationPriority.HIGH,
                imageUrl = imageUrl,
                actionUrl = "lumora://task/$taskId",
                isRead = false,
                createdAt = System.currentTimeMillis()
            )

           // notificationRepository.insertNotification(notification)
            Log.d(TAG, "Task completion notification saved: $taskTitle")
        } catch (e: Exception) {
            Log.e(TAG, "Error handling task completion: ${e.message}", e)
        }
    }

    /**
     * Handle user engagement notification
     * Called for reminders and keep-alive alerts
     */
    suspend fun handleEngagementNotification(
        engagementType: String, // "DAILY_REMINDER", "WEEKLY_SUMMARY", etc.
        title: String,
        message: String
    ) {
        try {
            val notification = Notification(
                id = UUID.randomUUID().toString(),
                title = title,
                message = message,
                type = NotificationType.ENGAGEMENT,
                priority = NotificationPriority.MEDIUM,
                actionUrl = "lumora://notifications",
                isRead = false,
                createdAt = System.currentTimeMillis()
            )

            //notificationRepository.insertNotification(notification)
            Log.d(TAG, "Engagement notification saved: $engagementType")
        } catch (e: Exception) {
            Log.e(TAG, "Error handling engagement notification: ${e.message}", e)
        }
    }

    /**
     * Handle feature announcement notification
     * Called when new features are released
     */
    suspend fun handleFeatureAnnouncement(
        featureId: String,
        featureName: String,
        featureDescription: String,
        imageUrl: String? = null
    ) {
        try {
            val notification = Notification(
                id = UUID.randomUUID().toString(),
                title = "🎉 New Feature: $featureName",
                message = featureDescription,
                type = NotificationType.FEATURE_ANNOUNCEMENT,
                priority = NotificationPriority.MEDIUM,
                imageUrl = imageUrl,
                actionUrl = "lumora://feature/$featureId",
                isRead = false,
                createdAt = System.currentTimeMillis()
            )

          //  notificationRepository.insertNotification(notification)
            Log.d(TAG, "Feature announcement saved: $featureName")
        } catch (e: Exception) {
            Log.e(TAG, "Error handling feature announcement: ${e.message}", e)
        }
    }

    /**
     * Handle error notification
     * For critical issues users need to know about
     */
    suspend fun handleErrorNotification(
        errorCode: String,
        errorMessage: String,
        actionUrl: String? = null
    ) {
        try {
            val notification = Notification(
                id = UUID.randomUUID().toString(),
                title = "⚠️ Alert",
                message = errorMessage,
                type = NotificationType.ENGAGEMENT,
                priority = NotificationPriority.HIGH,
                actionUrl = actionUrl ?: "lumora://notifications",
                isRead = false,
                createdAt = System.currentTimeMillis()
            )

           // notificationRepository.insertNotification(notification)
            Log.d(TAG, "Error notification saved: $errorCode")
        } catch (e: Exception) {
            Log.e(TAG, "Error handling error notification: ${e.message}", e)
        }
    }

    /**
     * Handle generic notification with custom type
     */
    suspend fun handleGenericNotification(
        title: String,
        message: String,
        type: NotificationType,
        priority: NotificationPriority,
        imageUrl: String? = null,
        actionUrl: String? = null
    ) {
        try {
            val notification = Notification(
                id = UUID.randomUUID().toString(),
                title = title,
                message = message,
                type = type,
                priority = priority,
                imageUrl = imageUrl,
                actionUrl = actionUrl,
                isRead = false,
                createdAt = System.currentTimeMillis()
            )

           // notificationRepository.insertNotification(notification)
            Log.d(TAG, "Generic notification saved: $title")
        } catch (e: Exception) {
            Log.e(TAG, "Error handling generic notification: ${e.message}", e)
        }
    }
}
