# ELLIOT Android agent handoff

**Baseline** · `0.6.3-dev.24` (version code 34), source snapshot `815b4cd` on `main`.

**Current priorities** · Make the full “Hey Elliot” phrase work at normal tabletop distance, then improve command transcription for Indian English, Hindi-English, and Bengali-English, including Bengali-accented English.

Read the [build and usage guide](README.md) and [voice evaluation guide](evaluation/voice/README.md) before changing capture or recognition. The [older implementation status](docs/VOICE_IMPLEMENTATION_STATUS.md) describes the path to this baseline. Use the findings below for the current state.

## What has been verified

| Area | Result | Scope |
| --- | --- | --- |
| Wake calibration | The Vivo saved a strict full-phrase profile labeled **Two wake models with your pronunciation**. It pairs the bundled BPE wake detector with a phoneme detector and learned pronunciations. Fresh wake samples and negative controls are required before saving a profile. | The saved profile is specific to this setup. Earlier attempts with strict uncalibrated wake or other candidates missed the phrase. |
| Flat on the table | The full phrase was audible on playback but weak or noisy. The same recording missed before and after enhanced filtering and after 3× gain. | Normal seated distance with the phone flat remains an open issue. The user also reported that a manually triggered command went unheard in this position. |
| Upright on the table | A separate utterance from the same seat matched the saved profile before GTCRN and missed after it, including with 3× gain. An upright “Hey Ellie” control was rejected. | Comparing the same clip isolates the filter effect for that clip. The flat and upright clips were separate utterances. |
| Live wake and request | In the upright Vivo check, “Hey Ellie” stayed idle. “Hey Elliot” opened command listening and Turbo in Indian English transcribed “What is my battery level?” The app answered through the local device-information tool. Recognition took about 10 seconds in that run. | One live scenario. Repeatability, flat placement, other devices, and locked-screen behavior still need measurement. |
| Multilingual STT | Earlier Bengali-English trials had clear input audio but returned empty or incomplete text. Auto chose Hindi on one Bengali-English sample. The byte transport fix restored Bengali characters in replay, with word errors and truncation still observed. | Check these failures again against dev.24. Current evidence covers individual recordings and pipeline checks. |
| Regression checks | Thirty focused unit tests and the paired wake calibration device test passed for dev.24. A separate Turbo device test passed a generated English battery request. | Generated speech verifies the pipeline and local route. Real Hindi-English and Bengali-English accuracy still needs held-out testing. |

## Keep these behaviors while making changes

- The successful Vivo configuration used **Direct** microphone capture with Android noise suppression and enhanced noise reduction enabled. For the saved strict profile, **Use enhanced filter for wake phrase** was off (`wakeBeforeEnhanced=true`). Wake uses the input before software GTCRN, requests still use GTCRN, and Android noise suppression stays active in both paths. Existing profiles default to their original filter routing.
- Calibration personalizes the wake detector through local derived pronunciation or acoustic features. Command STT has separate settings. The built-in English streaming Zipformer is the default and still handles Live Call. Optional Whisper Small and Large-v3 Turbo int8 packs can handle short live requests via **Settings → Check speech recognition → Use for live requests**. Indian English, Hindi-English, Bengali-English, and Auto are language settings for the same model family.
- Keep the native Sherpa-onnx worker in the private `:speech` process and preserve the crash fence and manual repair flow. Keep the bounded request buffer and the guard against stale decode results. Preserve the Whisper byte-token transport and rejection of incomplete UTF-8 output when changing the decoder. See [speech crash recovery](SPEECH_FIX.md) and [model sources and terms](THIRD_PARTY_MODELS.md).
- Keep live and calibration audio in memory by default. Obtain explicit consent for any recording export. Keep private recordings, ADB dumps, credentials, signing keys, and APKs out of Git. Preserve strict local command matching when testing transcription corrections, especially for commands with side effects.

## Next work, in order

