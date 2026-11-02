package com.eiliot.app.tools

object SpokenNumbers {
    private val units = listOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen")
    private val tens = mapOf("twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50, "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90)
    private val names = (units + tens.keys + "hundred").joinToString("|")
    private val pattern = Regex("\\b(?:$names)(?:[ -]+(?:and[ -]+)?(?:$names))*\\b", RegexOption.IGNORE_CASE)
    fun normalize(text: String): String = pattern.replace(text) { match ->
        var value = 0
        for (word in match.value.lowercase().split(Regex("[ -]+"))) {
            when { word == "and" -> Unit; word == "hundred" -> value = maxOf(value, 1) * 100; word in tens -> value += tens.getValue(word); else -> value += units.indexOf(word).coerceAtLeast(0) }
        }
        value.toString()
    }
    fun timerSeconds(text: String): Int? {
        val normalized = normalize(text.lowercase())
        val matches = Regex("\\b(\\d+)\\s*(hours?|minutes?|seconds?|mins?|secs?)\\b").findAll(normalized).toList()
        if (matches.isEmpty()) return null
        if (matches.any { (it.groupValues[1].toLongOrNull() ?: Long.MAX_VALUE) > 86400 }) return null
        val result = matches.sumOf { it.groupValues[1].toLong() * when { it.groupValues[2].startsWith("h") -> 3600; it.groupValues[2].startsWith("m") -> 60; else -> 1 } }
        return result.takeIf { it in 1..86400 }?.toInt()
    }
}
