# ELLIOT 0.6.1 — Blueprint implementation review

Prepared 30 October 2026, Asia/Kolkata. Baseline: delivered 0.6.0, version code 8. Target: 0.6.1, version code 9. “Implemented” below means the code exists and has the described software evidence; it does not mean phone acceptance has passed.

| Blueprint requirement | Delivered behavior | Evidence / remaining boundary |
| --- | --- | --- |
| Bundled acoustic stack | Real int8 ASR/KWS/TTS, Silero, tokenizer and phonemizer data in APK assets; SHA manifest and repairable internal extraction | Actual asset installation/corruption repair tested with INTERNET permission denied. About 88.3 MB assets, not 45 MB. No placeholder weights. |
| Restricted ELLIOT wake | Native KWS token graph for Elliot / hey Elliot / okay Elliot / wake up Elliot; idle full dictation removed | Native host KWS hit; no custom phoneme training or explicit [unk] node, no measured >80% false-wake improvement or 1–2 m claim. |
| Streaming English STT | Sherpa Zipformer 20M int8, modified beam search, trained BPE and visible app/tool hotwords | Real host decoding works but synthetic commands show errors. No punctuation restoration or high-accuracy certification. Android manual recognizer retained as fallback, Vosk dependency retained for compatibility but no old Vosk capture path claimed. |
| Default male neural speech | Bundled Ryan medium int8 via compatible Sherpa 1.13.8, AudioTrack mono float PCM, system known-male fallback | Real host waveform at 22,050 Hz. Native Android JNI/audio, route behavior and male system fallback availability remain phone gates. Ryan/eSpeak distribution restrictions apply. |
| No download/audition setup | No default model download UI or candidate audition list in the main shell | Bundled voice selected automatically when ready. Internal legacy API retained for regressions, not exposed as required setup. |
| Fast asset extraction | Application starts asynchronous hash-checked extraction before voice preparation | Completion depends on storage/device; zero latency/two-second/45-second onboarding guarantees not established. |
| Input AEC / NS / AGC | Same AudioRecord session, VOICE_COMMUNICATION, 16 kHz mono PCM16, exact 512-sample frames | Source + framework checks; effects depend on OEM. AEC requested is not AEC effectiveness proved. |
| Standard wake interruption | Keyword spotting stays active during neural speech, turn-scoped cancellation | Existing gate/regression checks and new ownership tests. Physical speaker feedback still needs testing. |
| Live Call VAD interruption | Explicit Live Call control, six consecutive raw probabilities > .65, 500 ms pre-roll, renewed ASR turn | Pure frame/epoch tests. Mode switches reset detection state. Six frames = 192 ms before trigger, incompatible with <150 ms from voice onset. |
| Sentence streaming speech | Token deltas feed 25-character-minimum sentence/clause chunker, code/Markdown cleanup, single turn queue | Pure segmentation tests; no first-audio <900 ms guarantee. Local TTS rate and provider speed need phone measurement. |
| Cancel old cloud/audio | Per-turn HTTP call sets, generation/epoch checks, immediate pause/flush and producer cancellation | Real mock HTTP SSE cancellation test keeps next turn alive. Hardware audible flush <10 ms not measured. |
| Interrupted history | Audio playback-head ledger records fully completed chunks, commits heard sentences plus em dash | Ledger/journal tests, including no duplicate previous answer. Conservative sentence-level history omits an unfinished sentence; no word alignment claim. |
| One-time setup | Persisted first-run flag, provider presets / verification / optional grants / real readiness and greeting | Actual activity wizard completes offline and survives recreation in framework test. Denied/native-unavailable text route avoids startup trap. |
| Three-tab shell | Companion / Vault / Settings, no MoreScreen, no separate diagnostic tab | Actual Compose render tests; one power control, mic and Live Call, no download/audition buttons. |
| Responsive and large text | Compact single pane, >=600 dp companion split, ellipsized labels, IME-aware input and preserved reduced motion | API36 renders at compact 1x/1.5x/2x, landscape, and wide dimensions. Ellipsized labels retain full semantics; TalkBack/keyboard/real inset acceptance remains pending. |
| Long replies | Inline expand/collapse and gradient fade, parent LazyColumn owns vertical scroll | Actual long response test verifies one vertical scroll. Full reader copy/share/citation tools remain future roadmap work. |
| Unified saving | Memory toggle checked in MemoryStore; notes/chat/web writes blocked when off, stored rows retained | Database guard and learning-paused tests. Current RAM session can still display text. |
| Scheduled companionship | Existing active hours/interval/snooze/spoken questions and optional follow-up retained | Busy/voice/review/Live Call prevent overlapping check-ins. Notification awareness separated from scheduled check-ins. Device timer/service behavior not measured. |
| Tools and authorization | All 16 tool schemas/dispatch paths and direct request grants retained, optional review off by default | Prior scope/freshness/protected-content regressions retained. Android grants and platform security boundaries remain. |
| Floating companion | Existing safe bounds/captions/drafts/appearance preserved, human-readable error status, Settings links updated | Prior overlay framework tests retained. Physical IME, rotation and permission lifecycle need handset verification. |
| Version and identity | Code9/name0.6.1, visible ELLIOT label, compatibility .preview package/signer retained | APK manifest/signature verified in release evidence. Original-package unsigned handoff remains separate. |

