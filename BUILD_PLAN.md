# ELLIOT — ordered implementation plan

Baseline: 0.3.1. Source roadmap: HF-2, 29 October 2026. Work is incremental; existing tools, data and manual controls remain available. A source change is not device acceptance. Status is updated after verification, not when a feature is merely proposed.

## Milestone 1 — 0.4.0 hands-free foundation

| Step | Work | Depends on | Verification | Status |
| --- | --- | --- | --- | --- |
| 1 | Shared voice-turn ownership, cancellation and stale-callback protection | Existing runtime epoch | Interrupted speech/results cannot resume an old turn; Off invalidates all turns | Implemented; automated checks passed |
| 2 | Wake during TTS; “Elliot” / “hey Elliot” stops playback and captures the next request | 1 | Test the transition and microphone ownership; actual acoustic acceptance at 1 m/2 m remains a handset gate | Implemented; automated checks passed; handset gate pending |
| 3 | Scheduled spoken check-ins with active hours, interval, snooze and a spoken reply window | 1–2 | Window boundaries, no catch-up bursts, no-response timeout, Quiet/Off and interrupted check-ins | Implemented; automated checks passed; handset gate pending |
| 4 | Direct-command authorization; optional review mode; no repeated Allow taps for matching supported tasks | 1 | Validate requested scope/arguments, reject unrelated actions, retain grants/lock checks and cancellation | Implemented; automated checks passed; handset gate pending |
| 5 | Hands-free settings, readiness indicators, notification speech choices and existing orb/chat feedback | 2–4 | State consistency, model/voice/microphone missing cases and preserved direct tools | Implemented; automated checks passed; handset gate pending |
| 6 | Release validation, signed Preview, unsigned original-package update and source handoff | 1–5 | Regression/framework tests, lint, signer/alignment checks; publish exact remaining phone checks | Implemented; automated checks passed; handset gate pending |

Steps 2 and 3 are mandatory in this milestone. Unsupported hardware behavior is reported and tested; it is not replaced by a manual Stop workaround. This milestone does not claim the whole roadmap is finished.

## Subsequent milestones, in dependency order

| Step | Work | Roadmap coverage | Completion evidence | Status |
| --- | --- | --- | --- | --- |
| 7 | Full capability registry and unified app/orb/task readiness | A01–A02, R01, R03, R11 | UI/model observe the same capability facts and recovery routes | Implemented in 0.5.0; 98 automated checks passed; physical-device gates remain |
| 8 | Orb docking, safe areas, keyboard/rotation, touch accessibility and motion settings | A08, section 11.3/11.6 | Real layout checks at small/large text, orientation and IME variations | Implemented in 0.6.0; 120 automated checks passed; device gates pending |
| 9 | Structured chat, long-answer reader, copy/share/citations, history search and branches | A10, U01–U12 | Reading position, large output, screen reader and no action replay on regenerate | Planned |
| 10 | Memory migrations, paging, scoped forgetting and portable encrypted backup/restore | A12, M04–M06, M08–M12, R05 | Cross-device restore and migration/recovery tests; session-only mode preserved | Planned |
| 11 | Durable task ledger, checkpoints, queue, pause/resume and duplicate prevention | A03, T01–T11, R04/R10 | Interrupted/ambiguous operations do not produce duplicate effects | Planned |
| 12 | Opt-in observation hub and context engine: device events, usage summaries and explicit project context | A04, C01–C08 | Permission/revocation, stale context, retention and battery measurements | Planned |
| 13 | Context-aware initiative, suggestion inbox, feedback, cooldowns and grouped notifications | A05, P01–P10 | Relevance, dismissal, repetition and quiet-policy evaluation | Planned |
| 14 | Project/episodic memory, semantic retrieval and source-linked correction | A06–A07, M01–M09 | Retrieval quality, superseded facts, provenance and privacy of derived indexes | Planned |
| 15 | Persistent reminders/to-dos and editable, versioned routine builder | A11, T12, F01/F10, section 7.4 | Scheduling/time zone/Off behavior, simulation and scope review | Planned |
| 16 | Owner-selected file workspace, document/PDF/OCR tools and share-to-ELLIOT | A13, F05–F07, C08, section 7.3 | File-grant loss, unsupported files, extraction quality and write verification | Planned |
| 17 | Calendar/contact/communication/media connectors, one complete integration at a time | A14, F02–F04/F08–F09/F11–F12 | Correct account/recipient, OAuth revocation and actual API outcomes | Planned |
| 18 | Personalized research workspaces, followed topics, citations, change detection and offline references | A15, W01–W12 | Source quality, freshness, budgets, outages and no executable web instructions | Planned |
| 19 | Richer male voice catalog, language/recognition repair, pronunciation and advanced conversation tuning | V01–V12, section 8.1 | Actual engine/language, gender confirmation, audio routing and false activation evaluation | Planned |
| 20 | Explicit local/cloud model routing and supported newer Nano/local engine experiments | A16, section 10 | Model availability, limits, cost controls and no hidden cloud fallback | Planned |
| 21 | Today/Tasks/Memory navigation and progressive setup, guided recovery and adaptive presentation | Sections 11–12 | End-to-end everyday flows; no removed capability or forced inaccessible control | Planned |
| 22 | Default-assistant/AppFunctions prototypes and trusted desktop/watch connections | A17–A18, section 15 | Platform eligibility, scopes, pairing, revocation and supported-device evidence | Research |

