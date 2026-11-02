# ELLIOT 0.6.2 — Crash repair review

Prepared 30 October 2026, Asia/Kolkata. The uploaded ADB evidence is preserved as user input; no changes were made to it.

| Finding / requirement | Implementation |
| --- | --- |
| KWS uses wrong architecture loader | Shared contract explicitly selects zipformer2 for KWS and zipformer for ASR. Native model metadata agrees. |
| Android and test configuration differed | Host test constructs the pinned low-level KWS config with the same explicit contract; negative subprocess reproduces exit 255. |
| Bad models can terminate JNI | Full installed SHA verification, bounded ONNX metadata reader, model family/required fields and keyword token checks precede worker JNI. |
| Native exit kills main process | All SDK constructors/inference confined to non-exported :speech service. EiliotApp skips companion initialization in that process. Main owns mic/playback/state. |
| Worker crash and retries | Binder death updates speech readiness, stops old voice/wake session and persists a version/model/config block. Manual repair retries; core/UI are not deliberately stopped. |
| Stale IPC callbacks / IDs | Proxies capture worker generation. Old handles and audio callbacks reject replacement sessions. Death cleanup is generation guarded. |
| Off / outstanding native generation | Invalidate main voice ownership; unbind worker. Worker onDestroy terminates its own process, including uncancellable JNI work. Physical acceptance remains to run. |
| Same-size corrupted model cache | Preflight rejects SHA mismatch. Explicit repair invalidates extraction marker and recopies actual assets. |
| Easy delivery | ARM64 APK for reported phone plus universal APK. Compression preserves the weights and supports normal Android native extraction. |
| Existing features | 16 tools, direct authorization, check-ins, male voice, memory, reader/orb/UI and Off retained. |

The actual device tests must establish :speech service behavior, Android native initialization, audible interruption, failure recovery and upgrade/data retention. Host unit/native checks establish narrower facts and are reported separately. No claim of completed phone certification, solved recognition accuracy or cleared commercial licenses is made.
