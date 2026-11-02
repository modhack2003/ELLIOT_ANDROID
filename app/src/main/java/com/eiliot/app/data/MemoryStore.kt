package com.eiliot.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.eiliot.app.llm.ChatMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class MemoryMessage(val id: Long, val role: String, val text: String, val time: Long,
    val source: String = "chat", val status: String = "complete")
data class MemoryNote(val id: Long, val text: String, val url: String, val time: Long, val kind: String)

/** Bodies use AES-GCM. IDs, roles, timestamps and source URLs are ordinary SQLite metadata. */
class MemoryStore(context: Context) : SQLiteOpenHelper(context, "eiliot_memory_v2.db", null, 1) {
    private val settings=context.applicationContext.getSharedPreferences("eiliot_v2_settings",Context.MODE_PRIVATE)
    private fun savingEnabled()=settings.getBoolean("memory_enabled",true)
    private val _messages = MutableStateFlow<List<MemoryMessage>>(emptyList())
    val messages: StateFlow<List<MemoryMessage>> = _messages
    private val _notes = MutableStateFlow<List<MemoryNote>>(emptyList())
    val notes: StateFlow<List<MemoryNote>> = _notes
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE messages(id INTEGER PRIMARY KEY AUTOINCREMENT, role TEXT NOT NULL, body TEXT NOT NULL, created INTEGER NOT NULL, source TEXT NOT NULL, status TEXT NOT NULL)")
        db.execSQL("CREATE TABLE notes(id INTEGER PRIMARY KEY AUTOINCREMENT, body TEXT NOT NULL, url TEXT NOT NULL DEFAULT '', created INTEGER NOT NULL, kind TEXT NOT NULL, fingerprint TEXT UNIQUE)")
    }
    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) = Unit
    @Synchronized fun addMessage(role: String, text: String, source: String = "chat", status: String = "complete"): Long {
        if(!savingEnabled())return -1L
        val v = ContentValues().apply { put("role", role); put("body", CryptoVault.seal(text)); put("created", System.currentTimeMillis()); put("source", source); put("status", status) }
        val id = writableDatabase.insertOrThrow("messages", null, v); refresh(); return id
    }
    @Synchronized fun addNote(text: String, url: String = "", kind: String = "note"): Boolean {
        if(!savingEnabled())return false
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest((kind + url + text).toByteArray()).joinToString("") { "%02x".format(it) }
        val v = ContentValues().apply { put("body", CryptoVault.seal(text.take(16000))); put("url", url); put("created", System.currentTimeMillis()); put("kind", kind); put("fingerprint", hash) }
        val added = writableDatabase.insertWithOnConflict("notes", null, v, SQLiteDatabase.CONFLICT_IGNORE) != -1L
        refresh(); return added
    }
    @Synchronized fun deleteNote(id: Long) { writableDatabase.delete("notes", "id=?", arrayOf(id.toString())); refresh() }
    @Synchronized fun clearAll() { writableDatabase.execSQL("DELETE FROM messages"); writableDatabase.execSQL("DELETE FROM notes"); writableDatabase.execSQL("VACUUM"); refresh() }
    @Synchronized fun refresh() {
        val msgs = mutableListOf<MemoryMessage>()
        readableDatabase.rawQuery("SELECT id,role,body,created,source,status FROM messages ORDER BY id DESC LIMIT 300", null).use { c ->
            while (c.moveToNext()) msgs += MemoryMessage(c.getLong(0), c.getString(1), decrypt(c.getString(2)), c.getLong(3), c.getString(4), c.getString(5))
        }
        val notes = mutableListOf<MemoryNote>()
        readableDatabase.rawQuery("SELECT id,body,url,created,kind FROM notes ORDER BY id DESC LIMIT 500", null).use { c ->
            while (c.moveToNext()) notes += MemoryNote(c.getLong(0), decrypt(c.getString(1)), c.getString(2), c.getLong(3), c.getString(4))
        }
        _messages.value = msgs.asReversed(); _notes.value = notes
    }
    private fun decrypt(value: String) = runCatching { CryptoVault.open(value) }.getOrDefault("[Encrypted record unavailable: recovery required]")
    fun context(): List<ChatMessage> = ContextBudget.select(messages.value.filter { it.status == "complete" && it.role in setOf("user", "assistant") }.takeLast(30).map { ChatMessage(it.role, it.text) }, 20000)
    fun recalledNotes(query: String): String {
        val words = query.lowercase().split(Regex("\\W+")).filter { it.length > 3 }.toSet()
        val selected = notes.value.filter { it.kind == "note" }.take(12) + notes.value.filter { it.kind == "knowledge" && words.any { w -> it.text.contains(w, true) } }.take(3)
        return selected.distinctBy { it.id }.joinToString("\n") { "[${it.kind}; ${it.url.ifBlank { "saved by owner" }}] ${it.text}" }.take(6000)
    }
}

object ContextBudget {
    fun select(history: List<ChatMessage>, maxChars: Int): List<ChatMessage> {
        var left = maxChars.coerceAtLeast(0); val chosen = mutableListOf<ChatMessage>()
        for (m in history.asReversed()) {
            if (left <= 0) break
            var start = (m.content.length - left.coerceAtMost(8000)).coerceAtLeast(0)
            if (start < m.content.length && start > 0 && m.content[start].isLowSurrogate()) start++
            val text = m.content.substring(start)
            chosen.add(ChatMessage(m.role, text)); left -= text.length
        }
        return chosen.asReversed()
    }
}
