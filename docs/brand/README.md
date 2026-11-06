# ELLIOT README artwork

The README artwork follows the app's **Singularity** interface: deep space, smoked glass, and an aurora of cyan, violet, and magenta.

Every asset is a self-contained SVG animated with SMIL, so GitHub plays it inside the README. The files use no scripts, external fonts, or images, and each one includes a text title and description.

| Asset | Purpose |
| --- | --- |
| [Hero](singularity-hero.svg) | The ELLIOT wordmark beside the 3D Holo Core turning above its hologram projector. |
| [Holo Core states](holo-core-states.svg) | The six orb states, each a 3D particle form turning in place. |
| [Voice pipeline](voice-pipeline.svg) | Standard and optional calibrated audio routing, with a signal pulse running the pipeline. |
| [Speech modes](speech-modes.svg) | The three selectable Whisper language settings, with an accuracy-evaluation note. |
| [Signal](singularity-signal.svg) | A decorative aurora waveform used as a section divider. |

## Regenerating

All five files come from [generate_art.py](generate_art.py), which needs only Python 3:

```bash
python3 docs/brand/generate_art.py
```

The particle forms, projection, and colours mirror [HoloCoreRenderer.kt](../../app/src/main/java/com/eiliot/app/overlay/HoloCoreRenderer.kt) and [OrbState.kt](../../app/src/main/java/com/eiliot/app/overlay/OrbState.kt). If you change a form or a state colour in the app, update the generator so the README still matches the phone. Each animation loops seamlessly because rotation and every time-based deformation complete a whole number of cycles per loop.

## Screenshots

The screenshots under `docs/ui/singularity` are Compose renders from the Robolectric shell and overlay tests (`PremiumShellUiTest`, `OverlayUiTest`). Earlier renders are kept under `docs/ui/0.6.x`.
