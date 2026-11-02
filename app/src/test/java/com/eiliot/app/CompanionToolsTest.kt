package com.eiliot.app

import com.eiliot.app.llm.*
import com.eiliot.app.tools.*
import com.eiliot.app.ui.OutputFormat
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress

class CompanionToolsTest {
    private fun fragment(index: Int, id: String? = null, name: String? = null, args: String? = null): JSONArray {
        val function = JSONObject(); name?.let { function.put("name", it) }; args?.let { function.put("arguments", it) }
        return JSONArray().put(JSONObject().put("index", index).put("function", function).also { if (id != null) it.put("id", id) })
    }
    @Test fun fragmentedArgumentsAreAssembledBeforeExecution() {
        val buffer = ToolCallBuffer()
        buffer.accept(fragment(0, "call_1", "open_", "{\"pack"))
        buffer.accept(fragment(0, name = "app", args = "age\":\"org.example.app\"}"))
        val call = buffer.complete().single()
        assertEquals("open_app", call.name); assertEquals("org.example.app", JSONObject(call.arguments).getString("package"))
    }
    @Test fun incompleteArgumentsAreNotExecutable() {
        val buffer = ToolCallBuffer(); buffer.accept(fragment(0, "one", "open_app", "{\"package\":"))
        assertThrows(Exception::class.java) { buffer.complete() }
    }
    @Test fun excessiveAndDuplicateToolCallsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { ToolCallBuffer().accept(fragment(8, "x", "device_info", "{}")) }
        val buffer = ToolCallBuffer(); buffer.accept(fragment(0, "same", "device_info", "{}")); buffer.accept(fragment(1, "same", "device_info", "{}"))
        assertThrows(IllegalArgumentException::class.java) { buffer.complete() }
    }
    @Test fun argumentTypesBoundsAndExtraKeysAreCheckedBeforeApproval() {
        val spec = ToolCatalog.get("set_media_volume")
        listOf("{\"percent\":101}", "{\"percent\":-1}", "{\"percent\":4.5}", "{\"percent\":\"50\"}", "{\"percent\":50,\"shell\":\"anything\"}", "{}").forEach {
            assertThrows(IllegalArgumentException::class.java) { spec.validate(JSONObject(it)) }
        }
        spec.validate(JSONObject("{\"percent\":0}")); spec.validate(JSONObject("{\"percent\":100}"))
    }
    @Test fun arbitraryToolsAndSettingsAreNotAccepted() {
        assertThrows(IllegalStateException::class.java) { ToolCatalog.get("run_shell") }
        assertThrows(IllegalArgumentException::class.java) { ToolCatalog.get("open_settings").validate(JSONObject("{\"page\":\"root\"}")) }
        assertEquals(16, ToolCatalog.specs.map { it.name }.distinct().size)
    }
    @Test fun actionLoopPassesActualResultsBackToTheModel() = runBlocking {
        var round = 0; var executions = 0
        val reply = AgentLoop.run(listOf(ChatMessage("user", "What is my battery?")), request = { messages ->
            if (round++ == 0) ModelTurn("", listOf(ToolCall("battery", "device_info", "{}")))
            else {
                assertEquals("tool", messages.last().role); assertEquals("battery", messages.last().toolCallId)
                assertTrue(messages.last().content.contains("61%")); assertEquals("device_info", messages[messages.size - 2].toolCalls.single().name)
                ModelTurn("Your battery is 61%.", emptyList())
            }
        }, execute = { executions++; "{\"ok\":true,\"battery\":\"61%\"}" })
        assertEquals(1, executions); assertEquals("Your battery is 61%.", reply)
    }
    @Test fun repeatedRequestIdsCannotRunTheSameActionAgain() = runBlocking {
        var executions = 0
        val error = runCatching { AgentLoop.run(listOf(ChatMessage("user", "Open app")), request = {
            ModelTurn("", listOf(ToolCall("same", "open_app", "{}")))
        }, execute = { executions++; "result" }) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException); assertEquals(1, executions)
    }
    @Test fun loopingModelHasABoundedNumberOfActions() = runBlocking {
        var round = 0; var count = 0
        val error = runCatching { AgentLoop.run(listOf(ChatMessage("user", "Task")), request = {
            ModelTurn("", listOf(ToolCall("call_${round++}", "device_info", "{}")))
        }, execute = { count++; "real result" }) }.exceptionOrNull()
        assertTrue(error is IllegalStateException); assertEquals(6, count)
    }
    @Test fun denialReachesTheModelWithoutFabricatedSuccess() = runBlocking {
        var round = 0
        val reply = AgentLoop.run(listOf(ChatMessage("user", "Open app")), request = { messages ->
            if (round++ == 0) ModelTurn("", listOf(ToolCall("one", "open_app", "{}")))
            else { assertTrue(messages.last().content.contains("declined")); ModelTurn("You declined; I did not open it.", emptyList()) }
        }, execute = { "{\"ok\":false,\"error\":\"Owner declined\"}" })
        assertTrue(reply.contains("did not"))
    }
    @Test fun sixthActionStillGetsAFinalSummaryTurn() = runBlocking {
        var requests = 0; var executions = 0
        val reply = AgentLoop.run(listOf(ChatMessage("user", "Task")), request = { messages ->
            if (requests++ < 6) ModelTurn("", listOf(ToolCall("step_$requests", "device_info", "{}")))
            else { assertEquals("tool", messages.last().role); ModelTurn("All six checks finished.", emptyList()) }
        }, execute = { executions++; "real result" })
        assertEquals(6, executions); assertEquals(7, requests); assertEquals("All six checks finished.", reply)
    }
    @Test fun cancellationBetweenBatchedCallsPreventsTheNextExecution() = runBlocking {
        var executions = 0
        val job = launch {
            AgentLoop.run(listOf(ChatMessage("user", "Task")), request = {
                ModelTurn("", listOf(ToolCall("a", "device_info", "{}"), ToolCall("b", "device_info", "{}")))
            }, execute = { executions++; currentCoroutineContext().cancel(); "completed first action" })
        }
        job.join(); assertTrue(job.isCancelled); assertEquals(1, executions)
    }
    @Test fun cancelledApprovalCannotBeApprovedLater() = runBlocking {
        ActionApprovals.cancel()
        try {
            var executed = false
            val job = launch { if (ActionApprovals.request("Open app", "Example app")) executed = true }
            yield(); val old = ActionApprovals.pending.value!!
            withTimeout(1000) { job.cancelAndJoin() }
            ActionApprovals.resolve(old.id, true)
            assertFalse(executed); assertNull(ActionApprovals.pending.value)
        } finally { ActionApprovals.cancel() }
    }
    @Test fun globalCancellationDeniesAPendingApproval() = runBlocking {
        ActionApprovals.cancel()
        try {
            val result = async { ActionApprovals.request("Read screen", "Share selected text") }
            yield(); ActionApprovals.cancel()
            assertFalse(result.await()); assertNull(ActionApprovals.pending.value)
        } finally { ActionApprovals.cancel() }
    }
    @Test fun approvalForAnotherIdDoesNotAuthorizeTheCurrentAction() = runBlocking {
        ActionApprovals.cancel()
        try {
            val result = async { ActionApprovals.request("Change volume", "20 percent") }
            yield(); val current = ActionApprovals.pending.value!!.id
            ActionApprovals.resolve(current + 1, true); yield(); assertFalse(result.isCompleted)
            ActionApprovals.resolve(current, false); assertFalse(result.await())
        } finally { ActionApprovals.cancel() }
    }
    @Test fun nanoActionsAreStructuredAndPlainTextIsNeverExecuted() {
        val action = NanoActionCodec.parse("{\"tool\":\"device_info\",\"arguments\":{}}", "nano_1")
        assertEquals("device_info", action.calls.single().name)
        assertTrue(NanoActionCodec.parse("Open the settings app", "nano_2").calls.isEmpty())
        assertThrows(IllegalArgumentException::class.java) { NanoActionCodec.parse("{\"tool\":\"open_app\",\"arguments\":{},\"approved\":true}", "nano_3") }
    }
    @Test fun webToolsRejectCleartextCredentialsAndPrivateAddresses() {
        listOf("http://example.org", "file:///data/private", "https://user:secret@example.org", "https://localhost/", "https://192.168.1.1/", "https://[::1]/").forEach { url ->
            assertThrows(IllegalArgumentException::class.java) { WebPolicy.url(url) }
        }
        listOf("127.0.0.1", "10.0.0.2", "169.254.169.254", "100.64.0.1", "172.16.0.1", "192.168.1.1", "::1", "fc00::1").forEach {
            assertFalse(it, WebPolicy.publicAddress(InetAddress.getByName(it)))
        }
        assertTrue(WebPolicy.publicAddress(InetAddress.getByName("1.1.1.1")))
        assertEquals("example.org", WebPolicy.url("https://example.org/path?q=hello").host)
    }
    @Test fun captionTailsDoNotStartInsideEmojiPairs() {
        assertEquals("B", OutputFormat.tail("A😀B", 2)); assertEquals("😀B", OutputFormat.tail("A😀B", 3))
        assertEquals("", OutputFormat.tail("hello", 0)); assertEquals("hello", OutputFormat.tail("hello", 100))
    }
    @Test fun largeToolReceiptsRemainValidBoundedJson() {
        val text = "\"😀\\\n".repeat(6000)
        val encoded = ToolReceipt.success("read_web_page", text)
        assertTrue(encoded.length <= 11500)
        val parsed = JSONObject(encoded)
        assertTrue(parsed.getBoolean("truncated")); assertTrue(parsed.getBoolean("ok"))
        val result = parsed.getString("result")
        assertFalse(result.last().isHighSurrogate())
    }
    @Test fun searchParserHandlesActualProviderMarkupAndUnwrapsDestinationLinks() {
        val html = javaClass.getResourceAsStream("/search/ddg_android.html")!!.bufferedReader().use { it.readText() }
        val results = WebSearchParser.duckDuckGo(html)
        assertEquals(6, results.size)
        assertEquals("https://developer.android.com/", results.first().url)
        assertTrue(results.first().title.contains("Android")); assertTrue(results.first().snippet.isNotBlank())
        assertTrue(WebSearchParser.relevant("Android Developers", results))
        assertTrue(results.none { it.url.contains("duckduckgo.com/l/") })
    }
    @Test fun irrelevantSearchResultsDoNotBecomeCurrentFacts() {
        assertFalse(WebSearchParser.relevant("Android Developers", listOf(WebSearchHit("How to make pizza", "https://example.org/pizza", "Cheese and tomatoes"))))
        assertTrue(WebSearchParser.relevant("Android Developers", listOf(WebSearchHit("Android guides", "https://developer.android.com", "Build apps"))))
    }
    @Test fun searchParserSkipsPrivateAndExecutableUrls() {
        val html = "<a class='result-link' href='javascript:alert(1)'>Invalid</a>" +
            "<a class='result-link' href='https://127.0.0.1/private'>Local</a>" +
            "<a class='result-link' href='https://example.org/?a=1&amp;b=2'>Useful &amp; public</a><td class='result-snippet'>A <b>result</b>.</td>"
        val result = WebSearchParser.duckDuckGo(html).single()
        assertEquals("Useful & public", result.title); assertEquals("https://example.org/?a=1&b=2", result.url)
    }
}
