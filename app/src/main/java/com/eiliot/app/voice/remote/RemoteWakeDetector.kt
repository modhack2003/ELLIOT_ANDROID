package com.eiliot.app.voice.remote

import com.eiliot.app.voice.WakeSetting
import com.eiliot.app.voice.PhonemeBackup
import com.eiliot.app.voice.AcousticWakeSegmenter
import com.eiliot.app.voice.ACOUSTIC_WAKE
import com.eiliot.app.voice.drainWakeResults

/** Owns the same wake streams in live listening and calibration replay. */
class RemoteWakeDetector(setting:WakeSetting):AutoCloseable {
    private val acousticProfile=setting.acousticProfile
    private val segmenter=acousticProfile?.let {AcousticWakeSegmenter()}
    private var replayVad:RemoteVad?=null
    // The BPE model's search is sensitive to where speech lands in an encoder
    // chunk. A second stream shares its weights and starts 160 ms out of phase.
    private val offsets=if(setting.requireHey && !setting.phonemeModel)intArrayOf(0,2560)else intArrayOf(0)
    private val spotters=mutableListOf<Pair<RemoteKeywordSpotter,IntArray>>()
    private val streams=mutableListOf<Pair<RemoteKeywordSpotter,RemoteStream>>()

    init {
        if(acousticProfile==null)try {
            spotters+=RemoteKeywordSpotter(
                setting.score,setting.threshold,setting.alternateNames,setting.phonemeModel,setting.requireHey,
                if(setting.phonemeModel)setting.learnedPronunciations else emptyList()
            ) to offsets
            setting.phonemeBackup?.let {mode->
                spotters+=RemoteKeywordSpotter(
                    2.5f,.1f,mode==PhonemeBackup.ACCENT,phonemeModel=true,requireHey=true,
                    learnedPronunciations=setting.learnedPronunciations
                ) to intArrayOf(0)
            }
            reset()
        }catch(e:Exception){close();throw e}
    }

    fun accept(frame:FloatArray,speechProbability:Float?=null):String? {
        if(acousticProfile!=null) {
            val probability=speechProbability ?:
                (replayVad ?: RemoteVad().also {replayVad=it}).compute(frame)
            val features=checkNotNull(segmenter).accept(frame,probability) ?: return null
            return ACOUSTIC_WAKE.takeIf {acousticProfile.matches(features)}
        }
        for((spotter,stream) in streams) {
            var hit:String?=null
            stream.acceptWaveform(frame,16000)
            drainWakeResults(
                {spotter.isReady(stream)},{spotter.decode(stream)},
                {spotter.getResult(stream).keyword},{spotter.reset(stream)}
            ) {if(hit==null)hit=it}
            if(hit!=null) {
                reset()
                return hit
            }
        }
        return null
    }

    fun reset() {
        segmenter?.reset()
        replayVad?.reset()
        releaseStreams()
        for((spotter,offsets) in spotters) {
            for(offset in offsets) {
                val stream=spotter.createStream()
                streams.add(spotter to stream)
                if(offset>0)stream.acceptWaveform(FloatArray(offset),16000)
            }
        }
    }

    private fun releaseStreams() {
        streams.forEach {(_,stream)->runCatching{stream.release()}}
        streams.clear()
    }

    override fun close() {
        segmenter?.reset()
        runCatching {replayVad?.release()}
        replayVad=null
        releaseStreams()
        spotters.forEach {(spotter,_)->runCatching{spotter.release()}}
        spotters.clear()
    }
}
