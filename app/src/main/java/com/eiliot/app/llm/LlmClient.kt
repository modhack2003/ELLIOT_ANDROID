package com.eiliot.app.llm

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.mapNotNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ChatMessage(val role: String, val content: String, val toolCalls: List<ToolCall> = emptyList(), val toolCallId: String? = null)
class LlmClient(private val http: OkHttpClient = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(90, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()) {
    companion object {
        private val calls = java.util.concurrent.ConcurrentHashMap.newKeySet<okhttp3.Call>()
        private val turnCalls = java.util.concurrent.ConcurrentHashMap<Long, MutableSet<okhttp3.Call>>()
        fun cancelActiveCall(turnTicket:Long) { turnCalls.remove(turnTicket)?.toList()?.forEach { it.cancel();calls.remove(it) } }
        fun cancelAll() { calls.toList().forEach { it.cancel() }; calls.clear() }
    }
    private fun <T> execute(req: okhttp3.Request, block: (okhttp3.Response) -> T): T {
        val call = http.newCall(req); calls.add(call)
        return try { call.execute().use(block) } finally { calls.remove(call) }
    }
    private fun root(raw: String): String {
        val value = raw.trim().replace(Regex("\\s+"), "").let { if (it.contains("://")) it else "https://$it" }.trimEnd('/')
        require(value.toHttpUrl().isHttps) { "Use an HTTPS API root to protect your key" }
        return value
    }
    private fun request(base: String, key: String, path: String) = Request.Builder().url(root(base) + path)
        .header("Authorization", "Bearer ${key.trim()}")
    fun discoverModels(baseUrl: String, apiKey: String): Result<List<String>> = runCatching {
        execute(request(baseUrl, apiKey, "/models").get().build()) { response ->
            check(response.isSuccessful) { "Model discovery failed (HTTP ${response.code}); enter a model manually if unsupported" }
            val data = JSONObject(response.body?.string().orEmpty()).optJSONArray("data") ?: JSONArray()
            (0 until data.length()).map { data.getJSONObject(it).getString("id") }.distinct().sorted()
        }
    }
    fun testChat(baseUrl: String, apiKey: String, model: String): Result<String> = runCatching {
        val json = JSONObject().put("model", model).put("max_tokens", 16).put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", "Reply with: online")))
        execute(request(baseUrl, apiKey, "/chat/completions").post(json.toString().toRequestBody("application/json".toMediaType())).build()) {
            check(it.isSuccessful) { "Connection test failed (HTTP ${it.code})" }; "Connection established"
        }
    }
    fun testTools(baseUrl: String, apiKey: String, model: String): Result<String> = runCatching {
        val definition = JSONObject().put("type", "function").put("function", JSONObject().put("name", "capability_probe")
            .put("description", "Protocol test only; no action is executed").put("parameters", JSONObject().put("type", "object").put("properties", JSONObject())))
        val json = JSONObject().put("model", model).put("max_tokens", 128).put("tools", JSONArray().put(definition))
            .put("tool_choice", JSONObject().put("type", "function").put("function", JSONObject().put("name", "capability_probe")))
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", "Call capability_probe with empty arguments.")))
        execute(request(baseUrl, apiKey, "/chat/completions").post(json.toString().toRequestBody("application/json".toMediaType())).build()) { response ->
            check(response.isSuccessful) { "Tool protocol test failed (HTTP ${response.code}); choose a function-tool capable model/provider" }
            val function = JSONObject(response.body?.string().orEmpty()).getJSONArray("choices").getJSONObject(0).getJSONObject("message")
                .optJSONArray("tool_calls")?.optJSONObject(0)?.optJSONObject("function")
            check(function?.optString("name") == "capability_probe") { "The provider did not return a structured tool call. Direct controls remain available in Tools." }
            JSONObject(function!!.getString("arguments"))
            "Structured tool calling confirmed. No phone action was executed."
        }
    }
    fun streamChat(baseUrl: String, apiKey: String, model: String, messages: List<ChatMessage>): Flow<String> =
        streamTurn(baseUrl, apiKey, model, messages).mapNotNull { (it as? ModelEvent.Text)?.value }
    fun streamTurn(baseUrl: String, apiKey: String, model: String, messages: List<ChatMessage>, tools: JSONArray? = null, turnTicket:Long?=null): Flow<ModelEvent> = callbackFlow {
        val arr = JSONArray(); messages.forEach { m ->
            val item = JSONObject().put("role", m.role).put("content", m.content)
            m.toolCallId?.let { item.put("tool_call_id", it) }
            if (m.toolCalls.isNotEmpty()) item.put("tool_calls", JSONArray().also { a -> m.toolCalls.forEach {
                a.put(JSONObject().put("id", it.id).put("type", "function").put("function", JSONObject().put("name", it.name).put("arguments", it.arguments)))
            } })
            arr.put(item)
        }
        val json = JSONObject().put("model", model).put("stream", true).put("max_tokens", 4096).put("messages", arr)
        if (tools != null && tools.length() > 0) json.put("tools", tools).put("tool_choice", "auto")
        val call = http.newCall(request(baseUrl, apiKey, "/chat/completions").post(json.toString().toRequestBody("application/json".toMediaType())).build())
        calls.add(call)
        turnTicket?.let { turnCalls.computeIfAbsent(it) { java.util.concurrent.ConcurrentHashMap.newKeySet() }.add(call) }
        val reader = launch(Dispatchers.IO) {
            try {
                val toolBuffer = ToolCallBuffer()
                call.execute().use { response ->
                    check(response.isSuccessful) { "Model request failed (HTTP ${response.code}). ${if (tools != null) "Choose a model/provider supporting function tools; Action shortcuts also work directly in Companion." else "Check the endpoint and model in Settings."}" }
                    val source = response.body?.source() ?: error("Empty response")
                    while (isActive && !source.exhausted()) {
                        val line = source.readUtf8LineStrict(1048576)
                        if (!line.startsWith("data:")) continue
                        val data = line.substringAfter("data:").trim()
                        if (data == "[DONE]") break
                        if (data.isBlank()) continue
                        val event = JSONObject(data)
                        check(!event.has("error")) { "The model provider returned a streaming error" }
                        val delta = event.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta") ?: continue
                        val token = if (delta.isNull("content")) "" else delta.optString("content")
                        if (token.isNotEmpty()) send(ModelEvent.Text(token))
                        delta.optJSONArray("tool_calls")?.let(toolBuffer::accept)
                    }
                }
                toolBuffer.complete().forEach { send(ModelEvent.Call(it)) }
                close()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (isActive) close(e) }
        }
        // Installed immediately: cancellation closes an in-progress socket/read, not just a completed call.
        awaitClose { call.cancel(); calls.remove(call);turnTicket?.let { id -> turnCalls[id]?.let { set -> set.remove(call);if(set.isEmpty())turnCalls.remove(id,set) } }; reader.cancel() }
    }
}
