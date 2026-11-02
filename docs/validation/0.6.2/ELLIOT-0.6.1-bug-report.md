# ELLIOT 0.6.1 exits on Start/Resume and restarts its service

Reported: 30 October 2026. Capture time: 19:13 IST.
Severity: High — starting the core companion function terminates the app.

## Environment
- Xiaomi model 24066PC95I (breeze), arm64-v8a.
- Android 16, API 36; custom ROM MistOS 4.8-Opaline.
- Package: com.eiliot.app.preview.
- App version: 0.6.1, versionCode 9; target SDK 36, minimum SDK 26.
- All ADB commands explicitly targeted the Xiaomi. The connected Vivo was not accessed.

## Reproduction
1. Install ELLIOT 0.6.1 and complete setup successfully.
2. Open the app and tap Start or Resume.
3. The UI changes, then the app closes within approximately 1–3 seconds (user observation).
4. A floating orb appears; attempting to use it can lead to repeated closures and automatic restarts (user observation).

Expected: The companion starts, displays its orb, and remains available.
Actual: The application process exits during wake-word model initialization. Android restarts the core service.

## Captured evidence
At 19:13:37.765, PID 7304 initializes Sherpa-ONNX KeywordSpotter through newFromFile. Its configuration points to these bundled model files under files/models/v061/kws/:
- encoder.onnx
- decoder.onnx
- joiner.onnx

At 19:13:38.797, the native runtime logs:
```
online-zipformer-transducer-model.cc:InitEncoder:118
'attention_dims' does not exist in the metadata
```
At 19:13:38.862, Zygote records:
```
Process 7304 exited cleanly (255)
```
ApplicationExitInfo independently records EXIT_SELF, status 255, at 19:13:38.868. This is a process exit; no Java exception stack is needed for this observed failure.

At 19:13:38.865, ActivityManager schedules SentinelCoreService to restart in 1000 ms. At 19:13:39.895, it starts replacement process 24547 for that service. This explains the observed automatic restart. The capture confirms one failure and one automatic restart, rather than proving an indefinite loop.

## Assessment
The immediate failure is in native wake-word encoder initialization. The metadata error strongly suggests that the bundled encoder model and the Sherpa-ONNX loader/model configuration are incompatible. The exact packaging or runtime defect requires inspection of the APK/source and ONNX metadata; this capture alone does not establish which component must change.

An older system audio-service crash exists in the device's historical buffer, but it occurred hours earlier in a different process and is not evidence of the ELLIOT failure captured here.

## Requested fix and validation
- Inspect the bundled KWS encoder metadata and verify the complete encoder/decoder/joiner/tokens set against the runtime version and model architecture.
- Package a matching runtime and model set, with a real-device initialization check.
- Prevent a native model-load failure from repeatedly restarting the companion; contain initialization failures and present a useful recovery message. A process-terminating native path cannot be handled by a Kotlin try/catch alone.
- Retest Start/Resume, orb activation, repeated starts, and recovery on this Xiaomi/ROM and a standard Android device.

Attachments: ELLIOT-0.6.1-crash-evidence.txt and ELLIOT-0.6.1-process-exits.txt. Full device logs are retained locally in work/; the attached evidence is filtered to the relevant failure and restart events.
