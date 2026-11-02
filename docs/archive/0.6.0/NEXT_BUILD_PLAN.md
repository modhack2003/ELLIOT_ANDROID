# ELLIOT 0.6.0 — next build plan

Prepared: 30 October 2026, Asia/Kolkata. Status: **software-verified 0.6.0 Preview candidate built; physical-device acceptance pending**.

Baseline: delivered 0.5.0 Preview, version code 7. Proposed next version: **0.6.0, code 8**, implementing ordered roadmap **step 8**. The version/code must be checked against the latest delivered artifact before implementation. The original planning specification below is retained; the implementation outcome and evidence are appended in section 13.

Planning method: Mobile App Builder’s App Spec/PRD and UI Design System skills, adapted to ELLIOT’s existing native Android implementation. Keep Kotlin, Jetpack Compose, native overlay Views and the current tool/runtime architecture. A no-code rebuild would not preserve the required Android system behaviour.

## 1. Goal and success measures

Make the floating companion easy to position, read and control while another app is open. It should stay reachable through rotation and keyboard changes, show bounded live captions, and remain usable with large text or assistive input.

The main owner is Bikram, who wants a companion that acts on clear requests and can be used hands-free. Additional use cases are one-handed use, reading while multitasking, large text, TalkBack/Switch Access and a phone with limited graphics resources. No new account or subscription is needed for this milestone.

Success is measured against acceptance tests, not appearance alone:

- The orb and its controls remain within the usable window in all supported test scenarios.
- Opening/closing the keyboard or rotating the phone neither loses the draft nor sends/repeats a task.
- Long answers remain bounded; the rest of the phone stays usable.
- Every overlay control has a target of at least 48 × 48 dp and an accessible alternative to dragging/long pressing.
- Reduced motion preserves readable state and captions. Hidden/detached/Off windows schedule no rendering or typing callbacks.
- All 16 existing tools, male voices, memory, wake interruption, scheduled spoken check-ins and global Off remain available under their existing settings.

## 2. Baseline evidence and problems to address

The current 0.5.0 validation report records 98 passing automated executions, zero lint errors, 69 release/68 Preview lint warnings, signature continuity and 16 KB structural alignment checks. These are baseline results, not results for 0.6.0.

Source inspection found the following implementation choices. These are code-level findings; they are not a claim that every phone currently exhibits every failure.

| Finding in 0.5.0 | Consequence to check | Planned correction |
| --- | --- | --- |
| Orb starts as an 80 dp window with raw saved `x`/`y` pixels | Placement may not transfer cleanly across size, density or orientation changes | Normalized edge anchor and per-window-profile placement |
| Placement uses display metrics and fixed 24/48/60 dp margins | System bars, cutouts and changing usable space may be handled inaccurately | Measured safe bounds and a shared geometry policy |
| Some overlay controls use explicit 32–38 dp heights | Effective touch targets can be too small | Minimum 48 dp targets and adaptive rows |
| Long press is recognized on release using elapsed time | Gesture behaviour and assistive alternatives need improvement | Platform gesture thresholds, cancellation handling and explicit actions |
| Panel uses fixed size/percentage assumptions and resize flags | Keyboard visibility and landscape space need real-window validation | Insets-aware placement with a conservative fallback |
| Caption placement assumes fixed vertical space despite variable text | Large fonts or wrapping may collide with the orb or screen edge | Measure caption size before placement and remeasure on content/font changes |
| Animation already supports system scale zero and detach/screen-off pauses | Existing support must be preserved, not rebuilt unnecessarily | Add explicit owner motion preferences and test the existing rendering paths |

The existing floating captions, typing reveal, long-text cap and shared capability/session state are working source features to improve and retain.

## 3. Prioritized scope

Effort values are relative: S = localized work, M = several cooperating components, L = platform/lifecycle work with device verification. They are not calendar estimates.

