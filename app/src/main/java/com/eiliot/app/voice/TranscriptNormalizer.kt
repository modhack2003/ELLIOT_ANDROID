package com.eiliot.app.voice

/** Readable case and known spelling variants only; never guess a missing action or app name. */
object TranscriptNormalizer {
    fun normalize(text:String,appNames:List<String> = emptyList()):String {
        var value=text.trim().lowercase(java.util.Locale.US).replace(Regex("\\bper\\s+cent\\b"),"percent")
        value=value.replace(Regex("\\bi\\b"),"I").replace(Regex("\\belliot(?:t)?\\b"),"Elliot")
        for(name in appNames.filter {it.isNotBlank()}.take(80))value=value.replace(Regex("\\b${Regex.escape(name)}\\b",RegexOption.IGNORE_CASE),name)
        return value.replaceFirstChar {if(it.isLowerCase())it.titlecase(java.util.Locale.US)else it.toString()}
    }
}
