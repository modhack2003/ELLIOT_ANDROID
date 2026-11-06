package com.eiliot.app.voice

/** Readable case and known spelling variants only; never guess a missing action or app name. */
object TranscriptNormalizer {
    /**
     * A request spoken straight out of the wake phrase is transcribed with that phrase attached
     * ("hey Elliot what is my battery level"). Offline commands are matched exactly, so the
     * address has to come off first. Only a leading address is removed, and never the whole
     * transcript: "hey Elliot" on its own is left for the caller to recognise as a bare wake.
     */
    private val ADDRESS = Regex(
        "^\\s*(?:hey|hi|hello|ok|okay)?\\s*(?:elliot|elliott|eliot|ellie)\\s*[,.!?]*\\s+(?=\\S)",
        RegexOption.IGNORE_CASE,
    )

    fun stripWakeAddress(text: String): String {
        val trimmed = text.trim()
        val stripped = ADDRESS.replace(trimmed, "")
        return stripped.ifBlank { trimmed }
    }

    fun normalize(text:String,appNames:List<String> = emptyList()):String {
        var value=stripWakeAddress(text).lowercase(java.util.Locale.US).replace(Regex("\\bper\\s+cent\\b"),"percent")
        value=value.replace(Regex("\\bi\\b"),"I").replace(Regex("\\belliot(?:t)?\\b"),"Elliot")
        for(name in appNames.filter {it.isNotBlank()}.take(80))value=value.replace(Regex("\\b${Regex.escape(name)}\\b",RegexOption.IGNORE_CASE),name)
        return value.replaceFirstChar {if(it.isLowerCase())it.titlecase(java.util.Locale.US)else it.toString()}
    }
}
