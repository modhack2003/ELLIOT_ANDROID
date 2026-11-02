package com.eiliot.app.tools

import android.text.Html
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.CancellationException
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.io.StringReader
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.resume

object WebPolicy {
    fun url(value: String): HttpUrl = value.toHttpUrl().also {
        require(it.isHttps && it.username.isEmpty() && it.password.isEmpty() && !it.host.endsWith(".local") && it.host != "localhost") { "Use a public HTTPS URL without embedded credentials" }
        if (it.host.contains(':') || it.host.matches(Regex("\\d+(?:\\.\\d+){3}"))) require(publicAddress(InetAddress.getByName(it.host))) { "Local/private IP destinations are excluded" }
    }
    fun publicAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress || address.isMulticastAddress) return false
        val b = address.address.map { it.toInt() and 255 }
        return if (b.size == 4) !(b[0] == 0 || b[0] >= 224 || (b[0] == 100 && b[1] in 64..127) || (b[0] == 198 && b[1] in 18..19))
        else b[0] and 0xe0 == 0x20
    }
}

object WebTools {
    private data class Loaded(val text: String, val url: String, val redirect: Boolean = false)
    private val calls = java.util.concurrent.ConcurrentHashMap.newKeySet<Call>()
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).dns(object : Dns {
            override fun lookup(hostname: String): List<InetAddress> = Dns.SYSTEM.lookup(hostname).also { addresses ->
                if (addresses.isEmpty() || !addresses.all(WebPolicy::publicAddress)) throw java.net.UnknownHostException("Private/local network destinations are excluded from web tools")
            }
        }).build()
    fun cancelAll() { calls.toList().forEach(Call::cancel); calls.clear() }
    private suspend fun fetchOnce(url: String): Loaded = suspendCancellableCoroutine { continuation ->
        val call = http.newCall(Request.Builder().url(WebPolicy.url(url)).header("User-Agent", "Mozilla/5.0 (compatible; ELLIOT/0.3; personal Android companion)").build())
        calls.add(call)
        continuation.invokeOnCancellation { call.cancel(); calls.remove(call) }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { calls.remove(call); if (continuation.isActive) continuation.resumeWithException(e) }
            override fun onResponse(call: Call, response: Response) {
                val value = runCatching { response.use { r ->
                    if (r.code in setOf(301, 302, 303, 307, 308)) {
                        val next = r.request.url.resolve(r.header("Location").orEmpty()) ?: error("Invalid redirect")
                        return@use Loaded("", WebPolicy.url(next.toString()).toString(), true)
                    }
                    check(r.isSuccessful) { "Web source returned HTTP ${r.code}; login-only or blocked pages need the browser" }
                    val type = r.body?.contentType()?.subtype.orEmpty()
                    check(type.contains("html") || type.contains("xml") || type.contains("rss") || type == "plain") { "This source is not a text page" }
                    val bytes = r.body?.byteStream()?.use { input ->
                        val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(16384)
                        while (true) { val n = input.read(buffer); if (n < 0) break; check(out.size() + n <= 1000000) { "Page exceeds the 1 MB reading limit" }; out.write(buffer, 0, n) }
                        out.toByteArray()
                    } ?: error("Empty web response")
                    Loaded(String(bytes, r.body?.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8), r.request.url.toString())
                } }
                calls.remove(call)
                if (continuation.isActive) value.fold(continuation::resume, continuation::resumeWithException)
            }
        })
    }
    private suspend fun fetch(url: String): Loaded {
        var current = url
        repeat(4) {
            val result = fetchOnce(current)
            if (!result.redirect) return result
            current = result.url
        }
        error("Too many redirects; open this page in the browser")
    }
    private fun readable(html: String): String = Html.fromHtml(html.replace(Regex("(?is)<(script|style|nav|header|footer)\\b[^>]*>.*?</\\1>"), " "), Html.FROM_HTML_MODE_LEGACY)
        .toString().replace(Regex("[ \\t]+"), " ").replace(Regex("\\n{3,}"), "\n\n").trim().take(11000)
    suspend fun page(url: String): String {
        val loaded = fetch(url)
        return JSONObject().put("source", loaded.url).put("retrieved_at", java.time.Instant.now().toString()).put("untrusted_page_text", readable(loaded.text)).toString()
    }
    suspend fun search(query: String): String {
        require(query.isNotBlank()) { "Enter a search query" }
        try {
            val ddg = "https://html.duckduckgo.com/html/".toHttpUrl().newBuilder().addQueryParameter("q", query).build().toString()
            val rows = WebSearchParser.duckDuckGo(fetch(ddg).text)
            check(WebSearchParser.relevant(query, rows)) { "Search results did not match the query" }
            return searchResult(query, "DuckDuckGo HTML", rows)
        } catch (e: CancellationException) { throw e } catch (_: Exception) { /* A separate source is tried; results must still match the query. */ }
        val url = "https://www.bing.com/search".toHttpUrl().newBuilder().addQueryParameter("format", "rss").addQueryParameter("q", query).build().toString()
        val xml = fetch(url).text
        require(!xml.contains("<!DOCTYPE", true) && !xml.contains("<!ENTITY", true)) { "Unsupported search feed declarations" }
        val parser = XmlPullParserFactory.newInstance().newPullParser().apply { setInput(StringReader(xml)) }
        val results = JSONArray(); var item: JSONObject? = null
        while (parser.eventType != XmlPullParser.END_DOCUMENT && results.length() < 6) {
            if (parser.eventType == XmlPullParser.START_TAG) when (parser.name) {
                "item" -> item = JSONObject()
                "title", "link", "description" -> if (item != null) { val name = parser.name; item.put(name, readable(parser.nextText()).take(1500)) }
            }
            if (parser.eventType == XmlPullParser.END_TAG && parser.name == "item") {
                val row = item
                if (row != null && runCatching { WebPolicy.url(row.optString("link")) }.isSuccess) results.put(row)
                item = null
            }
            parser.next()
        }
        check(results.length() > 0) { "Search provider returned no readable results; use Open web search in Tools" }
        val rows = (0 until results.length()).map { results.getJSONObject(it) }.map { WebSearchHit(it.optString("title"), it.optString("link"), it.optString("description")) }
        check(WebSearchParser.relevant(query, rows)) { "Search results were unrelated to the query. Try another query or Open web search in Tools." }
        return searchResult(query, "Bing RSS fallback", rows)
    }
    private fun searchResult(query: String, source: String, rows: List<WebSearchHit>): String = JSONObject().put("query", query).put("provider", source)
        .put("retrieved_at", java.time.Instant.now().toString()).put("untrusted_search_results", JSONArray().also { a ->
            rows.forEach { a.put(JSONObject().put("title", it.title).put("url", it.url).put("snippet", it.snippet)) }
        }).toString()
}
