package com.eiliot.app

import com.eiliot.app.voice.*
import com.eiliot.app.llm.ProviderPresets
import org.junit.Assert.*
import org.junit.Test

class SpeechPipelineTest {
    @Test fun tokenFragmentsProduceEachSpokenSentenceOnceBeforeEndOfStream() {
        val c=SentenceStreamChunker()
        assertTrue(c.append("Here is a useful plan for").isEmpty())
        assertTrue(c.append(" the afternoon.").isEmpty())
        assertEquals(listOf("Here is a useful plan for the afternoon."),c.append(" Now we can continue with another idea."))
        assertEquals(listOf("Now we can continue with another idea."),c.finish())
        assertTrue(c.finish().isEmpty())
    }
    @Test fun codeAndUrlsAreNotReadAsInstructionsAndFencesCanCrossTokens() {
        val c=SentenceStreamChunker()
        val parts=listOf("## A short explanation for the owner.\n","\u0060\u0060","\u0060kotlin\n","delete_everything();\n","\u0060\u0060\u0060\n","The [source](https://example.org) has a clear explanation. ")
        val spoken=(parts.flatMap(c::append)+c.finish()).joinToString(" ")
        assertFalse(spoken.contains("delete_everything"));assertFalse(spoken.contains("https://"))
        assertTrue(spoken.contains("source"));assertTrue(spoken.contains("explanation"))
    }
    @Test fun decimalsAndEmojiSurviveSegmentationAndLongClausesAreBounded() {
        val c=SentenceStreamChunker(minimum=10,maximum=60)
        val input="The measured value is 3.14 and the owner said 🙂. "+"Another useful word ".repeat(30)
        val chunks=c.append(input)+c.finish()
        assertTrue(chunks.first().contains("3.14"));assertTrue(chunks.first().contains("🙂"))
        assertTrue(chunks.drop(1).all {it.length<=80});assertEquals(input.trim(),chunks.joinToString(" "))
    }
    @Test fun shortFinalAnswerFlushesAndUnclosedCodeIsExcluded() {
        val c=SentenceStreamChunker()
        assertTrue(c.append("Yes.").isEmpty());assertEquals(listOf("Yes."),c.finish())
        assertTrue(c.append("\u0060\u0060\u0060secret\nnot spoken").isEmpty());assertTrue(c.finish().isEmpty())
    }
    @Test fun spokenLedgerExcludesQueuedAndPartiallyPlayedSentences() {
        val ledger=SpokenLedger();ledger.queued("First sentence.",16000);ledger.queued("Second sentence.",32000)
        assertEquals("",ledger.completed(15999));assertEquals("First sentence.",ledger.completed(16000))
        assertEquals("First sentence.",ledger.completed(31999));assertEquals("First sentence. Second sentence.",ledger.completed(32000))
    }
    @Test fun idleSpeechCannotDuplicateAPreviousAnswerAndACompletedWriterWinsTheRace() {
        val journal=SpeechCommitJournal();journal.begin(10);journal.committed(10)
        assertFalse(journal.claimInterruption(10))
        journal.begin(11);assertTrue(journal.claimInterruption(11));assertFalse(journal.claimInterruption(11))
        journal.committed(11);assertTrue(journal.isCommitted(11))
        journal.begin(12);assertTrue(journal.claimInterruption(12));journal.begin(13)
        assertFalse(journal.isCommitted(12));assertTrue(journal.claimInterruption(13))
    }
    @Test fun naturalBargeInNeedsSixConsecutiveEligibleFramesAndDoesNotRepeat() {
        val d=BargeInDetector()
        repeat(5) {assertFalse(d.accept(.9f,true))};assertTrue(d.accept(.9f,true))
        repeat(20) {assertFalse(d.accept(.99f,true))}
        assertFalse(d.accept(.1f,true));repeat(5) {assertFalse(d.accept(.8f,true))};assertTrue(d.accept(.8f,true))
    }
    @Test fun silenceInvalidValuesAndDisabledCallModeBreakConfirmation() {
        val d=BargeInDetector()
        repeat(5) {assertFalse(d.accept(.9f,true))};assertFalse(d.accept(Float.NaN,true))
        repeat(5) {assertFalse(d.accept(.9f,true))};assertFalse(d.accept(.95f,false))
        repeat(5) {assertFalse(d.accept(.9f,true))};assertFalse(d.accept(.65f,true))
    }
    @Test fun gateSeparatesWakeModeFromLiveSpeechAndOffInvalidatesBoth() {
        val g=VoiceTurnGate();g.begin(7,VoicePhase.SPEAKING,"answer")
        assertFalse(g.interruptForBargeIn(7));g.setLiveCallMode(true);assertTrue(g.interruptForBargeIn(7))
        assertFalse(g.interruptForBargeIn(6));g.begin(7,VoicePhase.LISTENING,"owner");assertFalse(g.interruptForBargeIn(7))
        g.cancel(8,false);assertFalse(g.isLiveCallMode.value);assertFalse(g.interruptForBargeIn(8))
    }
    @Test fun preRollRetainsOnlyTheLatestSamplesInOrder() {
        val r=AudioPreRoll(5);r.add(floatArrayOf(1f,2f,3f));assertArrayEquals(floatArrayOf(1f,2f,3f),r.snapshot(),0f)
        r.add(floatArrayOf(4f,5f,6f,7f));assertArrayEquals(floatArrayOf(3f,4f,5f,6f,7f),r.snapshot(),0f)
        r.clear();assertTrue(r.snapshot().isEmpty())
    }
    @Test fun providerSelectionUsesOnlyReturnedTextCandidates() {
        assertNull(ProviderPresets.choose(listOf("embedding-3","whisper-large","tts-model")))
        assertEquals("chat-mini",ProviderPresets.choose(listOf("embedding-3","large-instruct","chat-mini")))
        assertEquals("owner-custom",ProviderPresets.choose(listOf("owner-custom")))
    }
    @Test fun expandedWakePhrasesKeepTheRequestAndDoNotMatchSubstrings() {
        assertEquals("set volume to thirty percent",WakeWords.command("okay Elliot set volume to thirty percent"))
        assertEquals("open YouTube",WakeWords.command("wake up Elliot open YouTube"))
        assertFalse(WakeWords.contains("elliotson"))
        assertEquals("Tell me about Elliot Alderson",WakeWords.command("Tell me about Elliot Alderson"))
        assertEquals("Set volume to thirty percent",TranscriptNormalizer.normalize("SET VOLUME TO THIRTY PER CENT"))
        assertEquals("Open YouTube",TranscriptNormalizer.normalize("OPEN YOUTUBE",listOf("YouTube")))
    }
}
