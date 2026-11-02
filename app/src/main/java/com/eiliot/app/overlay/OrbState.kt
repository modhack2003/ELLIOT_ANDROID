package com.eiliot.app.overlay

import android.graphics.Color

/**
 * The six visual states of the ELLIOT "Arc Core" hover orb, per the design
 * language in ELLIOT_SYSTEM_DESIGN.md §4.1:
 *
 *  - [IDLE]      — slow breathing glow, rotating HUD ticks, gentle particle swirl.
 *  - [LISTENING] — ring dilates, waveform bars orbit the core, cyan-white shift.
 *  - [THINKING]  — particles spiral inward (data ingestion), spinning reticle.
 *  - [SPEAKING]  — audio-reactive ripple rings driven by TTS amplitude.
 *  - [ALERT]     — amber/red pulse + radar sweep + haptic tick.
 *  - [LEARNING]  — slow constellation, nodes connecting as knowledge is absorbed.
 *
 * [ordinal] doubles as the mode index fed to the AGSL master shader, so the
 * declaration order here is a rendering contract — do not reorder entries.
 *
 * [baseColor] / [hotColor] are the palette used by the Canvas fallback
 * renderer (API 26–32). The AGSL shader carries the same palette inline.
 */
enum class OrbState(
    val baseColor: Int,
    val hotColor: Int,
) {
    IDLE(
        baseColor = Color.parseColor("#00E5FF"),
        hotColor = Color.parseColor("#FFFFFF"),
    ),
    LISTENING(
        baseColor = Color.parseColor("#8EF6FF"),
        hotColor = Color.parseColor("#FFFFFF"),
    ),
    THINKING(
        baseColor = Color.parseColor("#7C8CFF"),
        hotColor = Color.parseColor("#E8ECFF"),
    ),
    SPEAKING(
        baseColor = Color.parseColor("#3EF2D8"),
        hotColor = Color.parseColor("#FFFFFF"),
    ),
    ALERT(
        baseColor = Color.parseColor("#FF5A2A"),
        hotColor = Color.parseColor("#FFD54F"),
    ),
    LEARNING(
        baseColor = Color.parseColor("#A78BFF"),
        hotColor = Color.parseColor("#E6DEFF"),
    ),
}
