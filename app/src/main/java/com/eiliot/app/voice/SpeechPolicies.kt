package com.eiliot.app.voice

/** Pure streaming segmentation: sentence boundaries, code exclusion, no duplicate emission. */
class SentenceStreamChunker(private val minimum: Int = 25, private val maximum: Int = 420) {
    private val pending=StringBuilder()
    private var inCode=false
    fun append(text:String):List<String> {
        pending.append(text)
        val out=mutableListOf<String>()
        while(true) {
            var boundary=-1;var i=0;var code=inCode
            while(i<pending.length) {
                if(i+2<pending.length && pending.substring(i,i+3)=="```") { code=!code;i+=3;continue }
                val ch=pending[i]
                val next=if(i+1<pending.length)pending[i+1] else null
                val decimal=ch=='.' && i>0 && pending[i-1].isDigit() && next?.isDigit()==true
                if(!code && i+1>=minimum && !decimal && ((ch in ".?!:" && next?.isWhitespace()==true)||ch=='\n')) { boundary=i+1;break }
                if(!code && i+1>=maximum && ch.isWhitespace()) { boundary=i+1;break }
                i++
            }
            if(boundary<0)break
            val raw=pending.substring(0,boundary)
            // Carry code-fence state when consuming an excluded block across token boundaries.
            inCode=raw.windowed(3).count { it=="```" }.let { if(it%2==1)!inCode else inCode }
            pending.delete(0,boundary)
            spoken(raw)?.let(out::add)
        }
        return out
    }
    fun finish():List<String> { val raw=pending.toString();pending.clear();val value=spoken(raw);inCode=false;return listOfNotNull(value) }
    private fun spoken(raw:String):String? {
        val clean=raw.replace(Regex("(?s)```.*?(?:```|$)")," ")
            .replace(Regex("\\[([^]]+)]\\([^)]*\\)"),"$1")
            .replace(Regex("https?://\\S+")," the linked source ")
            .replace(Regex("(?m)^\\s*(?:#{1,6}|[-*]|\\d+[.)])\\s+"),"")
            .replace(Regex("[*`_]+"),"").replace(Regex("\\s+")," ").trim()
        return clean.takeIf { it.isNotEmpty() }
    }
}

/** Only completed audio spans enter the spoken ledger, never merely synthesized/queued text. */
class SpokenLedger {
    private data class Span(val text:String,val end:Long)
    private val spans=mutableListOf<Span>()
    @Synchronized fun queued(text:String,endFrame:Long) { spans+=Span(text,endFrame) }
    @Synchronized fun completed(playbackFrame:Long):String=spans.filter { it.end<=playbackFrame }.joinToString(" ") { it.text }
}

/** Six independent 512-sample frames must exceed the configured probability threshold. */
class BargeInDetector(private val threshold:Float=.65f,private val frames:Int=6) {
    private var consecutive=0;private var latched=false
    fun accept(probability:Float,eligible:Boolean):Boolean {
        if(!eligible || !probability.isFinite() || probability<=threshold) { consecutive=0;latched=false;return false }
        consecutive++
        if(consecutive>=frames && !latched) { latched=true;return true }
        return false
    }
    fun reset() { consecutive=0;latched=false }
}

/** Bounded pre-roll avoids clipping the start of a wake-free interrupted command. */
class AudioPreRoll(private val capacity:Int=8000) {
    private val samples=FloatArray(capacity);private var count=0;private var cursor=0
    fun add(frame:FloatArray) { frame.forEach { samples[cursor]=it;cursor=(cursor+1)%capacity;count=minOf(capacity,count+1) } }
    fun snapshot()=FloatArray(count) { samples[(cursor-count+capacity+it)%capacity] }
    fun clear() { count=0;cursor=0 }
}

/** Prevent an idle/new utterance from re-committing the previous completed spoken answer. */
class SpeechCommitJournal {
    private val started=linkedSetOf<Long>()
    private val saved=linkedSetOf<Long>()
    @Synchronized fun begin(id:Long) { started+=id;while(started.size>64)started.remove(started.first()) }
    @Synchronized fun claimInterruption(id:Long)=id !in saved && started.remove(id)
    @Synchronized fun committed(id:Long) { started.remove(id);saved+=id;while(saved.size>64)saved.remove(saved.first()) }
    @Synchronized fun isCommitted(id:Long)=id in saved
}
