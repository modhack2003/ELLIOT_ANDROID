package com.eiliot.app.tools

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.atomic.AtomicLong

data class PendingAction(val id: Long, val title: String, val detail: String)

/** Resolved only by native UI. Model text and web content cannot grant approval. */
object ActionApprovals {
    val pending = MutableStateFlow<PendingAction?>(null)
    private val ids = AtomicLong()
    private var answer: CompletableDeferred<Boolean>? = null
    suspend fun request(title: String, detail: String): Boolean {
        check(answer == null) { "Another phone action is waiting for your approval" }
        val value = PendingAction(ids.incrementAndGet(), title, detail)
        val deferred = CompletableDeferred<Boolean>(); answer = deferred; pending.value = value
        return try { withTimeoutOrNull(90000) { deferred.await() } == true }
        finally { if (pending.value?.id == value.id) { pending.value = null; answer = null } }
    }
    fun resolve(id: Long, allow: Boolean) { if (pending.value?.id == id) answer?.complete(allow) }
    fun cancel() { answer?.complete(false); answer = null; pending.value = null }
}
