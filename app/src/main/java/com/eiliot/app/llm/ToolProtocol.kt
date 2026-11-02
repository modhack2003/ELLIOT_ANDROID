package com.eiliot.app.llm

import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

data class ToolCall(val id: String, val name: String, val arguments: String)
sealed interface ModelEvent {
    data class Text(val value: String) : ModelEvent
    data class Call(val value: ToolCall) : ModelEvent
}
data class ModelTurn(val text: String, val calls: List<ToolCall>)

/** No tool is executed until every streamed argument fragment has arrived and parsed. */
class ToolCallBuffer {
    private data class Parts(val id: StringBuilder = StringBuilder(), val name: StringBuilder = StringBuilder(), val args: StringBuilder = StringBuilder())
    private val parts = sortedMapOf<Int, Parts>()
    fun accept(deltas: JSONArray) {
        for (i in 0 until deltas.length()) {
            val delta = deltas.getJSONObject(i); val index = delta.getInt("index")
            require(index in 0..7) { "Too many parallel tool requests" }
            val p = parts.getOrPut(index) { Parts() }
            if (!delta.isNull("id")) p.id.append(delta.getString("id"))
            delta.optJSONObject("function")?.let { f ->
                if (!f.isNull("name")) p.name.append(f.getString("name"))
                if (!f.isNull("arguments")) p.args.append(f.getString("arguments"))
            }
            require(p.id.length <= 200 && p.name.length <= 80 && p.args.length <= 12000) { "Tool request exceeds limits" }
        }
    }
    fun complete(): List<ToolCall> = parts.values.map {
        require(it.id.isNotBlank() && it.name.isNotBlank()) { "Incomplete tool request" }
        JSONObject(it.args.toString())
        ToolCall(it.id.toString(), it.name.toString(), it.args.toString())
    }.also { calls -> require(calls.map { it.id }.distinct().size == calls.size) { "Duplicate tool request IDs" } }
}

object AgentLoop {
    suspend fun run(initial: List<ChatMessage>, request: suspend (List<ChatMessage>) -> ModelTurn,
        execute: suspend (ToolCall) -> String, step: (ToolCall, String?) -> Unit = { _, _ -> }): String {
        val messages = initial.toMutableList(); val ids = mutableSetOf<String>(); val visible = StringBuilder()
        repeat(7) { round ->
            currentCoroutineContext().ensureActive()
            val turn = request(messages.toList())
            currentCoroutineContext().ensureActive()
            if (turn.text.isNotBlank()) { if (visible.isNotEmpty()) visible.append("\n\n"); visible.append(turn.text) }
            if (turn.calls.isEmpty()) return visible.toString().also { check(it.isNotBlank()) { "The model returned no visible answer" } }
            check(round < 6) { "Task reached its action-round limit. No additional action was executed; completed actions remain in the activity list." }
            require(turn.calls.size <= 8 && turn.calls.all { ids.add(it.id) } && ids.size <= 10) { "Task action limit reached; ask ELLIOT to continue" }
            messages += ChatMessage("assistant", turn.text, turn.calls)
            for (call in turn.calls) {
                currentCoroutineContext().ensureActive()
                step(call, null)
                val result = execute(call).take(12000)
                currentCoroutineContext().ensureActive()
                messages += ChatMessage("tool", result, toolCallId = call.id)
                step(call, result)
            }
        }
        error("Task reached its step limit. Completed actions remain in the activity list.")
    }
}
