package com.eiliot.app.core

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.eiliot.app.R
import com.eiliot.app.EiliotApp
import com.eiliot.app.MainActivity
import com.eiliot.app.overlay.OverlayManager
import com.eiliot.app.voice.VoiceIo

class SentinelCoreService : LifecycleService() {
    private var receiver: BroadcastReceiver? = null
    override fun onCreate() {
        super.onCreate()
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) { OverlayManager.setOrbPaused(i.action == Intent.ACTION_SCREEN_OFF) }
        }
        receiver = r
        ContextCompat.registerReceiver(this, r, IntentFilter().apply { addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_USER_PRESENT) }, ContextCompat.RECEIVER_NOT_EXPORTED)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_OFF) { SentinelRuntime.stop(); stopSelf(); return START_NOT_STICKY }
        if (!SentinelRuntime.alive()) { stopSelf(); return START_NOT_STICKY }
        val mic = intent?.getBooleanExtra("microphone", false) == true &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val off = PendingIntent.getService(this, 1, Intent(this, SentinelCoreService::class.java).setAction(ACTION_OFF), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(this, EiliotApp.CHANNEL_CORE).setSmallIcon(R.drawable.ic_stat_eiliot)
            .setContentTitle("ELLIOT · ${if (mic) "voice ready" else "core active"}")
            .setContentText("Tap to open · Off stops voice, learning and autonomy").setContentIntent(open)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Turn off", off).setOngoing(true).build()
        val type = (if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0) or
            (if (mic && Build.VERSION.SDK_INT >= 30) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0)
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(1001, n, type) else startForeground(1001, n)
        } catch (_: Exception) { SentinelRuntime.status.value = "Open ELLIOT and tap Resume to allow voice"; stopSelf(); return START_NOT_STICKY }
        SentinelRuntime.microphoneReady = mic
        SentinelRuntime.status.value = if (mic) "Core active · voice can follow you" else "Core active · open app to arm voice"
        VoiceIo.init(applicationContext); ProactiveEngine.start(applicationContext)
        if (mic && SentinelRuntime.store.wakeEnabled) WakeListener.start(applicationContext)
        SentinelRuntime.refreshFeatures()
        if (SentinelRuntime.store.orbEnabled && (!SentinelRuntime.foreground || SentinelRuntime.store.orbInApp)) OverlayManager.show(applicationContext)
        return START_STICKY
    }
    override fun onDestroy() {
        if (SentinelRuntime.alive()) SentinelRuntime.stop()
        receiver?.let { runCatching { unregisterReceiver(it) } }; receiver = null
        SentinelRuntime.microphoneReady = false; ProactiveEngine.stop(); WakeListener.stop(); VoiceIo.stopListening(); VoiceIo.stopSpeaking(); OverlayManager.hide()
        super.onDestroy()
    }
    companion object {
        const val ACTION_START = "com.eiliot.app.START_USER"
        const val ACTION_OFF = "com.eiliot.app.OFF"
        fun start(ctx: Context) {
            val mic = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            SentinelRuntime.start(ctx, mic && SentinelRuntime.foreground)
        }
    }
}
