package com.eiliot.app.voice

import android.content.Context

data class CommandSpeech(val model: MultilingualModelPack.Spec, val language: String) {
    init {
        require(model in MultilingualModelPack.models)
        require(language in languages)
    }

    val label get() = "${model.label} · ${languages.getValue(language)}"

    companion object {
        val languages = mapOf("en" to "Indian English", "hi" to "Hindi-English", "bn" to "Bengali-English", "" to "Auto")

        fun load(context: Context): CommandSpeech? {
            val saved = context.getSharedPreferences("command_speech", 0).getString("selection", null) ?: return null
            return runCatching {
                val (model, language) = saved.split('|', limit = 2)
                CommandSpeech(MultilingualModelPack.spec(model), language)
            }.getOrNull()
        }

        fun save(context: Context, speech: CommandSpeech?) {
            if (speech != null) require(MultilingualModelPack.available(context, speech.model))
            check(context.getSharedPreferences("command_speech", 0).edit()
                .putString("selection", speech?.let { "${it.model.id}|${it.language}" }).commit())
        }
    }
}
