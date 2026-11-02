package com.eiliot.app.llm

data class ProviderPreset(val title:String,val root:String)
object ProviderPresets {
    val options=listOf(ProviderPreset("Groq","https://api.groq.com/openai/v1"),ProviderPreset("OpenRouter","https://openrouter.ai/api/v1"),
        ProviderPreset("DeepSeek","https://api.deepseek.com"),ProviderPreset("OpenAI","https://api.openai.com/v1"),ProviderPreset("Custom",""))
    /** Prefer a fast text-chat candidate returned by this account, never an invented model ID. */
    fun choose(offered:List<String>):String? = offered.filterNot { id ->
        listOf("embed","whisper","tts","image","dall-e","moderation","transcri","realtime","audio","vision-only").any { id.contains(it,true) }
    }.sortedWith(compareBy<String> { id -> when {
        id.contains("flash",true)||id.contains("mini",true)||id.contains("instant",true)->0
        id.contains("chat",true)||id.contains("instruct",true)->1
        else->2
    } }.thenBy { it }).firstOrNull()
}
