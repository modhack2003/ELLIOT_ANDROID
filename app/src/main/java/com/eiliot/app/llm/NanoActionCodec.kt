package com.eiliot.app.llm

import org.json.JSONObject

object NanoActionCodec {
    fun parse(raw: String, id: String): ModelTurn {
        val value = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        if (!value.startsWith("{")) return ModelTurn(raw, emptyList())
        val json = JSONObject(value)
        if (json.has("tool")) {
            require(json.keys().asSequence().all { it in setOf("tool", "arguments") }) { "Nano returned an invalid action envelope" }
            return ModelTurn("", listOf(ToolCall(id, json.getString("tool"), json.getJSONObject("arguments").toString())))
        }
        require(json.keys().asSequence().all { it == "reply" }) { "Nano returned an invalid reply envelope" }
        return ModelTurn(json.getString("reply"), emptyList())
    }
}