| ID | Feature | User value | Effort | Priority / target |
| --- | --- | --- | --- | --- |
| O01 | Safe bounds and one geometry policy for orb, caption and panel | Prevent inaccessible windows and overlaps | L | Must, 0.6.0 |
| O02 | Edge docking, normalized saved position and reset/move controls | Reliable one-handed placement | M | Must, 0.6.0 |
| O03 | Rotation, resizing and keyboard transitions | Keep input and controls reachable | L | Must, 0.6.0 |
| O04 | Compact captions and bounded responsive transcript | Read voice output without losing the foreground app | M | Must, 0.6.0 |
| O05 | 48 dp controls, assistive labels/actions and distinct gestures | Make the orb usable without precise touch or dragging | M | Must, 0.6.0 |
| O06 | Motion/typing/haptics preferences and lifecycle cleanup | Calm, efficient presentation | M | Must, 0.6.0 |
| O07 | Orb appearance/layout section in Controls, with reset | Discoverable personalization and recovery | M | Must, 0.6.0 |
| O08 | Hands-free, permission, privacy and tool regression checks | Preserve the companion’s essential workflow | L | Must, every build |
| O09 | Narrow native commands such as “move your orb left/right” | Change ELLIOT’s own layout by voice | S–M | Should; additive if release gates are met |
| O10 | Context cards, full theme variants and rich answer reader | Deeper presentation | L | Retained later scope; do not mark complete here |

The release’s minimum useful loop is: wake or tap → listen → act/respond → show compact text → interrupt or follow up, with the orb reachable and Off available. Optional appearance settings must not become prerequisites for using that loop.

## 4. User stories and acceptance criteria

| Story | Required behaviour / evidence |
| --- | --- |
| As the owner, I want to place the orb near either thumb | Drag ends at a reachable edge; move-left/right/reset also work without dragging; placement is restored for the matching window profile |
| As the owner, I want typing to work while the orb is open | Keyboard does not cover Send/Stop/Off in supported layouts; only deliberate text entry acquires focus; closing the panel releases focus without losing the draft |
| As the owner, I want to rotate during a reply | Same active turn, draft and answer remain; no duplicate send, phone action or newly started voice turn |
| As the owner, I want long voice answers shown as text | Caption stays compact, full text remains reachable, and reading older text does not force a scroll to the latest token |
| As a TalkBack/Switch Access user, I want equivalent actions | Open, Talk, move, close, Stop response and Turn Off are discoverable through labelled controls/custom actions; drag/long press are optional shortcuts |
| As a large-text user, I want readable controls | At 1×, 1.5× and 2× font scale, labels wrap by words or controls reflow; no letter-by-letter button labels and no clipped essential action |
| As the owner, I want a calm interface | Reduced motion keeps state labels; instant text skips decorative typing; haptics follow system settings and the owner switch |
| As the owner, I want global Off to mean Off | All overlay windows and callbacks disappear; later layout/permission events cannot bring them back without a current Resume |
| As the owner away from the phone, I want the same voice workflow | “Elliot”/“hey Elliot” interrupts speech; scheduled check-ins can ask and accept a spoken reply under the existing policy; no redundant Allow page is introduced |

## 5. Interaction and screen specification

Keep the existing primary navigation: Companion, Tools, Memory, Learn and Controls. This build does not reorganize the whole app into Today/Tasks/More.

| Surface | Layout / behaviour |
| --- | --- |
| Docked orb | Default visual size remains familiar; minimum interactive area 48 dp; labelled state from the shared session; tap toggles the compact panel; long press still offers Talk; accessible actions provide alternatives |
| Live caption | Short state label and approximately 3–5 lines, measured at the selected/system font scale; default target at most 20% of usable height; tap opens details; hidden when privacy rules require |
| Transcript panel | Header with Open ELLIOT, Turn Off and Close; readable reply/partial transcript in its own scroll region; input and Send when space permits; Talk/Stop response and Screen/Explain remain reachable through adaptive controls |
| Compact-space fallback | When minimum controls and readable text cannot fit, collapse captions and offer Open ELLIOT rather than shrinking controls or covering the screen. Keep Off accessible through the orb’s action and existing notification; explain the fallback locally |
| Controls → Orb & appearance | Visibility policy, visual size, opacity, caption text size/timeout, motion, typing reveal, haptics, move-left/right/reset and preview/reset. Advanced options can expand without burying basic controls |
| Companion | Retain the compact presence/readiness card. Apply the same text/motion choices to the touched surfaces and preserve keyboard room; full rich-chat work stays at step 9 |

Panel space budget: aim for no more than 45% of the current usable height and 360 dp, whichever is smaller. This is a **proposed default**, not an Android limit. If scaled content cannot fit within that budget, use the compact-space fallback; do not silently reduce font size or turn the panel into a full-screen overlay.

Distinguish controls clearly:

- **Close** hides the panel; it does not cancel a running task or disable voice.
- **Hide captions** hides visual captions; it does not mute ELLIOT.
- **Stop response** cancels the current response/listening operation using the existing cancellation boundary.
- **Quiet/snooze** keeps the existing spontaneous-speech policy.
- **Turn Off** stops the companion under the existing global-stop contract.

