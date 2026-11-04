package com.eiliot.app.voice.remote

import com.eiliot.app.voice.WakeSetting
import com.eiliot.app.voice.drainWakeResults

/** Owns the same wake streams in live listening and calibration replay. */
class RemoteWakeDetector(setting:WakeSetting):AutoCloseable {
    private val spotter=RemoteKeywordSpotter(
        setting.score,setting.threshold,setting.alternateNames,setting.phonemeModel,setting.requireHey,
        setting.learnedPhones
    )
    // The BPE model's search is sensitive to where speech lands in an encoder
    // chunk. A second stream shares its weights and starts 160 ms out of phase.
    private val offsets=if(setting.requireHey && !setting.phonemeModel)intArrayOf(0,2560)else intArrayOf(0)
    private val streams=mutableListOf<RemoteStream>()

    init {
        try {reset()}catch(e:Exception){close();throw e}
    }

    fun accept(frame:FloatArray):String? {
        for(stream in streams) {
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
        releaseStreams()
        for(offset in offsets) {
            val stream=spotter.createStream()
            streams.add(stream)
            if(offset>0)stream.acceptWaveform(FloatArray(offset),16000)
        }
    }

    private fun releaseStreams() {
        streams.forEach {runCatching{it.release()}}
        streams.clear()
    }

    override fun close() {
        releaseStreams()
        runCatching{spotter.release()}
    }
}
