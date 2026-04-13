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

        // Automatically hide our own "Syncing data..." foreground notification if possible
        if (sbn.packageName == packageName) {
            val extras = sbn.notification.extras
            val title = extras.getString(android.app.Notification.EXTRA_TITLE)
            val text = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString()

            if (title == "SIT Connect" && text == "Syncing data...") {
                try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                        cancelNotification(sbn.key)
                    } else {
                        @Suppress("DEPRECATION")
                        cancelNotification(sbn.packageName, sbn.tag, sbn.id)
                    }
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
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
