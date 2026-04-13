package com.example.sitconnect

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotificationSpyService : NotificationListenerService() {
    companion object {
        var instance: NotificationSpyService? = null
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        instance = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)
        // Can be used to keep a history of notifications
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)
    }

    fun getActiveNotificationsList(): List<StatusBarNotification> {
        return try {
            activeNotifications?.toList() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}

