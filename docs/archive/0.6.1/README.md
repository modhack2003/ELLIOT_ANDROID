# ELLIOT 0.6.1

Native Android companion built from the delivered 0.6.0 source, implementing the attached full-duplex voice and premium UX blueprint. Version code **9**; minimum Android API **26**; compile/target API **36**. This is a software-verified build for handset evaluation; physical audio and background-service acceptance is still required.

## What changed

- Actual local acoustic weights are inside the APK: restricted ELLIOT keyword spotting, streaming English Zipformer STT, Silero VAD and the Ryan male neural voice. Default voice setup requires no model download or voice audition.
- Standard mode accepts the wake phrase while replying. **Live Call** keeps the microphone open and uses six speech frames to interrupt without repeating the wake word. Hardware echo cancellation, noise suppression and automatic gain control are requested on the input session when supported.
- Cloud response sentences enter the local speech queue as tokens arrive. Interruption cancels the owning HTTP call and audio queue, and preserves only fully played sentence chunks in history.
- First-run onboarding connects a provider or chooses offline/Nano, requests optional Android grants, and starts ELLIOT. A persisted completion flag prevents repeat onboarding.
- Three destinations: **Companion**, **Vault**, **Settings**. Compact chat and wide two-pane layouts, one power control, Live Call beside the mic, inline long-message expansion, and a single parent chat scroll.
- Memory off blocks conversation/note/reference writes at the database boundary. Existing records stay readable until deliberately erased. Notification awareness and scheduled check-ins have independent switches.
- All 16 existing tools, direct-request authorization, scheduled spoken check-ins, bounded floating captions, orb positioning/appearance, manual alternatives and global Off are retained.

## Install

Install **ELLIOT_v0.6.1.apk** over the earlier delivered `.preview` package in the same Android profile. The visible app name is ELLIOT. Its internal package remains `com.eiliot.app.preview` and its delivered signer is retained to preserve update compatibility. Do not uninstall to solve a signer mismatch; uninstalling can lose local data.

The separate `ELLIOT_original_package_v0.6.1_unsigned.apk` uses `com.eiliot.app` and needs the owner's original signing key before installation. It cannot be signed with the evaluation key to update an existing owner-signed app. The two package identities have separate private storage.

## First use

1. Choose Groq, OpenRouter, DeepSeek, OpenAI, Custom HTTPS endpoint, foreground-only Nano, or offline commands. Verify & continue tests chat and structured tool support. Blank model selects an offered text model using a heuristic; it is not a quality benchmark.
2. Grant microphone/notification access for voice, overlay access for the floating companion, and optional Accessibility for eligible screen assistance. Android's system grants still require Android UI; ELLIOT does not add a second approval for a matching supported request by default.
3. Wait for real local extraction/voice initialization and start. Text-only continuation remains available if native voice cannot initialize. The wizard completion is saved once, even if optional grants were declined.
4. Say “Hey Elliot,” then “Set volume to thirty percent.” Start Live Call to speak naturally. Use the mic or text when recognition is imperfect. No wake, accuracy, echo or latency certification is implied by the build tests.
5. Settings contains voice, follow-up listening, check-in time frames, room mode, memory, background learning, orb appearance, screen tools and notification choices. Check-ins are optional owner settings and remain a core companion capability.
6. Global Off stops active inference, speech, capture, scheduled work and floating presentation. It cannot undo an action already dispatched to another app. Turn ELLIOT on from the visible app to arm the microphone again.

## Real limits and distribution terms

The packaged acoustic assets total about **88.3 MB**, rather than the blueprint's estimated 45 MB; native libraries add further APK size. Extraction and warm-up are asynchronous but are not guaranteed to finish in two seconds. The selected English ASR has observable command errors; there is no neural punctuation-restoration model. Recognition uses casing and conservative text normalization/hotword biasing, without guessing missing actions.

Six 32 ms VAD frames impose **192 ms of confirmation** before interruption can trigger. Therefore the blueprint's simultaneous less-than-150-ms-from-onset requirement cannot be met with that setting. Audio pause/flush is immediate in code after detection; actual audible stop timing, cloud latency, AEC, battery, distance and OEM survival require handset measurements.

**Important licensing:** Ryan's upstream model card identifies CC BY-NC-SA 4.0 dataset terms, and the compatible Sherpa 1.13.8 TTS binary includes GPL eSpeak phonemization. This handoff does not establish unrestricted commercial/proprietary redistribution rights. Retain model/runtime notices and source/build access, and resolve applicable license obligations before commercial/store distribution. See THIRD_PARTY_MODELS.md. The voice is the upstream Ryan male voice, not an imitation of the Mr. Robot actor.

## Build and check

Use JDK 21, Gradle 8.11.1, Android SDK 36, build tools 35.0.0, and a local `local.properties` containing your SDK path. Gradle dependencies need network access on a fresh machine; runtime default acoustic setup does not. The real models and pinned Sherpa AAR are included in this source handoff.

```bash
./gradlew :app:testReleaseUnitTest :app:lintRelease :app:lintPreview
./gradlew :app:assemblePreview :app:assembleRelease
```

`preview` is the internal compatibility build type. Its signing configuration uses the local Android debug keystore; **the delivered evaluation signing key is deliberately not in source**. A newly generated debug key will not update the delivered APK. Release remains unsigned until the owner supplies signing externally.

For an optional native CPU model smoke test (Linux host, not Android audio): install `sherpa-onnx==1.13.8` and `numpy`, then run `python3 tools/voice_smoke.py`. See VALIDATION.md for the exact delivered evidence, FEATURE_REVIEW.md for blueprint coverage, and NEXT_BUILD_PLAN.md for remaining gates. Historical 0.6.0 documentation is preserved under docs/archive/0.6.0.
