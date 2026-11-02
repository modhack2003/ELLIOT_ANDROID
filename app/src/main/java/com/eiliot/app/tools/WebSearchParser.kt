package com.eiliot.app.tools

import okhttp3.HttpUrl.Companion.toHttpUrl

data class WebSearchHit(val title: String, val url: String, val snippet: String)

object WebSearchParser {
    private fun clean(value: String): String = value.replace(Regex("(?s)<[^>]+>"), " ")
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ")
        .replace(Regex("\\s+"), " ").trim()
    private fun attribute(attrs: String, name: String): String? = Regex("(?is)\\b$name\\s*=\\s*([\"'])(.*?)\\1").find(attrs)?.groupValues?.get(2)?.let(::clean)
    fun duckDuckGo(html: String): List<WebSearchHit> {
        val links = Regex("(?is)<a\\b([^>]*)>(.*?)</a>").findAll(html).filter {
            attribute(it.groupValues[1], "class")?.split(' ')?.any { value -> value in setOf("result__a", "result-link") } == true
        }.toList()
        return links.mapIndexedNotNull { index, match -> runCatching {
            val href = attribute(match.groupValues[1], "href") ?: error("No result URL")
            val link = (if (href.startsWith("//")) "https:$href" else href).toHttpUrl()
            val destination = if (link.host == "duckduckgo.com" && link.encodedPath.startsWith("/l/")) link.queryParameter("uddg") ?: error("No destination") else link.toString()
            val url = WebPolicy.url(destination).toString()
            val tail = html.substring(match.range.last + 1, links.getOrNull(index + 1)?.range?.first ?: html.length)
            val snippet = Regex("(?is)<(?:a|td|div)\\b[^>]*class\\s*=\\s*[\"'][^\"']*(?:result__snippet|result-snippet)[^\"']*[\"'][^>]*>(.*?)</(?:a|td|div)>")
                .find(tail)?.groupValues?.get(1).orEmpty()
            WebSearchHit(clean(match.groupValues[2]).take(250), url, clean(snippet).take(900))
        }.getOrNull() }.filter { it.title.isNotBlank() }.distinctBy { it.url }.take(6)
    }
    fun relevant(query: String, results: List<WebSearchHit>): Boolean {
        val words = query.lowercase().split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length >= 3 && it !in setOf("the", "for", "and", "how", "what", "with", "from", "news", "latest", "today") }
        return results.isNotEmpty() && (words.isEmpty() || results.any { row -> words.any { word -> (row.title + " " + row.snippet + " " + row.url).contains(word, true) } })
    }
}
