package com.eiliot.app.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED || !SentinelRuntime.store.masterEnabled) return
        // No microphone restart from boot. An explicit Resume arms voice after unlock.
        SentinelRuntime.start(context, false)
    }
}
