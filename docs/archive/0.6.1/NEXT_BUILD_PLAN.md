# ELLIOT — after 0.6.1

Prepared 30 October 2026, Asia/Kolkata. The attached 0.6.1 blueprint is implemented to the extent recorded in FEATURE_REVIEW.md and VALIDATION.md. The original 0.6.0 specification is archived in docs/archive/0.6.0; do not treat it as a pending 0.6.1 plan.

## Finish handset acceptance first

1. Install/update the signed compatibility package without uninstalling. Verify saved settings, notes, earlier history and one-time wizard migration. Also clean-install in a separate test profile.
2. With network unavailable, verify extracted models, wake variants, real male audio and local commands. Measure first extraction/warm-up rather than assuming two seconds.
3. Test normal wake interruption and Live Call naturally while a long cloud answer streams. Record first-token-to-audio, speech-onset-to-six-frame-detection, detection-to-audible-stop, stale-token cancellation and interrupted history separately. The configured 192 ms detector cannot meet a 150 ms onset-to-stop budget.
4. Measure close/1m/2m speech, quiet/noisy/TV rooms, headphones/speaker/Bluetooth, recognition corrections, false wakes, battery and thermal behavior. Adjust only against observed results; do not infer accuracy from a valid model file.
5. Test global Off during capture, speech, provider work, optional review, a check-in, learning, and Settings voice test. Confirm no old response or follow-up restarts.
6. Test permission denial/revocation, foreground microphone arming, lock/unlock/screen off, DND/snooze/overnight active hours and manufacturer power management. Recheck notices/check-ins independently.
7. Test compact/landscape/foldable/keyboard, real 2x fonts, TalkBack and floating captions while another app is visible. Capture actual screenshots as handset evidence.
8. Resolve third-party voice/runtime license obligations before commercial/store distribution. Do not relabel unmeasured acceptance as passed.

## Add next without removing existing features

Continue the canonical 22-step ordered plan. Step 9 still needs fuller reader actions, copy/share/citation presentation and long-history navigation; this build removes nested scrolling and adds inline expansion only. Subsequent scoped milestones cover reliable multi-step work, memory correction and richer knowledge, files/integrations, advanced voice/local-model routing and assistant/device research. Voice and progressive-setup work in this build are partial progress toward later steps 19/21, not completion of those milestones.

Every later build must improve a real UI/UX flow, preserve all 16 tools, direct matching-request execution, male selected voice, scheduled spoken companionship, manual alternatives and global Off, and record concrete checks and remaining physical gates.
