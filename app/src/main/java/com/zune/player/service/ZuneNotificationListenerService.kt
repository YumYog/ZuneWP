package com.zune.player.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ZuneNotificationListenerService : NotificationListenerService() {

    companion object {
        private val _activeNotifications = MutableStateFlow<List<StatusBarNotification>>(emptyList())
        val activeNotifications: StateFlow<List<StatusBarNotification>> = _activeNotifications

        private var instance: ZuneNotificationListenerService? = null

        fun updateNotifications() {
            instance?.let { service ->
                try {
                    _activeNotifications.value = service.activeNotifications.toList()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        updateNotifications()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        instance = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        updateNotifications()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        updateNotifications()
    }
}
