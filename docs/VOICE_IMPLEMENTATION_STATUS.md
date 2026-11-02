# Voice implementation status — 2 November 2026

Development milestone: 0.6.3-dev.1 / versionCode 11.
Baseline source commit: 0d508c5fbc747010b16c0d51d8151aa3ef48748e.

Implemented in this milestone:

- Local version-controlled baseline; no remote upload.
- KWS result checked and reset after each decode, matching the pinned upstream loop.
- Default endpoint trailing silence increased from 0.65 to 1.4 seconds.
- Ordinary turn: 18 seconds to begin speech, then a maximum 60 seconds from the
  first VAD speech indication. Later speech frames cannot extend this maximum.
  Live Call retains its 75-second waiting budget, with the same utterance bound.
- Development-only microphone comparison flag; default capture remains unchanged.
- Development diagnostics opt-in, off by default; no recording/transcript logging added.
- Host smoke KWS sensitivity and decode-loop behavior aligned with Android.
- Real-speech scorer for three modes, independent Unicode-preserving scores,
  starter prompts and explicit held-out/consent requirements.

This milestone does not introduce a multilingual model, speaker enrollment,
personal vocabulary UI or acoustic training. Existing English model remains in
use. No claim of improved real-voice accuracy is established by unit/native tests.

Next gates:

1. Collect user-approved held-out speech trials for all three modes, with opt-in
   before saving recordings. Expand the starter prompt set before model selection.
2. Compare MIC reference capture against current communication capture with the
   same models and utterances; test echo/self-wakes as well as transcription.
3. Compare pause thresholds and real wake/pause/command behavior on the Vivo.
4. Select multilingual candidates by measured accuracy, latency and resources.
5. Implement second pass and personal vocabulary, then optional enrollment.

See evaluation/voice/README.md for evaluation and comparison-build instructions.
Baseline/progress and verification evidence are exported with the deliverables.

Validation: 160 unit/Robolectric tests passed with Java 21 (zero failed/skipped),
four native device tests passed on the Vivo, and four Python scorer tests passed.
User reported full-request capture after a natural-pause trial. This is not a
multilingual accuracy benchmark. Run the full suite with JAVA_HOME pointing to
Java 21; Android 36 Robolectric tests cannot run on Java 17.
