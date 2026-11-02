package com.eiliot.app.core

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Helpers for the permissions ELLIOT's perception/voice need. The UI (owned by the
 * design agent) calls these to drive the grant flows.
 */
object EiliotPermissions {

    fun isNotificationAccessGranted(ctx: Context): Boolean {
        val flat = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners")
            ?: return false
        val cn = ComponentName(ctx, EiliotNotificationListener::class.java).flattenToString()
        return flat.split(':').mapNotNull(ComponentName::unflattenFromString).any {
            it == ComponentName.unflattenFromString(cn)
        }
    }

    fun openNotificationAccessSettings(ctx: Context) {
        com.eiliot.app.overlay.OverlayManager.suspendForSettings()
        ctx.startActivity(
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
