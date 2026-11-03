# Real-speech evaluation

This harness scores supplied, user-approved reference/transcript pairs. It does
not record, upload, train or run an ASR model. Never present fixture scores as
device accuracy. Enrollment/calibration utterances must not be reused as held-out
acceptance trials.

Each UTF-8 JSONL row requires unique id, mode, reference and hypothesis. Modes:
indian_english, hindi_english, bengali_english. Optional expected_wake and
detected_wake must both be booleans. Record the exact source revision, model hash,
APK SHA-256, capture profile, device and environment alongside each trial set.
Use the user's actual intended text as reference. Agree native script versus
Roman transliteration beforehand; this scorer neither translates nor transliterates.

Example (illustrative only):

```json
{"id":"en-001","mode":"indian_english","reference":"Do not open YouTube","hypothesis":"Do open YouTube","expected_wake":true,"detected_wake":true}
```

Run from project root:

```powershell
python tools/evaluate_voice.py --input evaluation/voice/trials.jsonl --output build/voice-evaluation/results.json
python -m unittest discover -s tools -p test_evaluate_voice.py
```

Expand the starter prompts to at least 30 held-out utterances per mode before
model selection; larger independent sets are needed for release claims. Annotate
numbers, negations, names, timing and command success separately. False-wake rates
per hour require measured continuous negative-audio exposure, not phrase counts.

Development capture comparison (same models/thresholds):

```powershell
.\gradlew.bat :app:assemblePreview -PvoiceReferenceCapture=false -PvoiceDiagnostics=true
.\gradlew.bat :app:assemblePreview -PvoiceReferenceCapture=true -PvoiceDiagnostics=true
```

Archive and hash the first APK before building the second: the output path is
reused. Default capture remains VOICE_COMMUNICATION with effects. The reference
profile requests MIC and creates no explicit AEC/NS/AGC effects. MIC is not a
guarantee of unprocessed hardware audio. Runtime diagnostics default off.

## Reusing one recording for model comparisons (dev.6)

Settings > Check speech recognition is isolated from assistant actions. Optional
Whisper Small (375 MB) and Large-v3 Turbo (1036 MB) are diagnostic candidates;
normal wake/manual commands still use the bundled English recognizer.
`model-candidates.json` records their exact revisions, files and hashes. Download
the selected pack in the screen; Verify or repair detects same-size corruption.
No model file is accepted solely because it has the expected filename or size.

1. Choose Direct or Processed, a model and a language. Record one complete
   sentence after Speak now. The current capture limit is six seconds.
2. Play the last recording to check intelligibility and completeness.
3. Change language or model and tap Transcribe last recording. This reuses the
   same PCM without opening the microphone again. The original capture label
   stays attached to the result; changing the microphone only affects the next
   new recording.
4. Compare Auto and forced English/Hindi/Bengali on the same clip. Keep the raw
   results, including empty outputs, visible for inspection. The displayed time
   includes loading, verification and decoding, not just model inference.
5. Recording a new sentence clears previous results; closing or backgrounding
   the dialog clears its RAM-only audio/results. No audio is retained or uploaded.

The screen holds at most six result entries. These comparisons identify failures;
they do not replace the separate held-out set described above. Clear playback
does not prove a recognizer understands the words, and a generated English
smoke test does not establish Bengali-English or Hindi-English support.

Native compatibility checks, after installing the app and test APK:

```powershell
adb -s YOUR_DEVICE_SERIAL shell am instrument -w -r -e class com.eiliot.app.WhisperDeviceTest com.eiliot.app.preview.test/androidx.test.runner.AndroidJUnitRunner
```

This downloads missing candidate packs onto the phone and tests generated English
through each model. Run it before a live recording session because instrumentation
stops the assistant runtime. Record exact APK hashes and test metrics separately.
