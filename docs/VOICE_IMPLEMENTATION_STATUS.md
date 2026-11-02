# Voice implementation status — 2 November 2026

Current development milestone: 0.6.3-dev.2 / versionCode 12.
Baseline source commit: 0d508c5fbc747010b16c0d51d8151aa3ef48748e.

## Guided wake calibration (dev.2)

Settings > Calibrate my wake phrase compares communication capture with effects
against MIC without explicitly created effects. Each path uses four wake attempts
(two of each phrase) and two ordinary phrases. It evaluates score/threshold pairs
2.0/0.15, 2.5/0.10 and 3.0/0.08. At least three positive hits, coverage of both
phrases and zero negative hits are required to nominate a setting. Equal recall
prefers the more restrictive threshold and score; an exact tie preserves the
communication path.

A nominated setting must then pass two fresh wake attempts and reject two fresh
ordinary phrases before Save is enabled. This small diagnostic set does not
establish a real-world false-wake rate. Test speaker playback and ordinary
conversation after calibration, especially if MIC is selected.

- Quiet/clipped samples are rejected; clips are bounded to four seconds.
- Recordings remain in RAM; there is no file export, transcript log or upload.
- Only capture choice, score, threshold and a device/model fingerprint persist.
- The listener passes settings explicitly to the separate speech worker, avoiding
  stale cross-process preferences after a change.
- Existing settings survive an unsuccessful attempt. Settings offers Reset.
- Closing, cancellation or backgrounding exits setup; normal listening can resume
  when the app is active. Normal wake capture is suspended during calibration.
- An optional six-second transcription check displays raw English STT output
  without executing it as a command. It does not save the audio or transcript.

Implementation: `voice/WakeCalibration.kt`, `ui/VoiceCalibrationDialog.kt`,
`ui/SettingsScreen.kt`, `core/WakeListener.kt` and the remote client/service,
under `app/src/main/java/com/eiliot/app/`.

Validation: 10 focused unit checks passed (4 new calibration policy/quality,
4 listening policy, 2 JNI callback). All five native tests passed on Vivo I2018,
Android 13, including replay, raw transcription and profile save/load/reset.
These generated-audio tests do not certify the user's accent or microphone quality.
The installed version and the first calibration screen were checked on the Vivo.
User speech calibration and subsequent held-out wake/STT trials are still needed.

## Earlier changes retained (dev.1)

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

These milestones do not introduce a multilingual model, speaker enrollment,
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

Earlier dev.1 validation: 160 unit/Robolectric tests passed with Java 21 (zero failed/skipped),
four native device tests passed on the Vivo, and four Python scorer tests passed.
User reported full-request capture after a natural-pause trial. This is not a
multilingual accuracy benchmark. Run the full suite with JAVA_HOME pointing to
Java 21; Android 36 Robolectric tests cannot run on Java 17.
