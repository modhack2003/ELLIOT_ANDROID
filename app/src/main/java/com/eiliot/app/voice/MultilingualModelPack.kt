package com.eiliot.app.voice

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Optional pinned Whisper pack; audio never enters the download path. */
object MultilingualModelPack {
    private val mutex=Mutex()
    data class Entry(val name:String,val bytes:Long,val sha256:String)
    data class Spec(val id:String,val label:String,val repository:String,val revision:String,val files:List<Entry>) {
        val bytes get()=files.sumOf {it.bytes}
        val encoder get()=files[0].name
        val decoder get()=files[1].name
        val tokens get()=files[2].name
    }
    val SMALL=Spec("whisper-small-int8","Whisper Small","csukuangfj/sherpa-onnx-whisper-small","8f3c18b358db4d1f2fc1eae49d75cd20989e4309",listOf(
        Entry("small-encoder.int8.onnx",112442483,"4cbe7b22fa9026b843b60a68640c747de05bafb1a11b57edc0e66c232d9f33a9"),
        Entry("small-decoder.int8.onnx",262226114,"acad50b5c782696e91b55914cc5ab4f756f1532f76e22aa6fc615f39fb69a8ee"),
        Entry("small-tokens.txt",816730,"b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126")
    ))
    val TURBO=Spec("whisper-turbo-int8","Whisper Large-v3 Turbo","csukuangfj/sherpa-onnx-whisper-turbo","2ca6ff69fc878651b770880507669577ac41c2ff",listOf(
        Entry("turbo-encoder.int8.onnx",674716297,"b02dcdf54f348741e93fe732b67d933c8dcb6735655f710640143081db38878b"),
        Entry("turbo-decoder.int8.onnx",361080764,"20accd02388482eb3a46bd615631adfdc85e1eb2c7db9ea3f02a40ffe6b81547"),
        Entry("turbo-tokens.txt",816730,"b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126")
    ))
    val TURBO_FULL_DECODER=Spec("whisper-turbo-full-decoder","Turbo · unquantized decoder",TURBO.repository,TURBO.revision,listOf(
        TURBO.files[0],
        Entry("turbo-decoder.onnx",636209532,"b24db5d90fa230c5eaa6b823d74862ced9e0d1dc3e01ec46601968e8db0e09ec"),
        TURBO.files[2]
    ))
    val models=listOf(SMALL,TURBO,TURBO_FULL_DECODER)
    fun spec(id:String)=models.singleOrNull {it.id==id}?:throw IllegalArgumentException("Unknown speech model")
    fun directory(c:Context,s:Spec=SMALL)=File(c.filesDir,"speech-packs/${s.id}-${s.revision}")
    fun available(c:Context,s:Spec=SMALL):Boolean=runCatching {
        val root=directory(c,s)
        File(root,".ready").readText()==s.revision && s.files.all {File(root,it.name).length()==it.bytes}
    }.getOrDefault(false)
    fun verify(c:Context,s:Spec=SMALL):File {
        val root=directory(c,s)
        check(available(c,s)){"Prepare multilingual speech in Settings first"}
        s.files.forEach {entry->check(verified(File(root,entry.name),entry)){"Multilingual speech file needs repair"}}
        return root
    }
    private fun verified(file:File,entry:Entry):Boolean {
        if(!file.isFile || file.length()!=entry.bytes)return false
        val hash=MessageDigest.getInstance("SHA-256")
        file.inputStream().use {input->val buffer=ByteArray(65536);while(true){val n=input.read(buffer);if(n<0)break;hash.update(buffer,0,n)}}
        return hash.digest().joinToString(""){"%02x".format(it)}==entry.sha256
    }
    suspend fun prepare(c:Context,s:Spec=SMALL,onProgress:(String)->Unit={}):File=withContext(Dispatchers.IO) {
        mutex.withLock {
            onProgress("Verifying ${s.label}…")
            if(available(c,s) && runCatching{verify(c,s)}.isSuccess)return@withLock directory(c,s)
            val root=directory(c,s);val stage=File(root.parentFile,"${s.id}-${s.revision}.installing")
            check(stage.isDirectory || stage.mkdirs())
            // Keep fully verified files on cancellation so a large download can be retried.
            val remaining=s.files.filterNot {verified(File(stage,it.name),it)}.sumOf {it.bytes}
            check(c.filesDir.usableSpace>=remaining+64L*1024*1024){"Not enough storage for ${s.label}"}
            try {
                for((index,entry) in s.files.withIndex()) {
                    ensureActive();onProgress("Preparing ${s.label} ${index+1} of ${s.files.size}")
                    val preload=c.getExternalFilesDir(null)?.let{File(it,"speech-packs/${s.id}/${entry.name}")}
                    val local=(listOfNotNull(preload)+models.map{File(directory(c,it),entry.name)}).firstOrNull {verified(it,entry)}
                    val target=File(stage,entry.name)
                    if(verified(target,entry))continue
                    if(local!=null)local.copyTo(target,overwrite=true)
                    else {
                        val url="https://huggingface.co/${s.repository}/resolve/${s.revision}/${entry.name}"
                        val connection=URL(url).openConnection() as HttpURLConnection
                        connection.connectTimeout=30000;connection.readTimeout=30000
                        try {
                            check(connection.responseCode==200){"Speech pack download failed"}
                            connection.inputStream.use {input->target.outputStream().use {output->
                                val buffer=ByteArray(65536);var total=0L;var reported=0L
                                while(true) {
                                    ensureActive();val n=input.read(buffer);if(n<0)break;total+=n;check(total<=entry.bytes);output.write(buffer,0,n)
                                    if(total-reported>=8*1024*1024) {reported=total;onProgress("${s.label}: file ${index+1}/${s.files.size}, ${total*100/entry.bytes}%")}
                                }
                                check(total==entry.bytes){"Incomplete speech pack download"}
                            }}
                        }finally{connection.disconnect()}
                    }
                    check(verified(target,entry)){"Speech pack checksum mismatch"}
                }
                ensureActive();File(stage,".ready").writeText(s.revision)
                // Never replace an installed directory until every staged file is verified.
                val previous=File(root.parentFile,"${s.id}-${s.revision}.previous")
                if(previous.exists()) {
                    if(!root.exists())check(previous.renameTo(root)) else check(previous.deleteRecursively())
                }
                if(root.exists())check(root.renameTo(previous))
                if(!stage.renameTo(root)) {
                    if(previous.exists())check(previous.renameTo(root))
                    error("Could not activate multilingual speech")
                }
                if(previous.exists())previous.deleteRecursively()
                root
            }catch(e:Exception) {throw e}
        }
    }
}
