# ELLIOT 0.6.0 — feature review

30 October 2026 (India). Implements the software portion of roadmap step 8 with UI UX Designer’s mobile/accessibility guidance, retaining native Kotlin/Compose/Views.

## Changes

| Area | Result | Practical boundary |
| --- | --- | --- |
| Placement | Shared geometry for orb/caption/panel, measured safe bounds, edge docking and normalized profiles | Actual IME/cutout/gesture delivery varies by device; hardware checks remain |
| Layout | Full transcript, compact actions and a dedicated input layout when the keyboard reduces space | Too little space opens the main app; floating text stays bounded |
| Drafts | Overlay and Companion share the same in-memory draft; reconstruction does not send a message | Layout preferences never persist conversation text; no new cross-process draft persistence was added |
| Controls | 48 dp native action targets, labelled actions, explicit More menu and gesture cancellation | Actual TalkBack/Switch Access service interaction remains a phone test |
| Appearance | Collapsed settings card with preview, size/opacity, caption font/timeout, motion/typing/haptics/reset | Visual preferences do not enable observation, unmute speech or grant permissions |
| Motion | Calm system idle, expressive idle, reduced/static presentation, chat/native instant text | Real battery/thermal comparisons remain pending |
| Native layout commands | Narrow commands to move/reset the orb or show/hide captions without a model key | These act only on ELLIOT’s layout; ambiguous/generic directions are not phone commands |
| Lifecycle | Lock/Off/revocation clean up private windows, callbacks, focus and listeners | An already dispatched phone action cannot be undone by closing its window |
| Settings handoff | Floating windows are hidden for ELLIOT-opened settings pages | Reopen ELLIOT to restore them; return to arbitrary external apps is not inferred |

## Preserved

All 16 tools and their command scope/optional review, male-only confirmed or declared voices, memory/database/package identities, wake-word speech interruption, scheduled spoken check-ins, spoken follow-ups, Room/privacy controls, live capability dashboard, public-web/feed features and global Off remain. The original 98 regression cases are retained in the source.

## Rendering review

Tests render the actual native panel/orb components with sample data at normal/large text and landscape, and retain the earlier capability renders. These are simulated component scenes, not photos of a physical phone or proof of OS overlay/keyboard behaviour. Review found and corrected a cramped input layout, missing single-line button text in the render path and an incorrectly configured landscape test viewport.

## Remaining scope

Step 9 rich chat/reader/search/branches, memory backup, durable tasks, account connectors and the remaining roadmap stay planned. Current work does not add root, silent grants, lock bypass, private-account APIs or unrestricted app files. No new paid service or Android permission was added.

The earlier mandatory HF-2 phone gates remain open: actual Vosk/TTS quality, wake interruption under speaker echo, 1–2 m operation and OEM screen-off survival. Native source, framework tests and renders cannot certify those conditions.

Build outputs, test totals, lint warnings and signing/alignment checks are recorded in VALIDATION.md after final verification.
