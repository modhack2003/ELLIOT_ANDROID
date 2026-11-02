package com.eiliot.app.tools

import org.json.JSONObject

/** Created by native user input, never read from model arguments or retrieved text. */
class CommandGrant private constructor(val epoch: Long, private val request: String, private val directTool: String?) {
    private val text = SpokenNumbers.normalize(request.lowercase())
    private fun normalized(value: String) = value.lowercase().replace(Regex("[^a-z0-9]"), "")
    fun permitsApp(packageName: String, label: String) = directTool == "open_app" ||
        (normalized(label).isNotBlank() && normalized(request).contains(normalized(label))) || request.contains(packageName, true)
    fun permitsScreenLabel(label: String): Boolean {
        if (directTool == "click_screen") return true
        val ignored = setOf("button", "the", "tap", "click", "press", "select", "screen", "item", "app", "text")
        val labels = Regex("[a-z0-9]+").findAll(label.lowercase()).map { it.value }.filter { it.length >= 3 && it !in ignored }.toSet()
        val words = Regex("[a-z0-9]+").findAll(request.lowercase()).map { it.value }.toSet()
        return labels.any { it in words }
    }
    fun permits(name: String, args: JSONObject): Boolean {
        if (directTool != null) return name == directTool
        if (name in setOf("device_info", "list_apps", "recall_memory", "search_web", "read_web_page")) return true
        if (Regex("\\b(don't|do not|never|what if|what would|how do|how can|tell me how|explain how)\\b").containsMatchIn(text)) return false
        if (name != "read_screen" && Regex("^(what|why|how|explain|tell me)\\b").containsMatchIn(text.trim())) return false
        val open = Regex("\\b(open|launch|start|show|go to)\\b").containsMatchIn(text)
        val screen = Regex("\\b(screen|display|visible|looking at|see here)\\b").containsMatchIn(text)
        return when (name) {
            "open_app" -> open && !Regex("\\b(don't|do not|never)\\s+(open|launch|start)\\b").containsMatchIn(text)
            "open_settings" -> open && when (args.optString("page")) {
                "wifi" -> text.contains("wifi") || text.contains("wi-fi")
                "internet" -> text.contains("internet") || text.contains("network")
                "app" -> text.contains("settings") || text.contains("permissions")
                else -> args.optString("page").isNotBlank() && text.contains(args.optString("page"))
            }
            "open_url" -> open && args.optString("url").isNotBlank() && request.contains(args.optString("url"), true)
            "set_media_volume" -> {
                val volume = Regex("\\b(volume|mute|unmute)\\b").containsMatchIn(text)
                val requested = Regex("\\bvolume\\b[^0-9]{0,16}(\\d{1,3})\\b").find(text)?.groupValues?.get(1)?.toIntOrNull()
                    ?: Regex("\\b(\\d{1,3})\\s*(?:percent|%)").find(text)?.groupValues?.get(1)?.toIntOrNull()
                volume && (args.optInt("percent", -1) == requested || (Regex("\\bmute\\b").containsMatchIn(text) && args.optInt("percent", -1) == 0))
            }
            "set_timer" -> Regex("\\b(timer|countdown)\\b").containsMatchIn(text) && Regex("\\b(set|start|create|prepare)\\b").containsMatchIn(text) && SpokenNumbers.timerSeconds(request) == args.optInt("seconds", -1)
            "read_screen" -> screen || Regex("\\b(read|inspect|describe)\\s+(this|current|the)\\b").containsMatchIn(text) || Regex("\\b(tap|click|scroll|type|fill)\\b").containsMatchIn(text)
            "click_screen" -> Regex("\\b(tap|click|press|select)\\b").containsMatchIn(text)
            "type_text" -> Regex("\\b(type|fill|enter|replace)\\b").containsMatchIn(text) && request.contains(args.optString("text").takeIf { it.isNotBlank() } ?: return false, true)
            "scroll_screen" -> Regex("\\b(scroll|swipe)\\b").containsMatchIn(text) && (args.optString("direction") != "up" || !text.contains("down")) && (args.optString("direction") != "down" || !text.contains("up"))
            "navigate" -> when (args.optString("action")) {
                "back" -> Regex("\\b(back|previous)\\b").containsMatchIn(text)
                "home" -> Regex("\\bhome\\b").containsMatchIn(text)
                "recents" -> Regex("\\b(recents|recent apps|app switcher)\\b").containsMatchIn(text)
                "notifications" -> open && text.contains("notification")
                else -> false
            }
            "save_memory" -> Regex("\\b(remember|save|note|memorize)\\b").containsMatchIn(text)
            else -> false
        }
    }
    companion object {
        fun request(epoch: Long, text: String) = CommandGrant(epoch, text, null)
        fun direct(epoch: Long, tool: String): CommandGrant { ToolCatalog.get(tool); return CommandGrant(epoch, "", tool) }
    }
}
