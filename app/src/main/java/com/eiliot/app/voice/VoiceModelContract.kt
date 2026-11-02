package com.eiliot.app.voice

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest

/** Data shared with the host smoke tool; Android never guesses a loader from a filename. */
data class EngineSpec(val family:String,val folder:String,val modelingUnit:String,val vocabulary:String)
class VoiceModelContract(val source:String) {
    private val json=JSONObject(source)
    val threads=json.getInt("threads")
    fun spec(kind:String):EngineSpec { val v=json.getJSONObject(kind);return EngineSpec(v.getString("model_type"),v.getString("folder"),v.getString("modeling_unit"),v.getString("bpe_vocab")) }
    fun validate(root:File) {
        for(kind in listOf("kws","asr")) {
            val s=spec(kind);require(s.family in setOf("zipformer","zipformer2"))
            val m=OnnxMetadata.read(File(root,"${s.folder}/encoder.onnx"))
            require(m["model_type"]==s.family) { "$kind loader ${s.family} does not match ${m["model_type"]}" }
            val fields=if(s.family=="zipformer")listOf("attention_dims","encoder_dims","num_encoder_layers","left_context_len")else listOf("query_head_dims","value_head_dims","num_heads","encoder_dims","num_encoder_layers","left_context_len")
            require(fields.all { !m[it].isNullOrBlank() }) { "$kind encoder metadata is incomplete" }
            for(file in listOf("encoder.onnx","decoder.onnx","joiner.onnx","tokens.txt"))require(File(root,"${s.folder}/$file").length()>0)
            if(s.vocabulary.isNotBlank())require(File(root,s.vocabulary).length()>0)
        }
        val tokens=File(root,"kws/tokens.txt").readLines().map { it.substringBeforeLast(' ') }.toSet()
        File(root,"kws/keywords.txt").forEachLine { line ->
            require(line.substringBefore('@').trim().split(Regex("\\s+")).filter(String::isNotBlank).all {it in tokens}) { "Wake graph contains an unknown token" }
        }
    }
    companion object {
        fun load(c:Context)=VoiceModelContract(c.assets.open("voice-engine.json").bufferedReader().use {it.readText()})
        fun fingerprint(c:Context):String {
            val version=c.packageManager.getPackageInfo(c.packageName,0).versionName.orEmpty()
            val bytes=c.assets.open("voice-engine.json").use {it.readBytes()}+c.assets.open("models/manifest.json").use {it.readBytes()}+version.toByteArray()
            return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {"%02x".format(it)}
        }
        fun verify(c:Context,root:File) {
            val rows=JSONObject(c.assets.open("models/manifest.json").bufferedReader().use {it.readText()}).getJSONArray("files")
            for(i in 0 until rows.length()) {
                val r=rows.getJSONObject(i);val p=File(root,r.getString("path"));require(p.length()==r.getLong("bytes"))
                val h=MessageDigest.getInstance("SHA-256");p.inputStream().use {input->val b=ByteArray(65536);while(true) {val n=input.read(b);if(n<0)break;h.update(b,0,n)}}
                require(h.digest().joinToString("") {"%02x".format(it)}==r.getString("sha256")) {"Offline speech file needs repair"}
            }
            load(c).validate(root)
        }
    }
}

/** Bounded protobuf metadata reader. Skip the graph without loading weights or invoking JNI. */
object OnnxMetadata {
    fun read(file:File):Map<String,String> = RandomAccessFile(file,"r").use { f ->
        require(f.length() in 1..268435456)
        fun number():Long {var value=0L;var shift=0;while(shift<64) {val b=f.readUnsignedByte();value=value or ((b and 127).toLong() shl shift);if(b<128)return value;shift+=7};error("Invalid model metadata")}
        val result=mutableMapOf<String,String>()
        while(f.filePointer<f.length()) {
            val key=number();val wire=(key and 7).toInt()
            when(wire) {
                0->number()
                1->f.seek(f.filePointer+8)
                5->f.seek(f.filePointer+4)
                2->{
                    val n=number();val end=f.filePointer+n;require(n>=0 && end<=f.length())
                    if(key shr 3==14L) {
                        require(n<=65536 && result.size<128);val entry=mutableMapOf<Long,String>()
                        while(f.filePointer<end) {
                            val k=number();require(k and 7==2L);val len=number();require(len in 0..16384 && f.filePointer+len<=end)
                            val data=ByteArray(len.toInt());f.readFully(data);entry[k shr 3]=data.toString(Charsets.UTF_8)
                        }
                        result[checkNotNull(entry[1])]=checkNotNull(entry[2])
                    }else f.seek(end)
                }
                else->error("Invalid model wire format")
            }
            require(f.filePointer<=f.length())
        };result
    }
}
