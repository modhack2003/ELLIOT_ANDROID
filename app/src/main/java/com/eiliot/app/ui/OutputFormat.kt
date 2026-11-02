package com.eiliot.app.ui

enum class BlockKind { TEXT, HEADING, BULLET, CODE }
data class OutputBlock(val kind: BlockKind, val text: String)
object OutputFormat {
    fun blocks(value: String): List<OutputBlock> {
        val result = mutableListOf<OutputBlock>(); val buffer = StringBuilder(); var code = false
        fun flush() { if (buffer.isNotBlank()) result += OutputBlock(if (code) BlockKind.CODE else BlockKind.TEXT, buffer.toString().trimEnd()); buffer.clear() }
        for (line in value.lines()) {
            if (line.trimStart().startsWith("```")) { flush(); code = !code; continue }
            if (code) { buffer.appendLine(line); continue }
            when {
                line.isBlank() -> flush()
                line.matches(Regex("^#{1,6}\\s+.*")) -> { flush(); result += OutputBlock(BlockKind.HEADING, line.replaceFirst(Regex("^#{1,6}\\s+"), "")) }
                line.matches(Regex("^\\s*[-*]\\s+.*")) -> { flush(); result += OutputBlock(BlockKind.BULLET, line.replaceFirst(Regex("^\\s*[-*]\\s+"), "")) }
                line.matches(Regex("^\\s*\\d+[.)]\\s+.*")) -> { flush(); result += OutputBlock(BlockKind.TEXT, line.trimStart()) }
                else -> buffer.appendLine(line)
            }
        }
        flush(); return result
    }
    fun plain(value: String): String = blocks(value).joinToString("\n\n") { if (it.kind == BlockKind.BULLET) "• ${it.text}" else it.text }.replace("**", "").replace("`", "")
    /** Bounds both paragraphs and newline-heavy output without cutting a UTF-16 pair. */
    fun prefix(value: String, maxChars: Int, maxLines: Int = Int.MAX_VALUE): String {
        if (maxChars <= 0 || maxLines <= 0) return ""
        var end = minOf(value.length, maxChars)
        var lines = 1
        for (i in 0 until end) {
            if (value[i] == '\n' && ++lines > maxLines) { end = i; break }
        }
        if (end > 0 && end < value.length && value[end - 1].isHighSurrogate() && value[end].isLowSurrogate()) end--
        return value.substring(0, end)
    }
    fun tail(value: String, maxChars: Int): String {
        if (maxChars <= 0) return ""
        var start = (value.length - maxChars).coerceAtLeast(0)
        if (start > 0 && value[start].isLowSurrogate() && value[start - 1].isHighSurrogate()) start++
        return value.substring(start)
    }
    fun advance(value: String, position: Int, step: Int): Int {
        if (position >= value.length) return value.length
        val safe = position.coerceAtLeast(0)
        val n = minOf(step.coerceAtLeast(1), value.codePointCount(safe, value.length))
        return value.offsetByCodePoints(safe, n)
    }
}