## Work that runs through every milestone

- Preserve package/storage identities, male-only selected speech, all 16 tools, manual alternatives and global Off semantics.
- Add meaningful tests where behavior changes; keep model quality, framework simulation, acoustic tests and physical device results separate.
- Apply R01–R16 throughout: typed interfaces, cancellation, diagnostics, resource budgets, migrations, dependency review and signing continuity.
- Observe section 16 platform/distribution constraints; use APIs and integrations suited to the deployment.
- Follow section 18 acceptance criteria. Record device/version/mode for results rather than claiming universal behavior.
- Revisit priorities after real-device findings; do not silently delete remaining roadmap items.
- **Every future build includes a useful UI/UX improvement**, preserved large-text/manual/Off access, and release notes stating the changed screen and its verification. This is an ongoing product requirement from 30 October 2026.

## Current deliverable boundary

Milestone 1 has a software-verified 0.4.0 preview candidate. Its mandatory acoustic/device acceptance gates are still pending. Step 7 is now implemented as the 0.5.0 live-capabilities candidate while the 0.4.0 physical-phone gates remain open. The 0.6.0 candidate implements step 8 with software/render verification; step 9 is next. Real-device acceptance for step 8 remains pending. Other milestones are retained and remain planned until implementation and checks are recorded. Real microphone, speaker echo, wake accuracy, screen-off service survival and room-distance operation require an actual phone.


## Prepared next build — 0.6.0

30 October 2026 (India): the developer-ready specification is `ELLIOT_v0.6.0_Next_Build_Plan.md`. It expands step 8 into ordered implementation work and acceptance criteria using the Mobile App Builder PRD and UI design-system methods. This is planning status; no 0.6.0 app code or APK is claimed.

1. Establish safe overlay geometry and normalized saved placement.
2. Handle rotation, resizing and keyboard/focus changes without losing drafts or replaying tasks.
3. Reflow bounded captions/transcript and provide 48 dp accessible controls.
4. Add presentation/motion/typing/haptics preferences and recovery/reset controls.
5. Recheck all existing tools, wake interruption, scheduled spoken check-ins and Off behaviour; record simulator and physical-phone results separately.
6. Build, verify signatures/alignment, and deliver the Preview/source/reports only after implementation checks pass.

Every-build UI improvement remains mandatory. All later roadmap items are retained. Existing handset/acoustic gates remain pending until actual tests establish their results.


## Implementation record — 0.6.0

30 October 2026 (India): built the step-8 Preview candidate with 120 passing automated executions, zero lint errors and signature/alignment checks. Native geometry, edge/profile persistence, keyboard-aware layouts, 48 dp actions, accessibility alternatives and appearance/motion preferences are implemented. Native layout commands are additive. Earlier and new physical-device gates remain open; no universal keyboard/acoustic certification is claimed. Step 9 remains planned.