Direct commands retain their existing authorization. Opening layout settings must not add another action-approval flow. Optional review mode remains optional; Android grants are still handled through Android.

## 6. Geometry, keyboard and lifecycle rules

1. Measure the current window/display area and delivered system-bar, cutout and keyboard information. Use platform-appropriate APIs for API 30+ and an explicit fallback for API 26–29. Convert dp/sp to pixels only at the rendering boundary.
2. Derive a safe rectangle, subtracting observed occluded regions and a proposed 8 dp breathing margin. Account for gesture edges when reliable information is delivered. Do not treat a fixed 24 dp offset as a measured system inset.
3. Measure the actual orb/caption/panel dimensions. Clamp every window to the safe rectangle. Place caption/panel in the side with sufficient room; do not assume a fixed caption height.
4. After a deliberate drag, save edge and normalized vertical position. A temporary keyboard dodge must not overwrite the owner’s preferred placement.
5. On rotation/window/font/display-size change, recalculate bounds and reflow the same current session. Restore the relevant profile; fall back to a safe default when a saved profile is invalid.
6. Acquire keyboard focus only after deliberate input. Release it on Close, phone-action handoff, lock or Off as appropriate. Relayout must never dispatch an action or resend text.
7. Handle touch cancellation, interrupted drags, detach and orientation change. An accidental drag or cancelled gesture must not trigger Talk/Open on release.
8. Observe lock/screen-off/permission revocation with existing signals and rechecks. Remove private captions and accessibility text from hidden windows. Retain only the existing permitted in-memory conversation state; never write transcript text into layout preferences.
9. During an ELLIOT-initiated settings/permission handoff, hide/release the overlay as needed and restore it only when still permitted and running. Do not claim that ELLIOT can detect every other app’s system dialog.

Android application overlays sit below critical windows such as the IME. Floating keyboards can report no keyboard insets. Consequently, the implementation must test actual delivery of bounds/insets to these particular overlay windows and provide a manual move/reset/open-app fallback; flags or a simulator alone cannot establish universal keyboard avoidance. See references A1–A3.

## 7. UI design sheet

Apply these as candidate tokens for the touched orb/panel/layout-controls surfaces, while retaining the existing identity. Validate rendered contrast over the actual translucent surfaces; flat-color ratios alone do not certify the final UI.

| Token | Proposed dark value / rule |
| --- | --- |
| Background | `#060B13` |
| Raised surface | `#101A2A` |
| Primary text | `#E7EEF6` |
| Secondary text | `#B6C4D5` |
| Muted but readable text | `#91A4BB` |
| Accent / ready | `#53D5D0` |
| Attention | `#F5BC69` |
| Error | `#FF929C` plus an icon/text explanation |
| Typography | Existing platform sans-serif; headings 22 sp/semibold, normal body 16 sp, captions 14–16 sp; respect system scaling. Monospace only for technical data |
| Spacing | 4/8/12/16/24 dp; avoid dense rows of small text buttons |
| Shape and depth | 16–24 dp card radius, restrained border and one elevation layer; no new animated full-screen backdrop |
| Controls | At least 48 × 48 dp effective target; allow rows to wrap or become a vertical group |
| Icons | Reuse the existing Material/native icon vocabulary and explicit accessible labels |
| Motion | Proposed 120–200 ms docking/expansion; no overshoot covering other controls; reduced motion snaps state while retaining the label |
| Copy | Short and concrete: “Listening”, “Wake is off”, “Open Controls”, “Moved to the left”. Keep internal SDK/debug detail in diagnostics |

Contrast targets for the final rendered UI: at least 4.5:1 for normal text, 3:1 for large text and meaningful controls against their adjacent background. Status must also be understandable from labels/icons. Do not claim these targets are already verified in 0.6.0.

Recommended proposed defaults: familiar 80 dp orb, 100% opacity, system text scaling with 14 sp base caption, 20-second caption timeout, System motion preference, existing typing behaviour and system-respecting haptics. Let the owner preview changes. Appearance preferences must not change which events are announced aloud.

## 8. Data, modules and migration

Only local presentation preferences and transient UI state are needed. No new backend, account database, permissions, voice provider or paid integration is required.

