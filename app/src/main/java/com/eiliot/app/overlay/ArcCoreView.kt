package com.eiliot.app.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.provider.Settings
import android.view.Choreographer
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.annotation.RequiresApi

/**
 * The ELLIOT "Arc Core" — the always-present hover orb rendered over every
 * screen via [android.view.WindowManager]. JARVIS-grade arc-reactor visuals:
 * a procedural energy core, segmented arc ring, HUD ticks, particle fields,
 * and six fully-articulated states ([OrbState]) that crossfade on the GPU.
 *
 * Rendering:
 *  - API 33+ (the Android 16 target): one master AGSL [RuntimeShader] draws
 *    everything, including state crossfades, in a single full-view pass.
 *  - API 26–32 (or a non-hardware-accelerated canvas): [ArcCoreCanvasRenderer]
 *    reproduces the same scene with gradients/dashes/particles.
 *
 * Motion & power:
 *  - Driven by [Choreographer] (vsync). No [android.animation.ValueAnimator]
 *    busy-loop; when calm (IDLE, settled, silent) it drops to ~30 fps.
 *  - Stops entirely when detached, hidden, [setPaused] (screen-off hook), or
 *    when the system animator scale is 0 (reduced-motion tier: states snap and
 *    a single static composed frame is drawn).
 *
 * Haptics: entering [OrbState.ALERT] fires a LONG_PRESS tick, then CLOCK_TICK
 * pulses stay synced to the radar pulse-ring cycle (respects system haptics).
 *
 * Public contract (used by the service layer / [OverlayManager]):
 *  - [setState] / [getState] — drive the six orb states.
 *  - [setAmplitude] — live audio level 0..1 for LISTENING/SPEAKING reactivity.
 *  - [setPaused] — battery hook; freezes the loop until resumed.
 */
class ArcCoreView(context: Context) : View(context) {

    private var state: OrbState = OrbState.IDLE
    private var prevState: OrbState = OrbState.IDLE

    /** 0 = fully previous state, 1 = fully current. Snaps to 1 in reduced motion. */
    private var mix = 1f

    private var ampTarget = 0f
    private var ampSmooth = 0f

    private var timeSec = 0f
    private var attached = false
    private var running = false
    private var paused = false
    private var calm = true
    private var reducedMotion = false
    private var ownerMotion = "system"
    private var ownerHaptics = true
    private var lastFrameNanos = 0L
    private var lastPulseBucket = -1

