package com.eiliot.app.llm

import com.eiliot.app.core.SentinelRuntime
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

/** Genuine ML Kit adapter; no silent cloud fallback and no background inference. */
object NanoClient {
    val availability = MutableStateFlow(com.eiliot.app.capabilities.NanoState.UNCHECKED)
    val status = MutableStateFlow("Not checked · requires supported AICore device")
    private val client by lazy { Generation.getClient() }
    private var setupJob: Job? = null
    fun setup(download: Boolean) {
        setupJob?.cancel()
        setupJob = SentinelRuntime.scope.launch {
            try { if (download) download() else check() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { availability.value = com.eiliot.app.capabilities.NanoState.FAILED; status.value = e.message?.take(180) ?: "AICore unavailable" }
        }
    }
    fun stopSetup() { setupJob?.cancel(); setupJob = null }

    suspend fun check(): Int {
        check(SentinelRuntime.foreground) { "Open ELLIOT to check Gemini Nano" }
        val state = try { client.checkStatus() }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { availability.value = com.eiliot.app.capabilities.NanoState.FAILED; throw e }
        availability.value = when (state) {
            FeatureStatus.AVAILABLE -> com.eiliot.app.capabilities.NanoState.READY
            FeatureStatus.DOWNLOADABLE -> com.eiliot.app.capabilities.NanoState.DOWNLOADABLE
            FeatureStatus.DOWNLOADING -> com.eiliot.app.capabilities.NanoState.DOWNLOADING
            else -> com.eiliot.app.capabilities.NanoState.UNAVAILABLE
        }
        status.value = when (state) {
            FeatureStatus.AVAILABLE -> "Ready · on-device Gemini Nano"
            FeatureStatus.DOWNLOADABLE -> "Supported · model download required"
            FeatureStatus.DOWNLOADING -> "Model is downloading"
            else -> "Unavailable on this device/configuration"
        }
        return state
    }
    suspend fun download() {
        check(SentinelRuntime.foreground) { "Keep ELLIOT open during model setup" }
        if (check() == FeatureStatus.DOWNLOADABLE) {
            status.value = "Downloading Nano through AICore…"
            availability.value = com.eiliot.app.capabilities.NanoState.DOWNLOADING
            client.download().collect { status.value = "AICore model download in progress…" }
        }
        check()
    }
    suspend fun generate(messages: List<ChatMessage>, systemLimit: Int = 2500, onToken: (String) -> Unit) {
        check(SentinelRuntime.foreground) { "Gemini Nano requires the ELLIOT activity in front; open the app to continue" }
        check(check() == FeatureStatus.AVAILABLE) { status.value }
        val system = messages.firstOrNull { it.role == "system" }?.content.orEmpty().take(systemLimit)
        val current = messages.lastOrNull()?.content.orEmpty().take(if (systemLimit > 2500) 2500 else 5000)
        val recent = com.eiliot.app.data.ContextBudget.select(messages.drop(1).dropLast(1).map { m ->
            if (m.toolCalls.isEmpty()) m else m.copy(content = m.content + "\nACTION: " + m.toolCalls.joinToString { "${it.name} ${it.arguments}" })
        }, 11000 - system.length - current.length)
        val prompt = "SYSTEM: $system\n\n" + recent.joinToString("\n\n") { "${it.role.uppercase()}: ${it.content}" } + "\n\n${messages.lastOrNull()?.role?.uppercase() ?: "USER"}: $current"
        client.generateContentStream(prompt).collect { result ->
            check(SentinelRuntime.foreground) { "Nano paused because ELLIOT left the foreground" }
            result.candidates.firstOrNull()?.text?.let(onToken)
        }
    }
}
