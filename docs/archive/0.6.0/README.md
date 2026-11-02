# ELLIOT 0.6.0 — floating companion preview

ELLIOT is a male native Android companion with text/voice conversation, local memory, a floating caption orb, everyday phone/web tools and optional scheduled spoken check-ins. This version implements ordered step 8 on top of the 0.5.0 capability registry and 0.4.0 hands-free foundation. Physical-phone acceptance remains unfinished.

The internal package IDs remain `com.eiliot.app` and `com.eiliot.app.preview` to preserve update/data compatibility. Version code 8; minimum API 26; target/compile API 36.

## New in 0.6.0

- Orb, caption and transcript positions use shared safe bounds, delivered insets, gesture/cutout margins and measured view sizes. Edge and normalized vertical placement are saved separately for orientation/size profiles. Keyboard dodge does not overwrite preferred placement.
- The transcript uses full, compact and input-focused layouts within a bounded space budget. Very small areas use the main app instead of shrinking controls or creating a full-screen overlay. Draft text is shared with Companion in RAM and retained through panel reconstruction.
- Header, Send, Talk, Stop and More actions have 48 dp targets. More retains screen preview/explanation, latest reply, repositioning and optional-review choices. Drag, tap, long press and cancellation are distinct; accessibility actions offer alternatives to dragging.
- Controls → **Orb & appearance** has a live visual preview, size/opacity, caption text/timeout, reduced motion, instant text, haptics and reset. Options are collapsed initially to keep the other Controls sections reachable.
- System motion is calm when idle. Expressive keeps idle motion; Reduced and Android animation-off stop it. Instant text/reduced motion also apply to chat typing. No extra model/provider service is required.
- Exact native phrases work without a cloud key: **“Move your orb left/right”**, **“Reset your orb position”**, **“Hide your captions”**, **“Show your captions”**. These change ELLIOT’s own presentation; hiding captions does not mute speech.
- Private captions/panels are removed on lock/screen-off. Permission revocation and global Off remove window state and stop presentation callbacks. ELLIOT-initiated settings handoffs hide floating windows until ELLIOT is reopened; core/voice settings remain unchanged.

### Try the update

1. Install the signed 0.6.0 Preview over the previous Preview in the same profile. Do not uninstall to work around a signer mismatch; Preview and original-package apps have separate data.
2. Resume ELLIOT and enable overlay access if wanted. Open Controls → Orb & appearance → Customize orb & captions.
3. Try Move left/right and Reset, then rotate the phone. Dragging remains optional; TalkBack actions and the orb’s More menu offer alternatives.
4. Open a long response in the floating transcript. Type a draft, open/close the keyboard and rotate. The draft must remain and no task should be resent.
5. Try 2× system text, Reduced motion and instant text. When the overlay has too little room, open ELLIOT for the full answer/input.
6. Recheck “hey Elliot” during TTS and scheduled spoken check-ins on the actual phone. Prior automated checks do not certify speaker echo, microphone recognition, screen-off survival or 1–2 m use.

Floating keyboards and some OEM overlay windows do not deliver reliable IME bounds. Manual move/reset and Open ELLIOT remain available. This candidate does not claim universal keyboard avoidance, physical TalkBack behaviour or battery measurements; see VALIDATION.md for exact evidence.

## Retained from 0.5.0

- Tools opens a searchable, grouped **Live capabilities** dashboard, with the existing manual tools retained under **Direct controls**.
- A single typed registry supplies tool prerequisites, UI status, model context and blocked-tool recovery details. Dispatch rechecks the current conditions; an earlier green indicator cannot authorize a later action.
- Readiness distinguishes Ready, Conditional, Off, Needs setup, Paused, Not checked and Unavailable. Internet validation is not proof that a provider works. An unvalidated network can still be attempted; the app does not block VPN/captive-portal cases just because a probe is missing.
- Provider/web observations are process-local, expire after five minutes and contain fixed status categories. Settings changes invalidate cloud health. No provider calls are made merely to populate the dashboard.
- Nano support has typed observations and remains foreground-only. A stored model choice is not reported as verified model availability.
- Companion has a compact presence card and Voice/Wake/Screen/Memory status chips; it shrinks further when the keyboard opens. The app and floating captions use shared session presentation. Navigation and direct-controls selection survive activity recreation.
- Ask **“What tools do you have?”**, **“Check voice readiness”**, **“Check screen readiness”** or **“Check model readiness”** for a read-only local answer without a cloud key. Broader wording still uses the configured reasoning path.
- Notification-access detection now requires the exact listener component, eliminating package-substring false positives.

