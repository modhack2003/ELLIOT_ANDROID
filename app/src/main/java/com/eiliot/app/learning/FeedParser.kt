package com.eiliot.app.learning

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

data class FeedEntry(val title: String, val summary: String, val url: String)
object FeedParser {
    fun androidEntries(value: String): List<FeedEntry> {
        require(!Regex("(?i)<!\\s*(DOCTYPE|ENTITY)").containsMatchIn(value)) { "Feed document declarations are not allowed" }
        val parser = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }.newPullParser()
        parser.setInput(value.reader())
        val entries = mutableListOf<FeedEntry>(); var active = false; var title = ""; var url = ""; var summary = ""
        fun elementText(): String {
            val depth = parser.depth; val out = StringBuilder()
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.END_TAG && parser.depth == depth) break
                if (parser.eventType == XmlPullParser.TEXT) out.append(parser.text)
            }
            return out.toString()
        }
        while (parser.eventType != XmlPullParser.END_DOCUMENT && entries.size < 12) {
            if (parser.eventType == XmlPullParser.START_TAG) when (parser.name) {
                "item", "entry" -> { active = true; title = ""; url = ""; summary = "" }
                "title" -> if (active) title = elementText()
                "link" -> if (active) {
                    val href = parser.getAttributeValue(null, "href")
                    val rel = parser.getAttributeValue(null, "rel")
                    if (href != null) { if (rel == null || rel == "alternate") url = href }
                    else url = elementText()
                }
                "description", "summary", "content" -> if (active) summary = elementText().replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").take(1800)
            }
            if (parser.eventType == XmlPullParser.END_TAG && parser.name in setOf("entry", "item") && active) {
                if (title.isNotBlank() && (url.startsWith("https://developer.android.com/") || url.startsWith("https://android-developers.googleblog.com/"))) entries += FeedEntry(title, summary, url)
                active = false
            }
            parser.next()
        }
        return entries
    }
}