| Proposed component | Responsibility / integration |
| --- | --- |
| `OverlayGeometry` | Pure bounds/placement calculation; inputs include safe bounds, measured sizes, anchor and keyboard observations; outputs include placements or compact-space fallback |
| `OverlayLayoutStore` | Versioned appearance/layout preferences and migration of legacy placement; no conversation or notification content |
| `OverlayWindowController` | Lifecycle, focus, window attach/detach, callbacks and main-thread layout updates; may initially be a contained extraction from `OverlayManager` |
| `OverlayManager` | Keep the current conversation/caption API and public integration points while delegating geometry/focus work |
| `ArcCoreView` / renderer | Preserve shader/Canvas fallback and existing reduced-motion support; add owner motion preference and accessible actions |
| `SettingsScreen` or new appearance component | Preview/reset/reposition controls and clear visibility versus permission status |
| `CapabilityRegistry` / shared session | Continue as the readiness/state source; relayout must not manufacture listening or tool availability |

Proposed persisted fields: layout schema version, size/opacity, caption font/timeout, motion mode (`system`, `reduced`, `expressive`), typing mode (`reveal`, `instant`), haptics preference and per-profile `{edge, verticalFraction}`. Validate/clamp corrupt values. Profile keys should describe orientation/usable-size class, not hardware identifiers. Defaults preserve existing choices wherever applicable.

Migration: read the legacy `eiliot_orb_geometry` pixel position once; clamp it to the current known viewport and derive that profile’s anchor. Unknown profiles start at a safe default rather than pretending the old orientation is known. Retain legacy values for rollback; do not modify message/note schemas or erase existing settings. Draft, scroll position and panel mode remain transient UI/session state rather than new plaintext persisted content.

## 9. Implementation order

Each item is completed and checked before depending on it. This is the original planned sequence; current implementation evidence is recorded in section 13.

| Order | Work | Depends on | Completion evidence |
| --- | --- | --- | --- |
| 8.1 | Confirm latest 0.5.0 source/artifacts, reproduce baseline and record devices | Baseline | Original 98 checks accounted for; no unseen newer build overwritten |
| 8.2 | Implement pure geometry and compact-space rules | 8.1 | Bounds, overlap, minimum size and invalid-input tests |
| 8.3 | Add versioned layout preferences and legacy placement migration | 8.2 | Existing data preserved; corrupt values recover; profile restore/reset tests |
| 8.4 | Integrate live bounds, rotation and focus/IME handling | 8.2–8.3 | No duplicate windows/listeners, no lost draft or task replay; actual overlay checks on available phones |
| 8.5 | Reflow captions and transcript controls to the space budget | 8.4 | Long text, Unicode, large-font and keyboard render/interaction evidence |
| 8.6 | Implement accessible gestures/actions and 48 dp controls | 8.5 | TalkBack/Switch Access/manual alternatives and cancellation tests |
| 8.7 | Add appearance preview/reset and motion/typing/haptics controls | 8.3–8.6 | System/owner choices honored, no fake listening animation; hidden callbacks stop |
| 8.8 | Recheck all tools, direct-command/optional-review paths, wake and check-ins | 8.4–8.7 | Existing regression suite passes plus targeted new behaviour tests; phone-only results kept separate |
| 8.9 | Build and verify the candidate; update plan/release notes | 8.8 | Signing/package/version, lint, alignment, source and verification handoff |

## 10. Verification and release gates

| Layer | Cases / required evidence |
| --- | --- |
| Pure geometry | Portrait/landscape, both edges, 320/360/412 dp widths and representative wide windows; cutout/navigation insets; invalid anchors; keyboard changes; rectangles too small for a panel |
| Framework | Existing API 28/36 simulations retained; preference migration, cancellation/focus/attach cleanup and permission revocation; older API fallback code paths checked |
| Render / interaction | Normal, 1.5× and 2× text; compact portrait and landscape; short/very long/Unicode output; all essential actions reachable; no letter-stacked labels |
| Physical overlays | Available Samsung/Poco devices with the exact model/OS recorded; gesture/three-button navigation, cutout, keyboard open/closed, floating keyboard where available, rotation and background/app transitions |
| Hands-free carryover | Scheduled spoken check-in → spoken reply; wake during long TTS → old speech stops → same new request is captured; no second wake or Stop tap as a workaround; direct action without redundant Allow |
| Audio/device conditions | Actual selected male TTS, speaker/headset, quiet/noisy room and approximately 1 m/2 m; record failures and unsupported combinations honestly |
| Off / privacy | Off during streaming, typing, dragging, permission return and rotation; windows/callbacks remain stopped; lock hides private captions and their accessible text |
| Resource use | Compare idle/active/hidden rendering with 0.5.0 on the same phone; no repeated window allocations or callback growth; screen-off/detached presentation schedules no frames |
| Packaging | All retained tests plus meaningful new tests pass; no lint errors/new unexplained warnings; same Preview signer/package, code greater than 7, 16 KB structural checks, source ZIP without keys |

