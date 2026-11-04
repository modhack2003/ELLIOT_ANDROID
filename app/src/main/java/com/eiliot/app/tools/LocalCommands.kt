package com.eiliot.app.tools

import com.eiliot.app.llm.ToolCall
import org.json.JSONObject

/** Exact everyday commands work without asking a language model to select a tool. */
object LocalCommands {
    private fun call(name: String, args: JSONObject = JSONObject()) = ToolCall("local_${System.nanoTime()}", name, args.toString())
    fun parse(raw: String, appLookup: () -> List<LaunchableApp>): ToolCall? {
        val text = SpokenNumbers.normalize(raw.lowercase().trim().trimEnd('.', '!', '?', '।'))
        if (Regex("\\b(don't|do not|never)\\b").containsMatchIn(text)) return null
        if (Regex("^(?:please )?(?:set |change |turn )?(?:the |my )?(?:media |music )?volume(?: to| at)? \\d{1,3}(?: percent|%)?$").matches(text)) {
            val number = Regex("\\d+").find(text)!!.value.toInt()
            if (number in 0..100) return call("set_media_volume", JSONObject().put("percent", number))
        }
        if (text in setOf("mute", "mute media", "mute music", "mute the volume")) return call("set_media_volume", JSONObject().put("percent", 0))
        if (Regex("^(?:please )?(?:set|start|prepare|create) (?:a |the |an )?(?:timer|countdown) (?:for )?.+$").matches(text)) {
            SpokenNumbers.timerSeconds(text)?.let { return call("set_timer", JSONObject().put("seconds", it)) }
        }
        if (text in setOf("go back", "back", "go home", "home", "show recents", "open recent apps", "open notifications")) {
            val action = when { text.contains("back") -> "back"; text.contains("home") -> "home"; text.contains("recent") -> "recents"; else -> "notifications" }
            return call("navigate", JSONObject().put("action", action))
        }
        if (text in setOf("scroll down", "scroll up")) return call("scroll_screen", JSONObject().put("direction", if (text.endsWith("down")) "down" else "up"))
        if (text in setOf(
                "check my battery", "what is my battery", "what is my battery level", "what's my battery level",
                "check my phone", "check phone status", "check my free storage", "how much battery do i have",
                "मेरे फोन का battery level कितना है", "मेरे फोन का बैटरी लेवल कितना है",
                "আমার ফোনের battery level কত", "আমার ফোনের ব্যাটারি লেভেল কত"
            )) return call("device_info")
        val open = Regex("^(?:please )?(?:open|launch) (.+)$").matchEntire(raw.lowercase().trim().trimEnd('.', '!', '?'))
        if (open != null) {
            val target = open.groupValues[1].removePrefix("the ")
            val settings = mapOf("settings" to "app", "wifi settings" to "wifi", "wi-fi settings" to "wifi", "internet settings" to "internet", "bluetooth settings" to "bluetooth", "battery settings" to "battery", "sound settings" to "sound", "accessibility settings" to "accessibility")
            settings[target]?.let { return call("open_settings", JSONObject().put("page", it)) }
            fun normalized(value: String) = value.lowercase().replace(Regex("[^a-z0-9]"), "")
            val matches = appLookup().filter { normalized(it.name) == normalized(target) || it.packageName == target }
            if (matches.size == 1) return call("open_app", JSONObject().put("package", matches.single().packageName))
        }
        return null
    }
    fun reply(call: ToolCall, receipt: String): String {
        val data = JSONObject(receipt)
        if (!data.optBoolean("ok")) return data.optString("error", "The action could not finish.")
        val result = data.getString("result")
        if (call.name != "device_info") return result
        val info = JSONObject(result)
        return "## Phone status\n" + listOf("Battery", "RAM", "Internal storage", "Network").joinToString("\n") { "- $it: ${info.optString(it)}" }
    }
}