    /** Null on API < 33, on shader-compile failure, or when unusable. */
    private val shader: Shader? = if (USE_GPU_SHADER) ArcCoreShaders.createMasterShader() else null
    private val shaderPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        updateContentDescription()
    }

    // ------------------------------------------------------------------ API

    /**
     * Transition the orb to [newState] with a ~450 ms GPU crossfade.
     * Safe to call from any state, mid-transition, or before the view is
     * attached. Default state on attach is [OrbState.IDLE].
     */
    fun setState(newState: OrbState) {
        if (newState == state) return
        // Keep the visually dominant side as the crossfade source so an
        // interrupted transition never pops.
        prevState = if (mix < 0.5f) prevState else state
        state = newState
        refreshReducedMotion()
        mix = if (reducedMotion) 1f else 0f
        if (newState == OrbState.ALERT) {
            lastPulseBucket = -1
            if (ownerHaptics) performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
        updateContentDescription()
        syncLoop()
        invalidate()
    }

    fun getState(): OrbState = state

    fun setPresentation(motion: String, haptics: Boolean) {
        ownerMotion = motion; ownerHaptics = haptics; isHapticFeedbackEnabled = haptics
        refreshReducedMotion()
        if (reducedMotion) mix = 1f
        syncLoop(); invalidate()
    }

    /**
     * Live audio amplitude (TTS for SPEAKING, mic for LISTENING), 0..1.
     * Smoothed per-frame internally so callers may feed raw RMS values.
     */
    fun setAmplitude(level: Float) {
        ampTarget = level.coerceIn(0f, 1f)
        if (reducedMotion) {
            ampSmooth = 0f
            invalidate()
        }
    }

    /**
     * Battery hook for the service layer: pause the animation loop (screen
     * off, doze, user-requested quiet). The last frame remains on screen.
     */
    fun setPaused(p: Boolean) {
        if (paused == p) return
        paused = p
        syncLoop()
    }

    // ------------------------------------------------------------- lifecycle

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attached = true
        refreshReducedMotion()
        if (reducedMotion) mix = 1f
        syncLoop()
        invalidate()
    }

    override fun onDetachedFromWindow() {
        attached = false
        syncLoop()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        syncLoop()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        syncLoop()
    }

    // -------------------------------------------------------------- frame loop

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            val dt = if (lastFrameNanos == 0L) {
                0.016f
            } else {
                ((frameTimeNanos - lastFrameNanos) / 1e9f).coerceIn(0f, 0.1f)
            }
            lastFrameNanos = frameTimeNanos
            advance(dt)
            syncLoop()
            invalidate()
            scheduleNext()
        }
    }

    private fun scheduleNext() {
        if (!running) return
        // Cap to ~30 fps everywhere — much lighter on mid-range GPUs, still smooth.
        Choreographer.getInstance().postFrameCallbackDelayed(frameCallback, 32L)
    }

    private fun syncLoop() {
        val shouldRun = attached && isShown && !paused && !reducedMotion &&
            (ownerMotion == "expressive" || state != OrbState.IDLE || mix < 1f)
        if (shouldRun && !running) {
            running = true
            lastFrameNanos = 0L
            scheduleNext()
        } else if (!shouldRun && running) {
            running = false
            Choreographer.getInstance().removeFrameCallback(frameCallback)
        }
    }

    private fun advance(dt: Float) {
        timeSec += dt
        if (timeSec > TIME_WRAP_SECONDS) timeSec -= TIME_WRAP_SECONDS

        if (mix < 1f) {
            mix = (mix + dt / TRANSITION_SECONDS).coerceAtMost(1f)
        }
        ampSmooth += (ampTarget - ampSmooth) * (dt * AMP_SMOOTH_RATE).coerceAtMost(1f)

        if (state == OrbState.ALERT) {
            // Haptic tick synced to the radar pulse-ring cycle (1.25 s).
            val bucket = (timeSec * 0.8f).toInt()
            if (bucket != lastPulseBucket) {
                lastPulseBucket = bucket
                if (ownerHaptics) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
        }

        calm = state == OrbState.IDLE && mix >= 1f && ampTarget < 0.02f
    }

    // ---------------------------------------------------------------- drawing

    override fun onDraw(canvas: Canvas) {
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return
        if (USE_GPU_SHADER && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            shader != null && canvas.isHardwareAccelerated
        ) {
            pushUniforms()
            shaderPaint.shader = shader
            canvas.drawPaint(shaderPaint)
        } else {
            ArcCoreCanvasRenderer.draw(
                canvas, w, h, timeSec, state, prevState, mix, ampSmooth,
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun pushUniforms() {
        val rs = shader as? RuntimeShader ?: return
        rs.setFloatUniform("uSize", width.toFloat(), height.toFloat())
        rs.setFloatUniform("uTime", timeSec)
        rs.setFloatUniform("uAmp", ampSmooth)
        rs.setIntUniform("uMode", state.ordinal)
        rs.setIntUniform("uPrevMode", prevState.ordinal)
        rs.setFloatUniform("uMix", mix)
    }

    // ---------------------------------------------------------------- helpers

    private fun refreshReducedMotion() {
        reducedMotion = ownerMotion == "reduced" || try {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
        } catch (e: Exception) {
            false
        }
    }

    private fun updateContentDescription() {
        contentDescription = "ELLIOT Arc Core — ${state.name.lowercase()}"
    }

    internal fun isAnimating() = running

    private companion object {
        /** Use the per-pixel AGSL shader? Off = cheap Canvas renderer (far lighter). */
        const val USE_GPU_SHADER = false

        /** State crossfade duration. */
        const val TRANSITION_SECONDS = 0.45f

        /** First-order smoothing rate for the audio amplitude envelope. */
        const val AMP_SMOOTH_RATE = 14f

        /** Wrap the clock so fp32 trig stays precise across multi-hour uptime. */
        const val TIME_WRAP_SECONDS = 36000f
    }
}
