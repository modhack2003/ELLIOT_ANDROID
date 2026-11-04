package com.eiliot.app.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.eiliot.app.voice.remote.*
import kotlinx.coroutines.*
import java.security.MessageDigest

data class WakeSetting(
    val referenceCapture:Boolean,
    val score:Float=2f,
    val threshold:Float=.15f,
    val alternateNames:Boolean=false,
    val noiseReduction:Boolean=false,
    val softwareNoiseReduction:Boolean=false,
    val phonemeModel:Boolean=false,
    val requireHey:Boolean=false,
    val learnedPronunciations:List<List<String>> = emptyList()
) {
    val label get()=when {
        learnedPronunciations.isNotEmpty() -> "Your pronunciation"
        phonemeModel && alternateNames -> "Phoneme accent"
        phonemeModel -> "Phoneme dictionary"
        alternateNames -> "Alternate names"
        else -> "Original"
    }
    init {
        require(score.isFinite() && score in 1f..3.5f)
        require(threshold.isFinite() && threshold in .05f.. .3f)
        require(learnedPronunciations.isEmpty() || (
            phonemeModel && requireHey && learnedPronunciations.size<=4 &&
                learnedPronunciations.all(WakePronunciations::canLearn)
        ))
    }
}
data class WakeTrial(val phrase:String,val positive:Boolean,val audio:FloatArray)
data class WakeAssessment(val setting:WakeSetting,val hits:Int,val positives:Int,val falseHits:Int,val negatives:Int,val phrases:Set<String>,val phraseHits:Map<String,Int> = emptyMap()) {
    val eligible get()=positives>=4 && negatives>=2 && hits>=3 && falseHits==0 && "hey" in phrases && (setting.requireHey || "name" in phrases)
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
        hash.update(("wake-calibration-v6:"+android.os.Build.FINGERPRINT).toByteArray())
        for(name in listOf("voice-engine.json","models/manifest.json"))c.assets.open(name).use {input->
            val bytes=ByteArray(8192);while(true){val n=input.read(bytes);if(n<0)break;hash.update(bytes,0,n)}
        }
        return hash.digest().joinToString("") {"%02x".format(it)}
    }
    fun load(c:Context):WakeSetting?=runCatching {
        val p=c.getSharedPreferences("voice_calibration",0)
        if(p.getString("model",null)!=fingerprint(c))null else WakeSetting(
            p.getBoolean("reference",false),p.getFloat("score",2f),p.getFloat("threshold",.15f),
            p.getBoolean("alternate_names",false),p.getBoolean("reduce_noise",false),
            p.getBoolean("software_noise",false),p.getBoolean("phoneme_model",false),
            p.getBoolean("require_hey",false),
            p.getString("learned_phones",null).orEmpty().split('|').filter(String::isNotBlank)
                .map {it.split(' ').filter(String::isNotBlank)}
        )
    }.getOrNull()
    fun save(c:Context,s:WakeSetting) {check(c.getSharedPreferences("voice_calibration",0).edit()
        .putString("model",fingerprint(c)).putBoolean("reference",s.referenceCapture)
        .putFloat("score",s.score).putFloat("threshold",s.threshold)
        .putBoolean("alternate_names",s.alternateNames).putBoolean("reduce_noise",s.noiseReduction)
        .putBoolean("software_noise",s.softwareNoiseReduction).putBoolean("phoneme_model",s.phonemeModel)
        .putBoolean("require_hey",s.requireHey).putString("learned_phones",s.learnedPronunciations.joinToString("|"){it.joinToString(" ")}).commit())}
    fun clear(c:Context) {check(c.getSharedPreferences("voice_calibration",0).edit().clear().commit())}
}
object WakeCalibration {
    /** Bounded RAM-only capture. No files, network or automatic commands. */
    suspend fun capture(c:Context,reference:Boolean,seconds:Int=4,onEffects:(String)->Unit={},onReady:()->Unit={}):FloatArray=withContext(Dispatchers.IO) {
        require(seconds in 1..8)
        val samples=ArrayList<Float>(16000*seconds)
        val denoiser=if(WakeCapturePreference.softwareNoiseReduction(c))RemoteDenoiser()else null
        try {VoiceAudioInput(c,reference,WakeCapturePreference.noiseReduction(c)) {}.use {input->
            input.onFrame={frame->samples.addAll((denoiser?.filterChunk(frame)?:frame).asList())}
            withContext(Dispatchers.Main){onEffects("Noise filter ${if(input.noiseEnabled)"on"else "off or unavailable"}; auto gain ${if(input.gainEnabled)"on"else "off"}; enhanced filter ${if(denoiser!=null)"on"else "off"}");onReady()}
            val bytes=ByteArray(1024)
            while(samples.size<16000*seconds) {ensureActive();check(input.read(bytes,0,bytes.size)>0){"Microphone stopped. Try again."}}
        }}finally{runCatching{denoiser?.release()}}
        samples.take(16000*seconds).toFloatArray()
    }
    fun usable(audio:FloatArray):Boolean {
        if(audio.isEmpty())return false
        val rms=kotlin.math.sqrt(audio.sumOf {it.toDouble()*it}/audio.size)
        return rms>.001 && audio.count {kotlin.math.abs(it)>.99f}.toDouble()/audio.size<.03
    }
    fun settings(reference:Boolean,noiseReduction:Boolean=false,softwareNoiseReduction:Boolean=false,requireHey:Boolean=false):List<WakeSetting> {
        val base=WakeSetting(reference,noiseReduction=noiseReduction,softwareNoiseReduction=softwareNoiseReduction,requireHey=requireHey)
        val original=listOf(false,true).flatMap {alternate->
            listOf(2f to .15f,2.5f to .1f,3f to .08f,3.5f to .05f).map {(score,threshold)->
                base.copy(score=score,threshold=threshold,alternateNames=alternate)
            }
        }
        val phoneme=base.copy(score=2.5f,threshold=.1f,phonemeModel=true)
        return original+listOf(phoneme,phoneme.copy(alternateNames=true))
    }
    suspend fun detect(setting:WakeSetting,audio:FloatArray):Boolean=detectHits(setting,audio).isNotEmpty()
    suspend fun observePhones(audio:FloatArray):List<String> =withContext(Dispatchers.IO) {
        require(audio.size in 1..128000)
        val recognizer=RemotePronunciationRecognizer()
        try {
            val stream=recognizer.createStream()
            try {
                for(offset in 0 until audio.size+24000 step 512) {
                    ensureActive()
                    stream.acceptWaveform(FloatArray(512){i->audio.getOrElse(offset+i){0f}},16000)
                    while(recognizer.isReady(stream))recognizer.decode(stream)
                }
                recognizer.tokens(stream)
            }finally{runCatching{stream.release()}}
        }finally{runCatching{recognizer.release()}}
    }
    fun learnedSetting(base:WakeSetting,phones:List<String>):WakeSetting? {
        if(!base.requireHey || !WakePronunciations.canLearn(phones))return null
        return base.copy(score=2.5f,threshold=.1f,phonemeModel=true,learnedPronunciations=listOf(phones.toList()))
    }
    suspend fun detectHits(setting:WakeSetting,audio:FloatArray):List<String> =withContext(Dispatchers.IO) {
        RemoteWakeDetector(setting).use {detector->replay(detector,audio,false)}
    }
    private suspend fun replay(detector:RemoteWakeDetector,audio:FloatArray,firstOnly:Boolean):List<String> {
        val found=mutableListOf<String>()
        for(offset in 0 until audio.size+24000 step 512) {
            currentCoroutineContext().ensureActive()
            val frame=FloatArray(512){i->audio.getOrElse(offset+i){0f}}
            val hit=detector.accept(frame)?:continue
            found.add(hit)
            if(firstOnly)break
        }
        return found
    }
    suspend fun assess(reference:Boolean,trials:List<WakeTrial>,noiseReduction:Boolean=false,softwareNoiseReduction:Boolean=false,requireHey:Boolean=false):List<WakeAssessment> =withContext(Dispatchers.IO) {
        val results=mutableListOf<WakeAssessment>()
        val candidates=settings(reference,noiseReduction,softwareNoiseReduction,requireHey).toMutableList()
        if(requireHey) {
            val learned=trials.filter {it.positive && it.phrase=="hey"}.take(4)
                .mapNotNull {learnedSetting(candidates.first(),observePhones(it.audio))}.distinct()
            candidates.addAll(learned)
            if(learned.size>1) {
                val pronunciations=learned.flatMap {it.learnedPronunciations}
                candidates.add(learned.first().copy(learnedPronunciations=pronunciations))
            }
        }
        for(setting in candidates) {
            var hits=0;var falseHits=0;val phraseHits=mutableMapOf<String,Int>()
            RemoteWakeDetector(setting).use {detector->
                for((index,trial) in trials.withIndex()) {
                    if(index>0)detector.reset()
                    val hit=replay(detector,trial.audio,true).isNotEmpty()
                    if(hit && trial.positive) {
                        hits++
                        phraseHits[trial.phrase]=(phraseHits[trial.phrase]?:0)+1
                    }else if(hit)falseHits++
                }
            }
            results+=WakeAssessment(setting,hits,trials.count{it.positive},falseHits,trials.count{!it.positive},phraseHits.keys.toSet(),phraseHits.toMap())
        }
        results
    }
    /** Play only the current in-memory check; never sends it through the command pipeline. */
    suspend fun play(audio:FloatArray)=withContext(Dispatchers.IO) {
        require(audio.isNotEmpty() && audio.size<=16000*8)
        val player=AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(16000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_FLOAT).build())
            .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(audio.size*4).build()
        try {
            check(player.state!=AudioTrack.STATE_UNINITIALIZED)
            ensureActive();check(player.write(audio,0,audio.size,AudioTrack.WRITE_BLOCKING)==audio.size)
            player.play()
            withTimeout(audio.size*1000L/16000+3000) {while(player.playbackHeadPosition<audio.size){ensureActive();delay(25)}}
        }finally {runCatching{player.stop()};player.release()}
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
