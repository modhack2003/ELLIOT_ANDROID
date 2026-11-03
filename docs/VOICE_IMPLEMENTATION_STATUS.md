# Voice implementation status — 3 November 2026

Current working milestone: 0.6.3-dev.7 / versionCode 17.
Baseline source commit: 0d508c5fbc747010b16c0d51d8151aa3ef48748e.

## Consented local reference test (dev.7)

On November 3 the user authorized retaining one Bengali-English test recording
locally, with no cloud upload. The diagnostic screen now offers an explicit
Save one local test recording action. It writes a bounded 16 kHz mono PCM16 WAV
to app-scoped external `voice-evaluation/consented-sample.wav`. Existing files
are not overwritten. Recording and transcription do not automatically save.
The folder is excluded from Android cloud backup and device transfer. Once
saved, the file persists until deleted; closing the dialog still clears RAM.

Two WAV tests passed, including exact PCM16 round-trip and invalid/overlong input
rejection. The app built and installed on the Vivo; signing and bundled model
checks passed. One six-second WAV was explicitly saved and pulled locally for
reference inference. The user confirmed that this exact saved file is clear,
complete and at normal speed. It is not included in source archives or Git.

Reference runner: official whisper.cpp v1.9.2 Windows x64 CPU binary with a
SHA-256-verified multilingual Small F16 model. Initial Auto/Bengali runs also
failed to transcribe the clip; Auto detected Bengali. This does not isolate a
Sherpa bug: runtime, export and quantization differ. Additional decoding/model
comparisons and their exact results are recorded in the exported progress report.

Completed same-file comparison: phone Turbo int8 returned empty with Bengali;
reference Turbo F16 produced Bengali text with name/spelling errors. Reference
Auto identified Hindi. Neither passes the speech-quality gate. A saved-recording
device probe requires the explicit `consentedSpeechComparison=true` argument and
otherwise skips; it does not capture or save new microphone audio. Its pass means
execution only. Small ONNX also failed on Windows with both 1000 and 2400 tail
padding frames. Precision/export and language handling need controlled follow-up;
these results do not isolate a runtime-only defect.

## Multilingual diagnostics (dev.5–dev.6)

The normal assistant still uses the bundled English streaming Zipformer 20M model.
Settings > Check speech recognition can separately evaluate Whisper Small and,
in dev.6, Large-v3 Turbo int8 through Sherpa-onnx 1.13.8. This is not whisper.cpp,
voice training, or a deployed multilingual command pipeline. Wake detection still
uses the separate English GigaSpeech 3.3M KWS model and remains unresolved.

Optional model metadata, hashes and download logic are in
`app/src/main/java/com/eiliot/app/voice/MultilingualModelPack.kt`. Model packs live
in app-private `files/speech-packs/<id>-<revision>/`; they are not bundled in the
APK. Small is 375,485,327 bytes and Turbo is 1,036,613,791 bytes. Files download
from pinned public Hugging Face revisions and are verified before activation and
worker loading. No microphone audio enters that network path. Failed preparation
retains complete verified staged files for retry; partial files restart.

`SpeechEngineService.kt` and `NativeSpeechClient.kt` expose bounded, chunked
offline transcription. Clips above 28 seconds are rejected to avoid silent
truncation in the pinned backend. Each diagnostic attempt loads/releases its
recognizer; displayed time includes verification/loading/decoding. Cancelling
discards results, but does not interrupt an already running native decode.

Dev.6 adds Transcribe last recording so model/language comparisons use identical
PCM. Up to six labelled results stay in the screen's memory; recording again or
closing clears them. Direct/Processed labels describe the captured clip even if
the next-capture selection changes. Audio stays in RAM and commands never run.
Resetting wake calibration now explicitly sends the default score and threshold;
the worker no longer falls back to potentially stale cross-process preferences.

Observed dev.5 results:

- Generated English battery question: Whisper Small returned the expected text;
  one native smoke test passed (1.219-second audio, 3012 ms load and 2239 ms decode).
  This does not measure an accent or code switching.
- Live English prompt: “Hey idiot, what is my battery level?” in 5.4 seconds.
  Name recognition failed although the battery question was captured.
- Live Bengali-English prompt with Bengali forced: no words, 8.6 seconds. The
  user confirmed playback was clear and complete.
- User completed an Auto trial, but the result was gone on November 3. It is
  unscored, not a pass or failure. Hindi has not yet been tested.
- Later dev.3 native suite: 5/6 passed; generated standalone “Elliot” was missed,
  including after aligning the test decode loop. This supersedes any assumption
  that the earlier dev.2 five-test pass establishes reliable wake detection.

`app/src/androidTest/java/com/eiliot/app/WhisperDeviceTest.kt` measures native model
compatibility and generated-English timing. `MultilingualModelPackTest.kt` checks
corruption repair, cancelled staging/retry and preservation of another pack.
See the exported dev.6 progress/evidence for actual build and test outcomes.
Real held-out per-mode accuracy, final-pass command integration, vocabulary,
speaker enrollment and acoustic adaptation remain future gates.

Final dev.6 verification: 167 unit tests and 8/8 Vivo native tests passed; four
Python scorer tests passed. Both Whisper models returned the generated English
battery question. Small load/decode: 2860/1957 ms; Turbo: 4550/6642 ms, on separate
1.184/1.300-second generated clips. The synthetic wake test passed this run;
the earlier miss and real-voice failures remain unresolved evidence. APK signing,
373 bundled model files and native library alignment were checked successfully.

Real dev.6 same-recording trial (six-second Direct capture): Turbo Auto returned
“Iliet amar funer battery level qato” in 9.6 s; forced Bengali returned no words
in 11.3 s. Small Bengali and Auto returned the same incorrect Devanagari text
in 8.5–9.1 s. Neither candidate passed the prompted Bengali-English sentence.
The token table matches OpenAI's multilingual table, and Small metadata maps
bn to 50302. Root cause remains unproven; a reference-runtime comparison is the
next diagnostic gate. Audio remains RAM-only without explicit retention consent.

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
The first user speech calibration failed: no candidate passed. The subsequent
raw STT check showed ITONE for a trial prompted with "Hey Elliot, what is my
battery level?". Audio was not retained, so capture intelligibility and the exact
spoken contents were not independently verified. This is an unresolved real-voice
failure, not a successful accuracy result.

## Capture diagnostics (dev.3)

The dialog now displays Hey Elliot, Elliot and unwanted-wake counts for each
candidate. STT checks can use either processed or direct microphone capture.
Speak now appears after the recorder has started. The last sample remains only
in RAM for optional playback and is discarded on replacement or close. Playback
stays outside the assistant command pipeline and can be cancelled. This lets the
user check whether recorded speech is clear before changing recognition models.

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

These earlier dev.1–dev.3 milestones did not introduce a multilingual model, speaker enrollment,
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