### UI commitment for every future build

Every later milestone includes a useful UI/UX improvement, a clear loading/empty/error state where relevant, readable large text and preserved manual/Off controls. Record the changed screen and verification evidence in its release notes. Keep existing capabilities and data; avoid ornamental motion that obscures actual state. Screenshots from a renderer are not physical-phone acceptance.

## Retained hands-free foundation

- A shared voice-turn gate prevents an interrupted speech completion from closing a new listening turn.
- The downloaded Vosk English model now handles wake recognition and local requests through one continuous PCM input. Saying “Elliot” or “hey Elliot” during speech cancels the old reply and starts a local request without deliberately shutting off wake capture for the whole TTS answer.
- Scheduled spoken questions have adjustable active hours, interval, snooze and an optional reply window. They do not depend solely on an idle timer or a notification arriving.
- Direct-command mode is the default: matching supported phone actions use the native request's scope rather than opening another Allow page. Optional review mode retains the buttons and adds a spoken Allow/Decline question when speech is ready.
- Simple English volume, timer, app-opening, navigation and phone-status commands have a native parser, so those recognized forms do not need a cloud key.
- Room mode can allow eligible spoken check-ins/notices with the screen off or locked. Notification content speech and locked-content speech have separate controls. It does not unlock the phone or give access to protected app data.
- After eligible spoken replies/questions, a short listening window can accept a follow-up without another wake phrase. Timeout returns to wake listening.

**Not device-certified:** No phone is attached in this build environment. Speaker echo, wake/STT accuracy, actual TTS, screen-off service survival and 1–2 metre operation must be verified on the handset. A passing JVM/framework simulation is not an acoustic or OEM test. Read VALIDATION.md before treating this as a fully validated hands-free release.

## Install and set up

1. Install `ELLIOT_Preview_v0.6.0.apk` over the previous Preview in the same profile. It retains the delivered Preview certificate. Actual installation/migration is still a handset check. The original-package build is unsigned and needs the owner's original signing key; the Preview has separate private data from the original-package app.
2. Tap Resume while the app is visible. Grant microphone access if voice is wanted. Text and direct Tools controls remain available without it.
3. In Controls, select an available confirmed male voice. Android engine gender metadata is imperfect; the existing explicit audition/confirmation process remains. Unknown voices are not silently selected.
4. Download the optional English Vosk model in Offline wake word. Use **Enable wake and arm background voice**. The core notification reports whether voice was armed; Controls reports local capture/echo readiness. The local wake/command model is English; multilingual improvements remain later work.
5. Try “hey Elliot, set volume to thirty percent.” Then try saying “hey Elliot” during a long spoken answer and speak a new request. Check the new turn does not require a Stop tap and that old speech does not restart.
6. Turn on **ELLIOT approaches me** if wanted. Save an interval (5–1440 minutes) and active start/end times. End is exclusive; overnight windows are supported, equal start/end means all day. Use **Ask me now** for a manual test instead of waiting for the next scheduled question.
7. Enable Room mode only if spoken notices with the display inactive are wanted. DND, snooze, current conversation and global Off still apply. Microphone service arming and manufacturer power restrictions remain relevant.
8. Configure allowed notification package IDs in Controls. By default, ELLIOT gives a generic question rather than reading the body. Say “read it” during its listening window. Reading eligible contents while locked is a separate explicit setting. Notification text remains outside model conversation history.
9. For broader reasoning, configure an HTTPS API root/key/model and test structured tool support. Gemini Nano is still available on supported hardware while ELLIOT is the top foreground app, with no silent cloud fallback.
10. Enable overlay and Accessibility separately for floating captions and eligible screen assistance. Screen/phone-control switches and existing protected-content/freshness checks remain.

## Direct commands and scope

These examples use the native parser when recognized:

- “Set volume to thirty percent.”
- “Start a timer for five minutes.”
- “Open YouTube” when one visible launchable app matches that label.
- “Go back,” “go home,” “show recents,” “open notifications.”
- “Scroll down,” “scroll up” with eligible Accessibility access.
- “Check my battery,” “check my phone,” “check my free storage.”

The first parser/scope implementation is conservative and oriented to English request forms. A language model may handle broader wording, but tools reject mismatched scope/critical values and ask for missing detail rather than treating all model output as authorized. Recognition errors can still misidentify a spoken request; this is not biometric owner authentication.

