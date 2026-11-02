package com.eiliot.app.voice

import android.content.Context
import com.eiliot.app.core.SentinelRuntime
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** APK-owned, hash-verified files. No network or permission is needed for acoustic setup. */
object ModelAssets {
    val ready = MutableStateFlow(false)
    val status = MutableStateFlow("Preparing ELLIOT's voice")
    private var setup: Deferred<File>? = null
    fun start(context: Context) {
        if (setup != null) return
        setup = SentinelRuntime.scope.async(Dispatchers.IO) {
            try {
                val folder = install(context.applicationContext)
                ready.value = true; status.value = "Offline voice and listening are ready"
                folder
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { status.value = "Voice setup needs another try. Open Settings to retry."; throw e }
        }
        // Observe failures so asset errors never crash unrelated text/manual use.
        SentinelRuntime.scope.launch { runCatching { setup?.await() } }
    }
    suspend fun await(context: Context): File { start(context); return checkNotNull(setup).await() }
    fun directory(context: Context) = File(context.filesDir, "models/v061")
    fun retry(context: Context) { if (setup?.isActive == true) return; File(context.filesDir,"models/.v061_extracted").delete(); setup = null; ready.value = false; start(context) }
    internal fun install(context: Context): File {
        val root = File(context.filesDir, "models").apply { mkdirs() }
        val final = directory(context)
        val manifestBytes = context.assets.open("models/manifest.json").use { it.readBytes() }
        val fingerprint = MessageDigest.getInstance("SHA-256").digest(manifestBytes).joinToString("") { "%02x".format(it) }
        val list = JSONObject(String(manifestBytes)).getJSONArray("files")
        val marker = File(root, ".v061_extracted")
        fun complete() = marker.isFile && marker.readText() == fingerprint && (0 until list.length()).all { i ->
            val row = list.getJSONObject(i); File(final,row.getString("path")).let { it.isFile && it.length() == row.getLong("bytes") }
        }
        if (complete()) return final
        val stage = File(root,"v061.installing"); stage.deleteRecursively(); check(stage.mkdirs())
        try {
            for (i in 0 until list.length()) {
                val row = list.getJSONObject(i); val relative = row.getString("path")
                val target = File(stage,relative)
                check(target.canonicalPath.startsWith(stage.canonicalPath + File.separator))
                target.parentFile?.mkdirs(); val hash = MessageDigest.getInstance("SHA-256")
                context.assets.open("models/$relative").use { input -> target.outputStream().use { output ->
                    val buffer=ByteArray(65536); var total=0L
                    while(true) { val n=input.read(buffer);if(n<0)break;total+=n;check(total<=row.getLong("bytes"));hash.update(buffer,0,n);output.write(buffer,0,n) }
                    check(total==row.getLong("bytes"))
                } }
                check(hash.digest().joinToString("") { "%02x".format(it) }==row.getString("sha256")) { "Bundled voice file is damaged" }
            }
            marker.delete(); if(final.exists())check(final.deleteRecursively());check(stage.renameTo(final))
            val temp=File(root,".v061_extracted.tmp");temp.writeText(fingerprint);check(temp.renameTo(marker))
            return final
        } catch(e:Exception) { stage.deleteRecursively();throw e }
    }
}
