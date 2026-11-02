# ELLIOT 0.6.0 — build and verification

Date: 30 October 2026, Asia/Kolkata. Native floating-companion milestone, ordered step 8. Version code 8; minimum API 26; compile/target API 36. Based on the saved 0.5.0 source and 0.6.0 plan; UI UX Designer mobile/accessibility guidance was applied to the actual Android components.

## Artifacts

| File | Package | Bytes | SHA-256 |
| --- | --- | ---: | --- |
| ELLIOT_Preview_v0.6.0.apk | `com.eiliot.app.preview` | 51,660,299 | `9f7a5ebf6dfbf3b0597f03ee6546c88f152a1bfca0307812420d04a191fac9c9` |
| ELLIOT_v0.6.0_unsigned_update.apk | `com.eiliot.app` | 51,647,983 | `4ce366f239359bfaf3e75683c778e13d2a21de73c388b81a242b5ab1fe184a4d` |

The Preview retains the delivered Preview package and certificate. Physical installation/data migration is not performed here. The original-package artifact is unsigned and still needs the original owner's signing key. Preview/original app data remain separate. No signing/API key is in the source archive.

## Completed software checks

- **120 automated test executions passed**, with no failures, errors or skips. All original 98 regression executions are retained; new overlay coverage is additive.
- Geometry checks include safe insets/margins, density/profile restoration, corrupt anchors, keyboard dodge, small-space input/compact fallback and 1,500 deterministic placement cases that enforce bounds, space budget and no orb overlap.
- Framework tests on API 28 and 36 cover one-time legacy migration, preserved settings, corrupt preferences, system-motion precedence, actual simulated overlay attach/removal, Off, delayed layout events, permission revocation and native layout commands without a model key or optional approval.
- Native component interaction/render checks use 360 × 800 portrait at 1×/2× text and 800 × 360 landscape at 1.5× text. They verify effective visible action targets of at least 48 px in the mdpi fixture, labels, draft/send/Off/close callbacks, bounded scrollable output and stopped reduced-motion rendering. Earlier Compose capability checks are retained.
- The native PNGs render actual orb/panel classes with sample data. They are **simulated component scenes, not physical-phone screenshots or proof of real OS IME behaviour**. Render review corrected blank single-line button text, a cramped input layout and a landscape test window that had retained portrait dimensions.
- Release lint: **0 errors, 71 warnings**. Preview lint: **0 errors, 71 warnings**. Warnings are retained, not hidden by a baseline. Reviewed categories include application-context window ownership (cleared on hide), older-Android system dimension lookup, programmatic custom-view construction, native widget touch forwarding, physical left/right docking, localization and KTX/style/deprecation work.
- Native panel flat-color contrast ratios against `#101A2A`: `{"text": 14.93, "secondary": 9.85, "accent": 9.8, "attention": 10.21, "off": 8.18}`. All checked text/accent colors exceed 4.5:1. This is a token calculation, not full WCAG certification, image contrast analysis or service-based accessibility acceptance.
- Package/version/label and non-debuggable checks passed; backup and cleartext are disabled.
- Preview signature verification passed with APK Signature Scheme v2. Certificate SHA-256: `bf6219c7a6e98705285d015cfb1015a16179e8ec1b356506e00b8960fe62430b`.
- Both APKs passed 16 KB zipalign; all six bundled arm64/x86_64 ELF libraries per APK have load segment alignment of at least 16 KB. This is structural evidence, not hardware execution.
- Final clean build/test/lint used JDK 21, Gradle 8.11.1, AGP 8.10.1, Kotlin 2.2.20, SDK 36/build tools 35.0.0; application bytecode targets Java 17.

| Suite | Executions | Failures/errors | Skipped |
| --- | ---: | ---: | ---: |
| CapabilityAndroidLegacyTest | 3 | 0 | 0 |
| CapabilityAndroidTest | 3 | 0 | 0 |
| CapabilityLargeTextUiTest | 1 | 0 | 0 |
| CapabilityPolicyTest | 22 | 0 | 0 |
| CapabilityUiTest | 1 | 0 | 0 |
| CompanionToolsTest | 21 | 0 | 0 |
| CorePolicyTest | 19 | 0 | 0 |
| FeedParserTest | 3 | 0 | 0 |
| HandsFreePolicyTest | 16 | 0 | 0 |
| LlmClientTest | 5 | 0 | 0 |
| OverlayAndroidLegacyTest | 4 | 0 | 0 |
| OverlayAndroidTest | 4 | 0 | 0 |
| OverlayGeometryTest | 11 | 0 | 0 |
| OverlayLandscapeUiTest | 1 | 0 | 0 |
| OverlayLargeTextUiTest | 1 | 0 | 0 |
| OverlayUiTest | 1 | 0 | 0 |
| PhoneToolsAndroidLegacyTest | 1 | 0 | 0 |
| PhoneToolsAndroidTest | 1 | 0 | 0 |
| VoiceAndroidLegacyTest | 1 | 0 | 0 |
| VoiceAndroidTest | 1 | 0 | 0 |

## Device gates still open

No handset/device emulator is attached. Verify actual overlay insets, floating keyboards, cutouts, navigation modes, rotation, TalkBack/Switch Access, installation and migration on named devices. No physical battery/thermal/frame-time comparison was performed. A simulated target/callback check does not certify a real assistive service.

Prior mandatory HF-2 gates remain: real Vosk wake accuracy, speaker echo during interruption, actual selected male TTS, spoken check-in/reply flow, 1–2 m operation and OEM screen-off service survival. No background Nano inference or new provider credentials were exercised. These are pending, not replaced by a manual Stop workaround.

## User-visible boundaries

- Captions/panels use available bounds and observed IME data. Floating/OEM keyboards may not provide useful insets. Manual move/reset and Open ELLIOT are explicit alternatives; universal keyboard avoidance is not claimed.
- Very small spaces use the main app. Input mode preserves the draft without a full-screen overlay; resizing never sends/repeats a task.
- Close/hide captions do not mute or cancel the companion. Stop response and Turn Off retain separate meanings.
- ELLIOT-opened settings hide floating windows until ELLIOT is reopened. This avoids guessing when an arbitrary external settings/permission flow finished; core/voice are not disabled by that visual handoff.
- All 16 tools, male voice selection, memory identities, direct matching-command authorization, optional review, check-ins, wake and global Off remain. Layout preferences contain no message, screen or notification content.

Read README.md for setup and the exact native layout phrases. Step 9 (richer chat/reader/search/branches) remains next; backup, durable tasks, integrations and the later roadmap are retained.
