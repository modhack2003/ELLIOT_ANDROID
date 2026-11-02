# ELLIOT 0.6.1 — Validation and delivery record

Prepared 30 October 2026, Asia/Kolkata. Built from the delivered 0.6.0 source. Version code 9 / version name 0.6.1. All results below apply to the final source and APKs in this handoff.

## Artifact identity

| File | Package | Bytes | SHA-256 |
| --- | --- | ---: | --- |
| ELLIOT_v0.6.1.apk | com.eiliot.app.preview | 258,908,993 | `797b012ee81c92bdf523c8b6957fe57162073ef4a41532b341f798fc33b51364` |
| ELLIOT_original_package_v0.6.1_unsigned.apk | com.eiliot.app | 258,896,693 | `8355c5745aeabd003f11e741cc8a3ddaa485b54e80317ee0027858f85c1c05c1` |

The signed APK keeps the previous delivered compatibility certificate, SHA-256 `bf6219c7a6e98705285d015cfb1015a16179e8ec1b356506e00b8960fe62430b`. APK Signature Scheme verification passed. App label ELLIOT; no Preview/Beta version-name branding; application debuggable=false. The .preview package suffix is deliberately retained for installation/data compatibility. Actual upgrade installation is a handset gate. The original-package APK remains unsigned and needs the original owner's key; it is not an installable signed update as delivered. No signing/API key is included in source.

## Automated software checks

- Final `:app:testReleaseUnitTest`: **144 executions, 0 failures, 0 errors, 0 skipped**. Original 120 baseline regressions retained; 24 new executions cover speech segmentation/ledger/VAD/preroll/ownership, real SSE ticket cancellation, saving guards, one-time setup, actual assets/repair and responsive shell/reader behavior.
- Actual Robolectric/Android framework checks include API28/API36 overlay/service/voice regressions. New Compose tests use API36 at 360×800 dp (1×, 1.5×, 2× text), 800×360 dp landscape and 840×900 dp wide (1.5×). Renders are under docs/ui/0.6.1. They are sample-state software renders, not a phone or microphone test.
- Long-message expansion confirms the parent chat list is the only vertical scroller. The real activity completes the offline setup route and recreation does not repeat the wizard.
- Actual bundled 88 MB files install with INTERNET permission denied; corruption by truncation is repaired from APK assets. First copy verifies every SHA/length; the warm fast path checks marker plus lengths, not full rehash of every weight.
- Release lint: **0 errors, 0 fatal, 76 warnings**.
- Preview lint: **0 errors, 0 fatal, 74 warnings**.
- Lint warnings are not suppressed by a baseline. Reviewed areas include application-context static ownership, localization/RTL and KTX/deprecation style, custom native view touch forwarding and system dimension lookup. Cursor queries use `use` despite analyzer recycling warnings; stopService uses an explicit Intent despite a SAM-instance diagnostic. Further cleanup remains useful.
- Both APK variants assembled successfully. `zipalign -c -P 16 -v 4` passed for both. Every packaged arm64-v8a/x86_64 ELF load segment passed 16 KB alignment/congruence, including the new ONNX/Sherpa binaries and retained dependencies.
- The packaged model files were individually read from each finished APK and checked against the bundled SHA-256 manifest. `tools/check_release.py` is included for reproduction.
- Gradle logs include an environmental Robolectric temp-font directory cleanup warning. It did not fail any test. Native stripping tools were unavailable for some prebuilt libraries, so Gradle packaged their existing binaries; this is not a signing or alignment failure.

## Real native model smoke — host only

Pinned `sherpa-onnx==1.13.8` loaded the actual delivered weights on Linux CPU. Ryan generated nonzero 22,050 Hz PCM; restricted KWS detected `hey_elliot`; Silero detected speech and accepted 40 silence frames without speech; Zipformer returned a real decoded result. Full evidence is in docs/validation/0.6.1/result.json and command-evaluation.json. The sample run duration is not an end-to-end phone latency measurement.

**Recognition limitation:** synthesized “Set volume to thirty percent” decoded as “T VOLUME TO THIRTY PER CENT”; “Start a timer for five minutes” decoded as “ER FOR FIVE MINUTES”; “Open YouTube” decoded as “'N YOU TOO”. A separate human reference was largely recognized, but this is not an accuracy study. Do not claim production-quality command recognition, punctuation restoration, an 80% false-wake reduction or a measured 1–2 m range. The app conservatively normalizes text rather than inventing omitted actions/app names.

The selected latest Sherpa 2.0.1 build removed the eSpeak frontend needed by this Piper model. A real synthesis check caught the incompatibility; final source pins compatible 1.13.8 and includes real phonemizer data. It also retains that runtime/voice's license restrictions.

## Blueprint acceptance gates

| Gate | State |
| --- | --- |
| Zero additional default acoustic model downloads | Bundling/extraction verified in software; actual clean phone setup/wake/audio still pending |
| One-time wizard | Actual activity/recreation test passed; Android grant prompts/45-second handset target pending |
| Standard wake and wake-free Live Call interruption | State/ownership/algorithm implemented and tested; actual Android audio/AEC/interrupt conversation pending |
| <900 ms / <1 second first audio | Unmeasured; depends on provider, generation, phone and chunk length |
| Six-frame >.65 detection | Tested; raw VAD compute returns a probability, six 32 ms frames = 192 ms confirmation |
| <150 ms from speech onset / <10 ms audible flush | Not certified; six-frame onset threshold itself exceeds 150 ms. Code pauses/flushes after detection; measure detection-to-stop separately |
| No stale cloud/voice completion | Scoped HTTP cancellation plus ticket/epoch/ledger tests passed; physical end-to-end demonstration pending |
| Premium responsive shell | Automated renders and large-text/reader checks passed; physical IME/TalkBack/OEM/foldable checks pending |
| All baseline capabilities retained | 16 tools/authorization/manual/Off and check-in regressions retained; earlier handset gates remain open |
| Commercial distribution readiness | Not cleared: Ryan CC BY-NC-SA model-card terms and eSpeak GPL linkage/source obligations need resolution |

## Required physical checks

Clean install and upgrade with existing data; actual JNI load and cold/warm RAM/time; microphone denial/revocation and foreground service arming; local commands and wake variants; natural interruption with speaker/headphones/Bluetooth; echo/TV false wakes and quiet/noisy 1 m/2 m use; playback-to-history correspondence; first-token-to-audio and onset/detection/stop timing; Off during every mode; overnight check-ins, DND, snooze, lock/screen-off and OEM survival; floating windows with real IME/rotation/cutouts; actual large text/TalkBack. No attached handset/emulator was available, so none is marked passed.

## Source handoff

The source ZIP includes Kotlin, resources/manifests, Gradle configuration/wrapper, tests, actual model assets, the pinned native AAR, model/runtime notices/provenance, portable host smoke/APK verification tools, implementation blueprint, new review/validation and preserved historical 0.6.0 docs. It excludes signing keys, API keys, local SDK paths, build caches and duplicated build outputs. Build prerequisites/dependencies still require an Android toolchain and network on a fresh build machine.
