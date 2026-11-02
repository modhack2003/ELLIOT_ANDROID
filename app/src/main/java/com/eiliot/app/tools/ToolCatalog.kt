package com.eiliot.app.tools

import org.json.JSONArray
import org.json.JSONObject

data class ToolSpec(val name: String, val label: String, val description: String, val properties: JSONObject,
    val required: List<String> = emptyList(), val approval: Boolean = false) {
    fun schema() = JSONObject().put("type", "function").put("function", JSONObject().put("name", name).put("description", description)
        .put("parameters", JSONObject().put("type", "object").put("properties", properties).put("additionalProperties", false).put("required", JSONArray(required))))
    fun validate(arguments: JSONObject) {
        require(arguments.keys().asSequence().all { properties.has(it) }) { "Unknown argument for $label" }
        require(required.all { arguments.has(it) && !arguments.isNull(it) }) { "Missing argument for $label" }
        for (key in arguments.keys()) {
            val p = properties.getJSONObject(key); val value = arguments.get(key)
            when (p.getString("type")) {
                "string" -> require(value is String && value.length <= p.optInt("maxLength", 2000)) { "Invalid $key" }
                "integer" -> require(value is Number && value.toDouble() == value.toInt().toDouble() && value.toDouble() in p.getDouble("minimum")..p.getDouble("maximum")) { "Invalid $key" }
            }
            p.optJSONArray("enum")?.let { options -> require((0 until options.length()).any { options.get(it) == value }) { "Unsupported $key" } }
        }
    }
}

object ToolCatalog {
    private fun text(max: Int = 1000) = JSONObject().put("type", "string").put("maxLength", max)
    private fun choice(vararg values: String) = text().put("enum", JSONArray(values.toList()))
    private fun number(min: Int, max: Int) = JSONObject().put("type", "integer").put("minimum", min).put("maximum", max)
    private fun props(vararg values: Pair<String, JSONObject>) = JSONObject().also { o -> values.forEach { o.put(it.first, it.second) } }
    val specs = listOf(
        ToolSpec("device_info", "Check phone status", "Read real model, Android version, battery, RAM, storage, network and granted companion capabilities. No unique identifiers or other apps' private files.", props()),
        ToolSpec("list_apps", "Find an app", "List up to 60 visible launchable apps per page. Optional name filter and offset; follow next_offset if has_more. Not a complete system package inventory.", props("query" to text(100), "offset" to number(0, 10000))),
        ToolSpec("open_app", "Open app", "Open a launchable package returned by list_apps. Opening is not proof of completing a task inside it.", props("package" to text(200)), listOf("package"), true),
        ToolSpec("open_settings", "Open phone settings", "Show an Android settings page. The owner must change restricted system switches there.", props("page" to choice("internet", "wifi", "bluetooth", "display", "battery", "storage", "sound", "accessibility", "app")), listOf("page"), true),
        ToolSpec("set_media_volume", "Change media volume", "Set media volume to 0–100 percent. Does not change alarms, calls or Do Not Disturb.", props("percent" to number(0, 100)), listOf("percent"), true),
        ToolSpec("set_timer", "Prepare a timer", "Open the clock timer with a duration and label. Request a hands-free timer start where Clock supports it; do not claim independently verified running state.", props("seconds" to number(1, 86400), "label" to text(100)), listOf("seconds"), true),
        ToolSpec("search_web", "Search the web", "Fetch up to six live web search titles, links and snippets. Cite their URLs. Search-provider availability varies.", props("query" to text(500)), listOf("query")),
        ToolSpec("read_web_page", "Read a web page", "Fetch public HTTPS page text with a source URL. No login, local-network URLs, JavaScript execution or private account access.", props("url" to text(2000)), listOf("url")),
        ToolSpec("open_url", "Open in browser", "Open a public HTTPS URL in the owner's browser. This does not read or complete a page's workflow.", props("url" to text(2000)), listOf("url"), true),
        ToolSpec("read_screen", "Read current screen", "On a matching owner request, read non-sensitive visible text and element references from the current external app, and return it to this model. Requires screen-reading switch and Accessibility.", props(), approval = true),
        ToolSpec("click_screen", "Tap a screen item", "Click a reference from a fresh read_screen result. Requires phone-control switch, Accessibility and matching command scope. Sensitive submission/permission/payment controls must be finished manually.", props("ref" to number(1, 120)), listOf("ref"), true),
        ToolSpec("type_text", "Fill a text field", "Replace text in an editable reference from read_screen, within the owner request. Never passwords or protected apps. Does not press Send or submit.", props("ref" to number(1, 120), "text" to text(1000)), listOf("ref", "text"), true),
        ToolSpec("scroll_screen", "Scroll the current app", "Scroll a visible scrollable area one step and read the screen again for fresh references.", props("direction" to choice("down", "up")), listOf("direction"), true),
        ToolSpec("navigate", "Phone navigation", "Perform Android Back, Home, Recents or show Notifications within the owner request. Requires Accessibility and phone-control switch.", props("action" to choice("back", "home", "recents", "notifications")), listOf("action"), true),
        ToolSpec("save_memory", "Remember a note", "Save an owner-requested note into encrypted local memory when memory is enabled.", props("text" to text(2000)), listOf("text"), true),
        ToolSpec("recall_memory", "Recall saved notes", "Retrieve matching saved notes when memory is enabled. No invented memories.", props("query" to text(500)), listOf("query"))
    )
    fun schemas() = JSONArray().also { a -> specs.forEach { a.put(it.schema()) } }
    fun get(name: String) = specs.firstOrNull { it.name == name } ?: error("Unknown phone tool: $name")
    fun nanoCatalog(): String = specs.joinToString("\n") { spec ->
        "${spec.name}(" + spec.properties.keys().asSequence().joinToString(",") { key ->
            val p = spec.properties.getJSONObject(key)
            "$key${if (key in spec.required) "!" else "?"}:${p.optJSONArray("enum") ?: p.getString("type") }"
        } + ") ${spec.description.take(110)}"
    }
}
