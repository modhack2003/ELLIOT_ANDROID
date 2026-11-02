package com.eiliot.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.eiliot.app.core.*
import com.eiliot.app.overlay.OverlayManager
import com.eiliot.app.voice.VoiceIo

class EiliotApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val process = if(android.os.Build.VERSION.SDK_INT>=28)Application.getProcessName() else {
            getSystemService(android.app.ActivityManager::class.java).runningAppProcesses?.firstOrNull {it.pid==android.os.Process.myPid()}?.processName.orEmpty()
        }
        if(process.endsWith(":speech"))return
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL_CORE, "ELLIOT presence", NotificationManager.IMPORTANCE_LOW))
        SentinelRuntime.init(this)
        com.eiliot.app.voice.ModelAssets.start(this)
        VoiceIo.init(this)
        com.eiliot.app.capabilities.CapabilityRegistry.init(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                SentinelRuntime.foreground = true
                if (SentinelRuntime.alive()) {
                    SentinelCoreService.start(this@EiliotApp)
                    if (SentinelRuntime.store.orbEnabled && SentinelRuntime.store.orbInApp) OverlayManager.show(this@EiliotApp) else OverlayManager.hide()
                } else OverlayManager.hide()
                com.eiliot.app.capabilities.CapabilityRegistry.requestRefresh()
            }
            override fun onStop(owner: LifecycleOwner) {
                SentinelRuntime.foreground = false
                if (SentinelRuntime.store.inferenceMode == "nano" && EiliotBrain.state.value.busy) EiliotBrain.cancelCurrent()
                if (SentinelRuntime.alive() && SentinelRuntime.store.orbEnabled) OverlayManager.show(this@EiliotApp)
                com.eiliot.app.capabilities.CapabilityRegistry.requestRefresh()
            }
        })
    }
    companion object { const val CHANNEL_CORE = "eiliot_core" }
}
