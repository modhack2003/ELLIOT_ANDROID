package com.eiliot.app.overlay

/**
 * The six visual states of the ELLIOT Holo Core orb. Each state has its own
 * 3D form, and [HoloCoreRenderer] morphs every particle from one form to the
 * next:
 *
 *  - [IDLE]      geodesic sphere, slow rotation, gyroscope rings drifting.
 *  - [LISTENING] surface waves driven by the microphone, rings stack into halos.
 *  - [THINKING]  the sphere folds into a torus with data flowing through it.
 *  - [SPEAKING]  latitude rings pulse outward with the voice amplitude.
 *  - [ALERT]     a spiked star with jitter, dashed rings and a haptic tick.
 *  - [LEARNING]  a turning double helix with rungs connecting as it learns.
 *
 * Declaration order is used for per-state lookup tables, so do not reorder.
 *
 * [baseColor] tints the particles, [hotColor] is the near-camera highlight and
 * core, and [rimColor] lights the gyroscope rings and lens flare.
 */
enum class OrbState(
    val baseColor: Int,
    val hotColor: Int,
    val rimColor: Int,
) {
    IDLE(
        baseColor = 0xFF00E5FF.toInt(),
        hotColor = 0xFFF2FEFF.toInt(),
        rimColor = 0xFF8C7DFF.toInt(),
    ),
    LISTENING(
        baseColor = 0xFF8EF6FF.toInt(),
        hotColor = 0xFFFFFFFF.toInt(),
        rimColor = 0xFF00E5FF.toInt(),
    ),
    THINKING(
        baseColor = 0xFF8C7DFF.toInt(),
        hotColor = 0xFFEDEAFF.toInt(),
        rimColor = 0xFFFF4FD8.toInt(),
    ),
    SPEAKING(
        baseColor = 0xFF3EF2D8.toInt(),
        hotColor = 0xFFFFFFFF.toInt(),
        rimColor = 0xFF00E5FF.toInt(),
    ),
    ALERT(
        baseColor = 0xFFFF5A2A.toInt(),
        hotColor = 0xFFFFE08A.toInt(),
        rimColor = 0xFFFFB54F.toInt(),
    ),
    LEARNING(
        baseColor = 0xFFB48CFF.toInt(),
        hotColor = 0xFFF3EDFF.toInt(),
        rimColor = 0xFFFF4FD8.toInt(),
    ),
}
