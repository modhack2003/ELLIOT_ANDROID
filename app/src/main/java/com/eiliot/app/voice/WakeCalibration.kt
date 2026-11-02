package com.eiliot.app.voice

import android.content.Context
import com.eiliot.app.voice.remote.*
import kotlinx.coroutines.*
import java.security.MessageDigest

data class WakeSetting(val referenceCapture:Boolean,val score:Float=2f,val threshold:Float=.15f) {
    init {require(score.isFinite() && score in 1f..3.5f);require(threshold.isFinite() && threshold in .05f.. .3f)}
}
data class WakeTrial(val phrase:String,val positive:Boolean,val audio:FloatArray)
data class WakeAssessment(val setting:WakeSetting,val hits:Int,val positives:Int,val falseHits:Int,val negatives:Int,val phrases:Set<String>) {
    val eligible get()=positives>=4 && negatives>=2 && hits>=3 && falseHits==0 && phrases.containsAll(listOf("hey","name"))
}
object WakeCalibrationPolicy {
    fun choose(results:List<WakeAssessment>)=results.filter {it.eligible}.sortedWith(
        compareByDescending<WakeAssessment> {it.hits}.thenByDescending {it.setting.threshold}.thenBy {it.setting.score}
    ).firstOrNull()
    fun validationPass(hits:List<Boolean>)=hits==listOf(true,true,false,false)
}
object VoiceCalibrationStore {
    private fun fingerprint(c:Context):String {
        val hash=MessageDigest.getInstance("SHA-256")
        hash.update(("wake-calibration-v1:"+android.os.Build.FINGERPRINT).toByteArray())
        for(name in listOf("voice-engine.json","models/manifest.json"))c.assets.open(name).use {input->
            val bytes=ByteArray(8192);while(true){val n=input.read(bytes);if(n<0)break;hash.update(bytes,0,n)}
        }
        return hash.digest().joinToString("") {"%02x".format(it)}
    }
    fun load(c:Context):WakeSetting?=runCatching {
        val p=c.getSharedPreferences("voice_calibration",0)
        if(p.getString("model",null)!=fingerprint(c))null else WakeSetting(p.getBoolean("reference",false),p.getFloat("score",2f),p.getFloat("threshold",.15f))
    }.getOrNull()
    fun save(c:Context,s:WakeSetting) {check(c.getSharedPreferences("voice_calibration",0).edit()
        .putString("model",fingerprint(c)).putBoolean("reference",s.referenceCapture)
        .putFloat("score",s.score).putFloat("threshold",s.threshold).commit())}
    fun clear(c:Context) {check(c.getSharedPreferences("voice_calibration",0).edit().clear().commit())}
}
object WakeCalibration {
    /** Bounded RAM-only capture. No files, network or automatic commands. */
    suspend fun capture(c:Context,reference:Boolean,seconds:Int=4):FloatArray=withContext(Dispatchers.IO) {
        require(seconds in 1..8)
        val samples=ArrayList<Float>(16000*seconds)
        VoiceAudioInput(c,reference) {}.use {input->
            input.onFrame={frame->samples.addAll(frame.asList())}
            val bytes=ByteArray(1024)
            while(samples.size<16000*seconds) {ensureActive();check(input.read(bytes,0,bytes.size)>0){"Microphone stopped. Try again."}}
        }
        samples.take(16000*seconds).toFloatArray()
    }
    fun usable(audio:FloatArray):Boolean {
        if(audio.isEmpty())return false
        val rms=kotlin.math.sqrt(audio.sumOf {it.toDouble()*it}/audio.size)
        return rms>.001 && audio.count {kotlin.math.abs(it)>.99f}.toDouble()/audio.size<.03
    }
    suspend fun detect(setting:WakeSetting,audio:FloatArray):Boolean=withContext(Dispatchers.IO) {
        val kws=RemoteKeywordSpotter(setting.score,setting.threshold);val stream=kws.createStream();var found=false
        try {
            for(offset in 0 until audio.size+24000 step 512) {
                ensureActive()
                val frame=FloatArray(512) {i->audio.getOrElse(offset+i){0f}}
                stream.acceptWaveform(frame,16000)
                drainWakeResults({kws.isReady(stream)},{kws.decode(stream)},{kws.getResult(stream).keyword},{kws.reset(stream)}) {found=true}
            }
        } finally {runCatching{stream.release()};runCatching{kws.release()}}
        found
    }
    suspend fun assess(reference:Boolean,trials:List<WakeTrial>):List<WakeAssessment> {
        val results=mutableListOf<WakeAssessment>()
        for((score,threshold) in listOf(2f to .15f,2.5f to .1f,3f to .08f)) {
            val setting=WakeSetting(reference,score,threshold);var hits=0;var falseHits=0;val phrases=mutableSetOf<String>()
            for(t in trials) {val hit=detect(setting,t.audio);if(hit && t.positive){hits++;phrases+=t.phrase}else if(hit)falseHits++}
            results+=WakeAssessment(setting,hits,trials.count{it.positive},falseHits,trials.count{!it.positive},phrases)
        }
        return results
    }
    suspend fun transcribe(audio:FloatArray):String=withContext(Dispatchers.IO) {
        val asr=RemoteRecognizer();val stream=asr.createStream("ELLIOT\nBATTERY\nVOLUME")
        try {
            for(offset in 0 until audio.size+32000 step 512) {
                ensureActive();stream.acceptWaveform(FloatArray(512){i->audio.getOrElse(offset+i){0f}},16000)
                while(asr.isReady(stream))asr.decode(stream)
            }
            asr.getResult(stream).text
        }finally {runCatching{stream.release()};runCatching{asr.release()}}
    }
}
