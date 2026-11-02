package com.eiliot.app.capabilities

/** Read-only native answers work without a model key, and cannot execute an action. */
object CapabilityQuestions {
    fun answer(raw: String, snapshot: CapabilitySnapshot): String? {
        val text = raw.trim().lowercase().trimEnd('.', '!', '?')
        if (text in setOf("what can you do", "what tools do you have", "show your capabilities", "check your capabilities")) {
            val tools = snapshot.entries.filter { it.tool }
            val ready = tools.filter { it.availability == Availability.READY }.joinToString(", ") { it.title }
            val conditional = tools.filter { it.canAttempt && it.availability != Availability.READY }.joinToString(", ") { it.title }
            val setup = tools.filterNot { it.canAttempt }.joinToString(", ") { it.title }
            return "## My current capabilities\n${snapshot.readyTools} of 16 tools are ready; ${snapshot.availableTools - snapshot.readyTools} are conditional.\n\n" +
                "**Ready:** ${ready.ifBlank { "None while prerequisites are missing." }}\n\n" +
                "**Conditional:** ${conditional.ifBlank { "None." }}\n\n" +
                "**Off or needs attention:** ${setup.ifBlank { "None." }}\n\n" +
                "Settings shows each capability and the access it needs. Readiness does not mean a task has already completed."
        }
        val ids = when (text) {
            "why is voice not working", "why isn't voice working", "check voice readiness" -> listOf("speech", "voice_input", "wake_word")
            "is wake word ready", "check wake word" -> listOf("wake_word")
            "why is screen reading not working", "check screen readiness" -> listOf("read_screen", "click_screen")
            "check model readiness" -> listOf("cloud_model", "nano_model")
            else -> return null
        }
        return ids.mapNotNull(snapshot::entry).joinToString("\n\n") {
            "**${it.title} · ${it.availability.label}**\n${it.reason}" + if (it.recovery != Recovery.NONE) " ${it.recovery.label}." else ""
        }
    }
}