Native UI controls mint a grant only for their selected tool. Model-returned JSON cannot create such a grant. Switching optional review on restores the original explicit review path. Required Android grants, lock checks, screen-capture consent and provider authentication are not bypassed.

The timer tool now requests `EXTRA_SKIP_UI=true` in direct mode. Clock decides whether it can start without UI. A dispatched timer request is not an independently verified running timer. Review mode retains the earlier Clock-preview behavior.

Voice controls include “cancel,” “stop,” “turn off,” “be quiet”/“snooze”/“not now” for a one-hour check-in snooze, and “stop check ins” for disabling automatic check-ins. In a live notification question, “not now” dismisses that notice. Global Off stops the companion and pending work; it cannot undo an action already dispatched.

## Preserved tool inventory

| Tool | Scope |
| --- | --- |
| `device_info` | Public device/Android, battery, RAM, storage, network and capability observations |
| `list_apps` | Paginated visible launcher inventory, with optional name filter |
| `open_app` | Launch a matched requested app; no claim that an in-app workflow finished |
| `open_settings` | Open the requested supported settings panel; owner/system handles restricted switches |
| `set_media_volume` | Set the requested media percentage; not calls, alarms, ringer or DND |
| `set_timer` | Request Clock timer setup/start, with honest handler/verification limits |
| `search_web` | Public search results, snippets and source URLs |
| `read_web_page` | Bounded public HTTPS text; no login/session/JavaScript access |
| `open_url` | Requested public HTTPS browser launch |
| `read_screen` | Eligible external screen text/references through enabled Accessibility |
| `click_screen` | Fresh labelled reference matching requested target; sensitive submissions remain manual |
| `type_text` | Requested non-password text replacement; does not press Send |
| `scroll_screen` | Requested direction, target/freshness guards retained |
| `navigate` | Requested Back/Home/Recents/Notifications with control grant/unlock checks |
| `save_memory` | Owner-requested note in local encrypted-body storage |
| `recall_memory` | Bounded supplied saved-note context |

All 16 tools remain. Tool execution retains schema validation, runtime epochs, enabled switches, cancellation, result receipts and bounded model loops. Direct mode changes duplicate application approval, not Android privileges.

## Data, voice and capability boundaries

Message/note bodies use Keystore-backed AES-GCM in SQLite. IDs, roles, timestamps and source URLs remain ordinary metadata. Memory-off conversation context stays in RAM. Portable memory backup/restore, semantic/project retrieval and durable action history are ordered later work, not completed features here.

Local wake capture/command decoding uses transient microphone audio and does not save PCM recordings. Its unrestricted English recognizer costs more resources than the earlier keyword-only grammar. AEC is requested where the device exposes it; an enabled effect is not proof of perfect echo rejection. Speaker/headset/ambient-noise and power measurements are required before advertising reliable room use.

The Android speech service remains the fallback for manual/follow-up input when the local wake stream is not ready; its provider may use the network. Selected TTS voices may also be network-backed, as shown in the voice catalog. Scheduled questions are local templates, not background Nano inference. Responses to the owner can use the configured cloud/Nano path.

Notification contents are handled locally for announcements and are excluded from persistent/RAM model message history by that path. Web/screen/tool/reference text cannot mint native command authorization. Cloud mode sends the context deliberately supplied for a request to the configured provider.

No root, silent permission grants, lock bypass, arbitrary shell, account API integrations or unrestricted other-app files were added. Existing feed learning remains scheduled reference ingestion, not model training.

## Build and roadmap

Use JDK 21 for the full simulation/test workflow, Gradle 8.11.1, Android platform 36 and build tools 35.0.0. Application bytecode targets Java 17. Configure your SDK in local.properties:

```sh
./gradlew clean testReleaseUnitTest lintRelease lintPreview assembleRelease assemblePreview
```

The release APK needs the original owner's signing key. Preview uses the local debug signing configuration, with application debugging disabled. A newly generated local debug key cannot update an installed Preview signed by the retained key. No signing key or API key is included in the source ZIP.

See BUILD_PLAN.md for all 22 ordered steps. The hands-free foundation, capability registry and overlay-layout changes are implemented candidates. Richer chat, memory backup, task-ledger, integrations and platform experiments remain in the ordered plan. See FEATURE_REVIEW.md and VALIDATION.md for exact checks and remaining gates.