## Architecture and ownership

`EiliotApp` initializes settings, starts bundled model installation, initializes voice and the typed capability registry. `SentinelRuntime` owns enabled state, epoch, foreground eligibility, service arming and global shutdown. `ModelAssets` owns internal versioned acoustic files. `WakeListener` owns one microphone capture and native KWS/ASR/VAD objects; idle uses KWS, active turns use full ASR.

`VoiceTurnGate` issues tickets and rejects stale callbacks. `EiliotBrain` owns a request serial, scoped tool grants, cloud/Nano/native routing, chat state, token chunking and history writes. `LlmClient` owns HTTP calls by request ticket. `NeuralSpeechPlayer` owns each native generation/audio track and tracks completed sentence frames. All interruption paths invalidate stale work before starting a replacement turn. SpeechCommitJournal prevents an idle or new turn from persisting the previous completed answer again.

`MemoryStore` retains existing encrypted bodies and database/package identities. The storage boundary rejects new writes while memory is off. LearningScheduler also checks saving eligibility. ProactiveEngine checks busy/listening/speaking/Live Call and independent check-in/notice settings. Off cancels work and clears visible overlays; cached native models can remain in process RAM without inference.

The main shell has exactly three destinations. Vault consolidates notes, history and web references. Settings consolidates capability recovery and configuration. The optional action drawer contains useful native phone/app/web shortcuts and guidance to use the orb over the target app for screen actions; ELLIOT does not attempt to read its own UI as the target screen.

## What this release does not claim

No phone/emulator is attached. Consumer-grade acoustics, fully duplex loudspeaker stability, recognition quality, 1–2 m wake range, noise/TV false activation rate, battery use, cold extraction time, clean-install duration, real hardware latency, lock-screen behavior and OEM background survival are unmeasured. Gemini Nano remains device-dependent and foreground-only, with a separate AICore model where needed. Cloud reasoning and web tools still require a network/provider; bundled voice does not provide a general offline LLM.

The app cannot gain unrestricted Android system control, unlock the device, read protected/password content, bypass another app's consent, silently grant Accessibility/overlay/microphone permissions, or verify a third-party action merely because an intent was dispatched. Existing guardrails and user Off/manual routes remain.

## Distribution blocker

Keep THIRD_PARTY_MODELS.md and its upstream Ryan model card. Ryan dataset noncommercial/share-alike terms and GPL eSpeak linkage require an appropriate distribution/source-compliance decision before commercial or store release. The source handoff includes ELLIOT's code, actual models, native AAR, notices and source links; it is not a declaration that all third-party commercial rights are cleared.