1. **Reproduce tabletop capture before tuning wake.** Use the same Vivo, seat, and room with the phone flat and upright. Record actual distance and whether the microphone edge is clear. Test quiet and typical fan/AC conditions. Replay each clip through the existing before/after-filter comparison and log capture level, speech onset, wake hit, and latency. Check the manual mic from the same seat to separate poor capture from a wake-only miss. Keep audio in memory.
2. **Measure wake recall and unwanted wakes together.** Repeat “Hey Elliot” with separate samples for calibration and validation. Include “Hey Ellie,” “Elliot” alone, ordinary conversation, and TV or assistant playback as controls. Measure wake recall by condition, unwanted wakes per hour, and median and 95th-percentile wake latency. Blanket gain and wider matching have already produced unwanted wakes in testing. Compare any new setting with the strict paired profile under the same conditions.
3. **Compare real speech across the three requested modes.** Use the same held-out utterances for Zipformer, Whisper Small, and Turbo where each supports the case. Include short commands, longer sentences with pauses, names, and natural Hindi-English and Bengali-English code-switching. Measure word or character error by language, command completion, and cold and warm latency. Score wake, transcription, and action routing separately. Review VAD clipping and the upstream short-input decoder token budget if multilingual output is truncated.
4. **Test device behavior before changing defaults.** Exercise locked screen, screen-off idle, foreground/background transitions, microphone contention, permission changes, and repeated wake/request cycles. Record idle power use and peak memory on the Vivo and another handset. Compare model load, audio endpointing, and decoder time separately before adding model preloading or changing the default recognizer.
5. **Evaluate further personalization after measuring the baseline.** The current guided setup adapts wake matching and leaves command STT unchanged. Evaluate whether an optional local enrollment or vocabulary bias improves held-out commands. Track any change in unwanted wakes, and make saved personalization removable.

## Source map

| Concern | Start here |
| --- | --- |
| Capture, denoising, and wake-to-command handoff | [VoiceAudioInput.kt](app/src/main/java/com/eiliot/app/voice/VoiceAudioInput.kt), [WakeListener.kt](app/src/main/java/com/eiliot/app/core/WakeListener.kt) |
| Wake profiles, replay diagnostics, and settings | [WakeCalibration.kt](app/src/main/java/com/eiliot/app/voice/WakeCalibration.kt), [RemoteWakeDetector.kt](app/src/main/java/com/eiliot/app/voice/remote/RemoteWakeDetector.kt), [VoiceCalibrationDialog.kt](app/src/main/java/com/eiliot/app/ui/VoiceCalibrationDialog.kt), [SettingsScreen.kt](app/src/main/java/com/eiliot/app/ui/SettingsScreen.kt) |
| Command capture, model selection, and decoding | [CommandUtterance.kt](app/src/main/java/com/eiliot/app/voice/CommandUtterance.kt), [CommandSpeech.kt](app/src/main/java/com/eiliot/app/voice/CommandSpeech.kt), [MultilingualModelPack.kt](app/src/main/java/com/eiliot/app/voice/MultilingualModelPack.kt), [WhisperTokenTransport.kt](app/src/main/java/com/eiliot/app/voice/WhisperTokenTransport.kt) |
| Native worker and local command matching | [SpeechEngineService.kt](app/src/main/java/com/eiliot/app/voice/remote/SpeechEngineService.kt), [NativeSpeechClient.kt](app/src/main/java/com/eiliot/app/voice/remote/NativeSpeechClient.kt), [LocalCommands.kt](app/src/main/java/com/eiliot/app/tools/LocalCommands.kt) |
| Device regressions | [NativeSpeechDeviceTest.kt](app/src/androidTest/java/com/eiliot/app/NativeSpeechDeviceTest.kt), [WhisperDeviceTest.kt](app/src/androidTest/java/com/eiliot/app/WhisperDeviceTest.kt) |

## Run checks

Use JDK 21, Android SDK 36, and Build Tools 35.0.0. The preview package is `com.eiliot.app.preview`. Run these from the repo root in PowerShell. Use `./gradlew` on Unix-like systems.

```powershell
.\gradlew.bat :app:testPreviewUnitTest
.\gradlew.bat :app:assemblePreview -PvoiceArm64Only=true
adb devices -l
.\gradlew.bat :app:connectedPreviewAndroidTest -PvoiceArm64Only=true
```

Use `-PvoiceDiagnostics=true` for a diagnostic preview. Device tests can stop the app's voice runtime. Reopen the app and confirm the listener has resumed before a live check. Update this handoff after verified changes, with the build, test conditions, result, and remaining limitation. When changing tracked source, refresh the affected [source manifest](SOURCE_MANIFEST.json) entries using the staged Git blobs.
