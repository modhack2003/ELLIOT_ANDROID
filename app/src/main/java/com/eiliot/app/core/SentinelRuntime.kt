package com.eiliot.app.core

import android.content.Context
import android.content.Intent
import com.eiliot.app.data.MemoryStore
import com.eiliot.app.data.SecureStore
import com.eiliot.app.learning.LearningScheduler
import com.eiliot.app.overlay.OverlayManager
import com.eiliot.app.voice.VoiceIo
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** One cancellation and permission boundary shared by UI, services, jobs and the overlay. */
object SentinelRuntime {
    lateinit var context: Context; private set
    lateinit var store: SecureStore; private set
    lateinit var memory: MemoryStore; private set
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled
    val status = MutableStateFlow("Stopped · your memory is retained")
    val erasing = MutableStateFlow(false)
    val memoryStatus = MutableStateFlow("")
    var foreground = false
    var microphoneReady = false
    @Volatile var epoch = 0L; private set
    fun init(ctx: Context) {
        if (::context.isInitialized) return
        context = ctx.applicationContext; store = SecureStore(context); memory = MemoryStore(context)
        _enabled.value = store.masterEnabled
        EiliotBrain.init(context)
        scope.launch(Dispatchers.IO) { runCatching { memory.refresh() }.onFailure { status.value = "Memory needs recovery; settings are still available" } }
    }
    fun start(ctx: Context, microphone: Boolean = false) {
        if (erasing.value) { status.value = "Wait while local memory is erased"; return }
        init(ctx); store.masterEnabled = true; _enabled.value = true
        runCatching { ctx.startForegroundService(Intent(ctx, SentinelCoreService::class.java)
            .setAction(SentinelCoreService.ACTION_START).putExtra("microphone", microphone)) }
            .onFailure { store.masterEnabled = false; _enabled.value = false; status.value = "Open ELLIOT and tap Resume to start the service" }
    }
    fun startAndArmMic(ctx:Context,onReady:()->Unit={}) {
        if(!foreground) { status.value="Open ELLIOT to start voice";return }
        start(ctx,true);val token=epoch
        scope.launch {
            val armed=withTimeoutOrNull(5000) { while(alive(token) && !microphoneReady)delay(25);alive(token) && microphoneReady }==true
            if(armed)onReady()else if(alive(token))status.value="Voice needs microphone access. Check Settings."
        }
    }
    fun eraseMemory() {
        if (erasing.value) return
        stop(); erasing.value = true; memoryStatus.value = "Erasing local memory…"
        scope.launch {
            try {
                EiliotBrain.awaitStopped(); com.eiliot.app.learning.LearningScheduler.awaitStopped()
                withContext(Dispatchers.IO) { memory.clearAll() }
                EiliotBrain.clearConversation(); memoryStatus.value = "Local memory, session text and draft erased"
            } catch (e: Exception) { memoryStatus.value = "Erasure needs attention: ${e.message?.take(150)}" }
            finally { erasing.value = false }
        }
    }
    fun stop() {
        if (!::store.isInitialized) return
        store.masterEnabled = false; _enabled.value = false; microphoneReady = false; epoch++
        EiliotBrain.cancelCurrent(); ProactiveEngine.stop(); WakeListener.stop()
        com.eiliot.app.llm.NanoClient.stopSetup(); com.eiliot.app.llm.LlmClient.cancelAll()
        VoiceIo.stopListening(); VoiceIo.stopSpeaking(); LearningScheduler.stop(context)
        com.eiliot.app.voice.remote.NativeSpeechClient.shutdown()
        ScreenReader.clear(); OverlayManager.hide()
        context.stopService(Intent(context, SentinelCoreService::class.java))
        status.value = "Stopped · microphone, learning and autonomy are off"
    }
    fun alive(token: Long = epoch) = ::store.isInitialized && store.masterEnabled && token == epoch
    fun refreshFeatures() {
        if (!alive()) return
        if (store.learningEnabled && store.memoryEnabled) LearningScheduler.schedule(context) else LearningScheduler.stop(context)
        if (!store.wakeEnabled && !WakeListener.liveCall.value) WakeListener.stop()
    }
    fun setMemoryEnabled(enabled:Boolean) {
        store.memoryEnabled=enabled;EiliotBrain.cancelCurrent()
        if(!enabled)LearningScheduler.stop(context)else if(alive() && store.learningEnabled)LearningScheduler.schedule(context)
        com.eiliot.app.capabilities.CapabilityRegistry.requestRefresh()
    }
}
