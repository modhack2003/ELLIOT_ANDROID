package com.eiliot.app.core

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Perception: feeds every incoming notification (WhatsApp, SMS, email, etc.) into the
 * ProactiveEngine so ELLIOT can notice and comment on what's happening on the device.
 * Requires the user to grant Notification Access in system settings.
 */
class EiliotNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        if (sbn.packageName == applicationContext.packageName) return
        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        ProactiveEngine.onNotification(sbn.packageName, title, text)
    }
}
