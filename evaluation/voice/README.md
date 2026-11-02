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
