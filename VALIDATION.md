# ELLIOT 0.6.2 — Crash fix and validation

Prepared 30 October 2026, Asia/Kolkata. Built from delivered 0.6.1 source. Version code 10/name 0.6.2. This release addresses the uploaded Xiaomi 24066PC95I / arm64 / MistOS Android 16 (API 36) crash report.

## Diagnosis and changes

0.6.1 forced `modelType=zipformer` for the KWS encoder whose actual metadata says `zipformer2`. The old loader required missing `attention_dims` and terminated native execution with exit 255. ASR's separate model really is zipformer and must keep that loader. The former host KWS test auto-detected its model, so it missed the Android override.

0.6.2 separates those settings in assets/voice-engine.json; validates all installed model hashes and required metadata before JNI; routes all Sherpa/ONNX work through a non-exported same-UID :speech service; skips main-runtime startup in that process; guards Binder requests/callbacks by worker generation; and records startup/worker failures for explicit repair. Kotlin catches no longer serve as the only defense against native exit. Off stops local capture/playback and unbinds the worker, whose destruction kills outstanding native work. Repair stops the old microphone session, clears the fence, invalidates the extraction marker and restores files from bundled assets. Main UI, memory and phone/web tools remain independent of native inference.

## Validation performed

- Release unit/framework suite: **154 executions, 0 failures/errors/skipped**. All 144 previous executions retained, plus 10 new model-contract/fence/metadata/IPC/stale-handle/corruption checks.
- Release lint: **0 errors, 0 fatal, 84 warnings**. Warnings are not hidden by a baseline.
- Preview lint: **0 errors, 0 fatal, 83 warnings**. Warnings are not hidden by a baseline.
- Native Linux regression subprocess with the old explicit loader reproduces exit 255 and the exact missing-attention_dims error. The corrected explicit loader test successfully initializes KWS/ASR/TTS/VAD, detects hey_elliot, produces real Ryan audio and decodes the smoke command. Test results are evidence of host model/config compatibility, not Android JNI, acoustic accuracy or phone latency.
- Robolectric emitted a temporary font-directory cleanup warning; it did not fail the tests. Existing Compose/overlay/readiness/permission/data/scope checks remain. No selected regression test was disabled to pass this build.
- A clean build with the Gradle build cache disabled completed after an incremental build-cache DEX packing failure. A later APK output-snapshot race was resolved by packaging with --no-watch-fs and one Gradle worker; assembly then completed successfully. This is separate from the phone's native model-loader fault.
- Both signed APKs retain certificate SHA256 bf6219c7a6e98705285d015cfb1015a16179e8ec1b356506e00b8960fe62430b. App label ELLIOT, compatibility package com.eiliot.app.preview, version code 10/name 0.6.2; nondebuggable. Actual installation/data migration is a handset gate.
- All delivered main APK model files were read and SHA-checked against their manifest. APK compression preserves model bytes. Every packaged 64-bit ELF load passed 16 KB alignment/congruence; zipalign -P 16 passed. JNI uses Android's normal compressed-library extraction. The model/runtime weights and third-party rights are unchanged.
- Native device tests compiled for the preview target. They initialize actual packaged engines in another PID and exercise PCM/worker death/manual recovery on Android. **They were not executed:** adb devices is empty and no KVM device is present. The compiled test APK is supplied separately for a dedicated test profile; test setup turns the companion off without erasing memory.

## Artifacts

| File | Bytes | SHA256 |
| --- | ---: | --- |
| ELLIOT_v0.6.2.apk | 93,268,860 | `0113806d2aa795f8faac79de9c8522bae13cd8e29d3ab43e1e82deaaa7e28a02` |
| ELLIOT_v0.6.2_universal.apk | 141,695,859 | `36af3dd7d0455cc814b3283135c700c8ce0094964d0875b54ffb282065cd86a1` |
| ELLIOT_original_package_v0.6.2_unsigned.apk | 141,683,543 | `dc3416928b7d61f9a0af8840cd187313055f63459f98d1e001c829de4a41ff1b` |

Use ELLIOT_v0.6.2.apk on the reported ARM64 Xiaomi. The universal build retains all four supported ABIs. Both contain the whole app and speech pack; no split-part joining or model download is required. Install over 0.6.1 in the same profile. Do not uninstall/clear storage. The original-package build is unsigned and still requires the original owner's key; the source excludes all signing/API keys and local toolchain paths.

## Remaining acceptance work

Run actual Xiaomi/MistOS36 Start/Resume, repeated starts, orb, wake variants, local commands, Live Call and standard interruption; global Off during native initialization/generation; intentional worker death and Repair; real microphone/speaker/headphones/Bluetooth echo; background/lock and OEM lifecycle; existing notes/settings/history after upgrade. The architecture is implemented, but real Android process/audio behavior is not claimed passed without execution.

Earlier recognition/noise/distance/latency, 192 ms six-frame versus 150 ms target, Nano support/background and third-party Ryan/eSpeak commercial redistribution limitations remain. This crash fix is not an accuracy or commercial-license clearance. Full source and prior documentation are retained; all 16 tools, direct requests, check-ins, male speech, memory/offline/cloud/Nano/UI/orb capabilities and global Off remain.