Use behavioural evidence rather than promising a predetermined test count. Record which checks ran in a simulator, renderer or physical phone. Preserve the unresolved 0.4/0.5 wake/echo/room-distance gates; a prettier overlay does not close them.

Two honest delivery states are possible: **software-verified Preview candidate** if only automated/render checks are available, or **device-validated for the listed configurations** after the required phone checks pass. Do not advertise universal hands-free/keyboard reliability from a simulator result. Failed mandatory software checks block the candidate; unknown device results remain explicitly pending.

## 11. Required handoff

Expected artifacts after implementation: `ELLIOT_Preview_v0.6.0.apk`, original-package unsigned update if the original signing key is still unavailable, full source ZIP, feature review, validation report and relevant native UI renders. Retain the Preview signing identity and separate original-package identity; no key belongs in the source archive. Provide the installable Preview through the established Google Drive delivery workflow and save source/reports with the project documents.

Update the ordered tracker only when there is actual evidence. Keep the older build available until the owner has checked the update. Document settings migration and remaining device limits. Each future release must continue to include a useful UI/UX improvement, preserved functionality and a record of what was checked.

## 12. Boundaries and later roadmap

Scheduled automatic spoken approaches and wake-word interruption during TTS remain mandatory carried-forward features. Preserve all 16 tools, direct matching-command execution, optional review, male-only confirmed/declared voices, memory, feed learning, screen assistance, shared readiness and global Off.

The 0.6.0 plan focuses on overlay usability. Step 9 retains rich chat, reader, citations/copy/share, search and branches. Steps 10–22 retain memory backup/migrations, durable tasks, observation/context, smarter initiative, semantic memory, routines, file/OCR tools, account integrations, research workspaces, voice/language upgrades, model routing and assistant/desktop/watch experiments. Keeping those later milestones does not remove existing features.

Open items for implementation-time measurement: actual IME/inset delivery on the owner’s phones, supported device/window profiles, measured layout/performance at large text, and the outstanding acoustic results. These do not prevent preparing this plan. No exact completion date or new cloud cost is promised before that work is measured.

## References

- Baseline: saved `ELLIOT_ORDERED_BUILD_PLAN.md`, version 1; `ELLIOT_Futuristic_Features_and_UX_Roadmap.md`, version 4, sections 11–12 and step 8; `ELLIOT_v0.5.0_validation.md`.
- Source reviewed: 0.5.0 `OverlayManager.kt`, `ArcCoreView.kt` and relevant Controls code. The proposed new classes/preferences above do not exist merely because they are specified here.
- A1: [Android overlay window contract](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_APPLICATION_OVERLAY).
- A2: [WindowInsets.Type, including IME limitations](https://developer.android.com/reference/android/view/WindowInsets.Type).
- A3: [Window insets and edge-to-edge Views](https://developer.android.com/develop/ui/views/layout/edge-to-edge). Activity layout guidance is not proof that an external overlay receives identical keyboard information.
- A4: [Accessible Android Views and touch targets](https://developer.android.com/guide/topics/ui/accessibility/views/apps-views).
- A5: [Custom View accessibility actions](https://developer.android.com/guide/topics/ui/accessibility/views/custom-views) and [Compose semantics/actions](https://developer.android.com/guide/topics/ui/accessibility/composables).

Platform references checked 30 October 2026. Dependency or API changes must be verified when implementing; retain the pinned 0.5.0 toolchain unless a necessary change is independently justified.


## 13. Implementation outcome — 30 October 2026

The 0.6.0 Preview candidate is built, with 120 passing automated executions, zero lint errors and verified Preview signature/16 KB alignment. The original 98 regression executions remain. UI UX Designer mobile/accessibility guidance was applied to actual native Views/Compose, not a replacement website or mockup.

Implemented: shared safe geometry and measured captions, normalized placement/migration/reset, full/compact/input layouts, RAM draft continuity, 48 dp labelled controls, gesture cancellation and accessible alternatives, appearance/typing/motion/haptics choices and local move/reset/caption commands. Automated checks and native renders cover the software parts of 8.1–8.9. Native keyboard-aware input mode is an addition to the original compact fallback plan.

Physical keyboard/rotation/OEM/accessibility, acoustic and room-distance checks are still pending. No device validation is claimed. Detailed counts, warnings, artifacts and boundaries are in `ELLIOT_v0.6.0_validation.md`; actual component renders are included in the source under `docs/ui/0.6.0`. Step 9 and all later roadmap items are retained.
