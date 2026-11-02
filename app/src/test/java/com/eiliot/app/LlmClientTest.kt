package com.eiliot.app

import com.eiliot.app.llm.ChatMessage
import com.eiliot.app.llm.LlmClient
import com.eiliot.app.llm.ModelEvent
import com.eiliot.app.llm.ToolCall
import com.eiliot.app.tools.ToolCatalog
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class LlmClientTest {
    @Test fun bargeInCancelsOnlyItsOwnStreamAndTheNextTurnCanFinish()=runBlocking {
        val (server,client)=setup()
        try {
            val started=CompletableDeferred<Unit>()
            val event="data: {\"choices\":[{\"delta\":{\"content\":\"First sentence starts now. \"}}]}\n\n"
            server.enqueue(MockResponse().setHeader("Content-Type","text/event-stream").setBody(event+" ".repeat(10000)).throttleBody(128,100,TimeUnit.MILLISECONDS))
            val old=launch(Dispatchers.Default) {
                client.streamTurn(server.url("/v1").toString(),"test-key","model",listOf(ChatMessage("user","old")),turnTicket=701)
                    .onEach {started.complete(Unit)}.catch { }.collect()
            }
            withTimeout(10000) {started.await()}
            server.enqueue(MockResponse().setHeader("Content-Type","text/event-stream").setBody("data: {\"choices\":[{\"delta\":{\"content\":\"New answer.\"}}]}\n\ndata: [DONE]\n\n"))
            val next=async(Dispatchers.Default) {client.streamTurn(server.url("/v1").toString(),"test-key","model",listOf(ChatMessage("user","new")),turnTicket=702).toList()}
            LlmClient.cancelActiveCall(701)
            withTimeout(3000) {old.join()}
            assertEquals("New answer.",(withTimeout(10000) {next.await()}.single() as ModelEvent.Text).value)
            assertEquals(2,server.requestCount)
        }finally {LlmClient.cancelAll();server.shutdown()}
    }
    private fun setup(): Pair<MockWebServer, LlmClient> {
        val cert = HeldCertificate.Builder().commonName("localhost").addSubjectAlternativeName("localhost").addSubjectAlternativeName("127.0.0.1").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(cert).build()
        val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
        val server = MockWebServer().apply { protocols = listOf(okhttp3.Protocol.HTTP_1_1); useHttps(serverTls.sslSocketFactory(), false); start(java.net.InetAddress.getByName("127.0.0.1"), 0) }
        val http = OkHttpClient.Builder().proxy(java.net.Proxy.NO_PROXY).protocols(listOf(okhttp3.Protocol.HTTP_1_1)).dns(object : okhttp3.Dns { override fun lookup(hostname: String): List<java.net.InetAddress> = listOf(java.net.InetAddress.getByName("127.0.0.1")) }).sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager).readTimeout(5, TimeUnit.SECONDS).build()
        return server to LlmClient(http)
    }
    @Test fun streamedReplyPreservesHistoryAndOnlyVisibleContent() = runBlocking {
        val (server, client) = setup()
        try {
            server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody("data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"hidden\"}}]}\n\ndata: {\"choices\":[{\"delta\":{\"content\":\"Hello \"}}]}\n\ndata: {\"choices\":[{\"delta\":{\"content\":\"again\"}}]}\n\ndata: [DONE]\n\n"))
            val messages = listOf(ChatMessage("system", "ELLIOT"), ChatMessage("user", "old"), ChatMessage("assistant", "previous"), ChatMessage("user", "new"))
            assertEquals("Hello again", client.streamChat(server.url("/v1").toString(), "test-key", "test-model", messages).toList().joinToString(""))
            val request = server.takeRequest(2, TimeUnit.SECONDS)!!
            assertEquals("/v1/chat/completions", request.path)
            assertEquals(4, JSONObject(request.body.readUtf8()).getJSONArray("messages").length())
        } finally { server.shutdown() }
    }
    @Test fun stoppingInterruptsAnActiveSocketRead() = runBlocking {
        val (server, client) = setup()
        try {
            val firstToken = CompletableDeferred<Unit>()
            val firstEvent = "data: {\"choices\":[{\"delta\":{\"content\":\"first\"}}]}\n\n"
            server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(firstEvent + " ".repeat(4096)).throttleBody(128, 100, TimeUnit.MILLISECONDS))
            val job = launch(Dispatchers.Default) {
                client.streamChat(server.url("/v1").toString(), "test-key", "test-model", listOf(ChatMessage("user", "hi")))
                    .onEach { firstToken.complete(Unit) }.collect()
            }
            withTimeout(10000) { firstToken.await() }
            withTimeout(1500) { job.cancelAndJoin() }
            assertTrue(job.isCancelled)
            assertNotNull(server.takeRequest(1, TimeUnit.SECONDS))
        } finally { server.shutdown() }
    }
    @Test fun cleartextEndpointsAreRejectedBeforeSendingKeys() {
        val result = LlmClient().discoverModels("http://127.0.0.1:1/v1", "test-key")
        assertTrue(result.isFailure); assertTrue(result.exceptionOrNull()!!.message!!.contains("HTTPS"))
    }
    @Test fun functionCallsStreamAsStructuredEventsAndResultsKeepTheirCallIds() = runBlocking {
        val (server, client) = setup()
        try {
            server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(
                "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"phone_1\",\"function\":{\"name\":\"device_info\",\"arguments\":\"{\"}}]}}]}\n\n" +
                "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"function\":{\"arguments\":\"}\"}}]}}]}\n\ndata: [DONE]\n\n"))
            val events = client.streamTurn(server.url("/v1").toString(), "test-key", "tools-model", listOf(ChatMessage("user", "Check phone")), ToolCatalog.schemas()).toList()
            assertEquals("device_info", (events.single() as ModelEvent.Call).value.name)
            val first = JSONObject(server.takeRequest(2, TimeUnit.SECONDS)!!.body.readUtf8())
            assertEquals(16, first.getJSONArray("tools").length()); assertEquals("auto", first.getString("tool_choice"))
            server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody("data: {\"choices\":[{\"delta\":{\"content\":\"Battery checked.\"}}]}\n\ndata: [DONE]\n\n"))
            val history = listOf(ChatMessage("user", "Check phone"), ChatMessage("assistant", "", listOf(ToolCall("phone_1", "device_info", "{}"))), ChatMessage("tool", "actual battery", toolCallId = "phone_1"))
            client.streamTurn(server.url("/v1").toString(), "test-key", "tools-model", history, ToolCatalog.schemas()).toList()
            val next = JSONObject(server.takeRequest(2, TimeUnit.SECONDS)!!.body.readUtf8()).getJSONArray("messages")
            assertEquals("phone_1", next.getJSONObject(2).getString("tool_call_id"))
            assertEquals("device_info", next.getJSONObject(1).getJSONArray("tool_calls").getJSONObject(0).getJSONObject("function").getString("name"))
        } finally { server.shutdown() }
    }
    @Test fun toolSupportProbeChecksStructuredCallsWithoutRunningPhoneActions() {
        val (server, client) = setup()
        try {
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("{\"choices\":[{\"message\":{\"tool_calls\":[{\"function\":{\"name\":\"capability_probe\",\"arguments\":\"{}\"}}]}}]}"))
            assertTrue(client.testTools(server.url("/v1").toString(), "test-key", "tools-model").isSuccess)
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("{\"choices\":[{\"message\":{\"content\":\"I support tools\"}}]}"))
            assertTrue(client.testTools(server.url("/v1").toString(), "test-key", "text-only-model").isFailure)
        } finally { server.shutdown() }
    }
}
