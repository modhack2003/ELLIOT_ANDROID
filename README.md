# ELLIOT 0.6.2 — native speech crash repair

Version code **10**. Built from delivered 0.6.1 source (code 9). Minimum API 26 (Android 8.0), target/compile API 36 (Android 16). App label ELLIOT, compatibility package `com.eiliot.app.preview` and previous delivered signer retained. The original package `com.eiliot.app` remains available as an unsigned build requiring the owner's original key.

## What this fixes

The 0.6.1 shared speech configuration forced the first-generation Zipformer loader onto the second-generation wake model. The loader asked for `attention_dims` and exited with status 255. Native process exit bypassed Kotlin catch handlers and Android restarted the core service. The old host smoke test auto-detected KWS instead, so it did not exercise that Android configuration.

0.6.2 uses a shared `assets/voice-engine.json` contract: KWS is explicitly `zipformer2`; transcription is `zipformer`. Checks validate real model hashes, encoder metadata, companion files and keyword tokens before JNI. The same contract drives the pinned native host test, including an isolated negative test reproducing the old exit 255.

All Sherpa/ONNX inference now runs in the non-exported, same-UID `:speech` service process. The main app owns microphone capture, hardware echo effects, AudioTrack playback, live speech/wake turn ownership, conversation, tools and UI. Binder transports16kHz frames/results and bounded PCM callbacks; a native worker exit disconnects speech while the main interface stays available by design. Android execution/device acceptance remains explicitly separate from host checks.

A version/model/config scoped fence records interrupted worker startup and observed worker death. Successful handshake clears the pending marker so normal main-process lifecycle does not permanently block a healthy engine. Observed worker failure blocks automatic native retry until **Repair offline voice** in Settings. Recovery unbinds the old worker, clears the fence, repairs files from APK assets, and prepares speech again. Old worker handles and callbacks cannot act on a replacement session. Off unbinds the worker; its destruction terminates any outstanding native work. No general in-app approval step is introduced.

## Install/update

Use **ELLIOT_v0.6.2.apk** for the reported ARM64 Xiaomi 24066PC95I. It is a complete installable APK, not an archive fragment. Install over the previous signed 0.6.1 in the same profile. **Do not uninstall or clear storage** to update; notes/settings/history retain their existing identity.

A universal APK is included for other supported processor architectures. APKs use lossless resource/JNI compression and Android's normal native-library extraction. Real model bytes are extracted/checked before use; acoustic weights and model licenses have not changed. ABI-specific and universal builds come from the same code.

After installation: open ELLIOT, tap Resume, verify it stays open, then try Hey Elliot and a simple volume/timer command. Test wake interruption and Live Call separately. If speech fails, keep the app open and use Settings → Repair offline voice. Text, phone/web tools, memory and global Off remain available subject to their existing settings/grants.

## Retained capabilities and UI

All 16 tools, direct matching-request authorization, optional action review, male selected voice, scheduled spoken check-ins and notice policies, memory/write controls, Nano/cloud/offline routing, background reference learning, bounded floating orb/captions, large text, three-tab shell and long-message reader remain. The UI improvement in this repair release is useful speech recovery with an app interface that is independent of native inference. No model/voice feature is removed to avoid the crash.

## Reproduce checks

Requires a full JDK21 (a JRE is insufficient), Gradle8.11.1, Android SDK36/build tools35.0.0 and network for fresh dependencies. Set local SDK path in your own `local.properties`.

```bash
./gradlew --no-watch-fs --no-build-cache --max-workers=1 :app:testReleaseUnitTest :app:lintRelease :app:lintPreview
./gradlew --no-watch-fs --no-build-cache --max-workers=1 clean :app:assemblePreview :app:assembleRelease :app:assemblePreviewAndroidTest
```

`preview` is the internal compatibility build type, with no Preview UI branding. Source excludes the delivered signing key. A newly generated local debug key cannot update the delivered APK. Release/original-package artifacts remain unsigned until externally signed with the proper owner key.

For actual Android JNI/Binder checks on a dedicated test device/profile:

```bash
./gradlew --no-watch-fs --no-build-cache --max-workers=1 :app:connectedPreviewAndroidTest
```

Instrumentation prepares actual engines, checks worker PID isolation, processes silence/synthesized PCM, kills the worker, and checks failure/retry behavior. It turns the companion off before each test and does not erase notes/history. These tests must run on a test device; compilation alone is not execution or microphone/AEC certification.

Optional Linux CPU checks:

```bash
python3 -m pip install sherpa-onnx==1.13.8 numpy
python3 tools/voice_smoke.py
# Expected to exit 255 and log missing attention_dims; run as a separate process:
python3 tools/voice_smoke.py --regression-wrong-loader
```

## Remaining limits

The supplied ADB report establishes the 0.6.1 fault; no device is connected to this build session. Actual Xiaomi/MistOS/Android16 startup, Binder death/recovery, microphone, loudspeaker echo, latency, lock/background survival,1–2m recognition and physical accessibility acceptance remain handset gates until run. JNI tests are supplied/compiled; their execution status is recorded in VALIDATION.md. The fix is not a claim that every acoustic or OEM issue is resolved.

English recognition quality still needs evaluation; a passing smoke command is not an accuracy study. Six 32 ms VAD frames still impose 192 ms confirmation, so the earlier 150 ms onset-to-stop target is not certified. Ryan's model-card dataset terms and the pinned eSpeak-containing runtime's GPL obligations remain unresolved for commercial/proprietary/store distribution; see THIRD_PARTY_MODELS.md. This release changes no third-party licensing rights.
