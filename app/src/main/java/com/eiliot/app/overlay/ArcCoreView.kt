package com.eiliot.app.overlay

import android.content.Context
import android.graphics.Canvas
import android.provider.Settings
import android.view.Choreographer
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import kotlin.math.abs
import kotlin.math.exp

/**
 * The ELLIOT orb: a 3D "Holo Core" particle hologram, used in the app and as
 * the floating overlay above other apps. Rendering lives in [HoloCoreRenderer].
 *
 * Public contract (used by [OverlayManager] and the Compose screens):
 *  - [setState] / [getState]: six states, each a different 3D form; changing
 *    state morphs every particle from its current position.
 *  - [setAmplitude]: live audio level 0..1 for LISTENING / SPEAKING.
 *  - [setPresentation]: owner motion preference and haptics.
 *  - [setPaused]: battery hook; freezes the loop, last frame stays visible.
 *  - [setAmbient]: keep the idle hologram alive while it is the hero of a
 *    visible screen. The floating overlay leaves this off so it is calm.
 *  - [setInteractive]: let a finger spin the hologram with inertia. The
 *    overlay leaves this off because its own touch listener drags the window.
 *  - [setTilt]: device tilt -1..1 for parallax.
 *  - [setContrastDisc]: faint dark disc so the orb reads over light apps.
 *
 * Motion and power: as the overlay it drives itself with [Choreographer] at
 * ~30 fps, and stops when detached, hidden, paused, calm, or when motion is
 * reduced (system animator scale 0 or the owner's "reduced" choice). In
 * ambient mode the owner supplies frames through [advanceFrame] instead.
 * Reduced motion snaps state changes and draws a single still frame; a
 * finger can still turn it, without inertia.
 */
class ArcCoreView(context: Context) : View(context) {

    private val renderer = HoloCoreRenderer()
    private val frame = HoloCoreRenderer.Frame()

    private var state: OrbState = OrbState.IDLE
    private var prevState: OrbState = OrbState.IDLE
    private var mix = 1f
    private var ampTarget = 0f
    private var ampSmooth = 0f
    private var timeSec = 0f

    private var attached = false
    private var running = false
    private var paused = false
    private var reducedMotion = false
    private var ownerMotion = "system"
    private var ownerHaptics = true
    private var ambient = false
    private var interactive = false
    private var lastFrameNanos = 0L
    private var lastPulseBucket = -1

    // Spin (radians) and its inertia (radians per second).
    private var spinYaw = 0f
    private var spinPitch = 0f
    private var velYaw = 0f
    private var velPitch = 0f
    private var touching = false
    private var velocity: VelocityTracker? = null
    private var lastX = 0f
    private var lastY = 0f
    private var travel = 0f

    // Device tilt parallax.
    private var tiltTargetX = 0f
    private var tiltTargetY = 0f
    private var tiltX = 0f
    private var tiltY = 0f

    init {
        updateContentDescription()
    }

    // ------------------------------------------------------------------ API

    fun setState(newState: OrbState) {
        if (newState == state) return
        prevState = if (mix < 0.5f) prevState else state
        state = newState
        refreshReducedMotion()
        renderer.snapshot()
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

    fun setAmplitude(level: Float) {
        ampTarget = level.coerceIn(0f, 1f)
        if (reducedMotion) {
            ampSmooth = 0f
            invalidate()
        } else {
            syncLoop()
        }
    }

    fun setPaused(p: Boolean) {
        if (paused == p) return
        paused = p
        syncLoop()
    }

    /**
     * Ambient mode hands the clock to the owner: the view stops scheduling its
     * own frames and animates only when [advanceFrame] is called. Compose
     * screens drive it from an infinite-animation frame clock, which follows
     * the platform's animation policy (tests and tooling can suspend it).
     */
    fun setAmbient(on: Boolean) {
        if (ambient == on) return
        ambient = on
        lastFrameNanos = 0L
        syncLoop()
    }

    /** One owner-driven frame in ambient mode. Ignored when paused, hidden or motion is reduced. */
    fun advanceFrame(frameTimeNanos: Long) {
        if (!ambient || !attached || paused || reducedMotion || !isShown) { lastFrameNanos = 0L; return }
        val dt = if (lastFrameNanos == 0L) 0.016f else ((frameTimeNanos - lastFrameNanos) / 1e9f).coerceIn(0f, 0.1f)
        lastFrameNanos = frameTimeNanos
        advance(dt)
        invalidate()
    }

    fun setInteractive(on: Boolean) {
        interactive = on
        isClickable = on || isClickable
    }

    fun setTilt(x: Float, y: Float) {
        tiltTargetX = x.coerceIn(-1f, 1f)
        tiltTargetY = y.coerceIn(-1f, 1f)
        if (reducedMotion) { tiltX = 0f; tiltY = 0f }
    }

    fun setContrastDisc(on: Boolean) {
        frame.contrastDisc = on
        invalidate()
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
        velocity?.recycle(); velocity = null
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

    // ----------------------------------------------------------------- touch

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!interactive || !isEnabled) return super.onTouchEvent(event)
        val size = maxOf(1, minOf(width, height)).toFloat()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                velocity?.recycle()
                velocity = VelocityTracker.obtain().also { it.addMovement(event) }
                lastX = event.x; lastY = event.y; travel = 0f
                touching = true; velYaw = 0f; velPitch = 0f
                syncLoop()
            }
            MotionEvent.ACTION_MOVE -> {
                velocity?.addMovement(event)
                val dx = event.x - lastX
                val dy = event.y - lastY
                lastX = event.x; lastY = event.y
                travel += abs(dx) + abs(dy)
                spinYaw += dx / size * SPIN_PER_WIDTH
                spinPitch = (spinPitch + dy / size * SPIN_PER_WIDTH * 0.6f).coerceIn(-MAX_PITCH, MAX_PITCH)
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                touching = false
                velocity?.let { v ->
                    v.addMovement(event); v.computeCurrentVelocity(1000)
                    if (!reducedMotion) {
                        velYaw = (v.xVelocity / size * SPIN_PER_WIDTH).coerceIn(-MAX_SPIN, MAX_SPIN)
                        velPitch = (v.yVelocity / size * SPIN_PER_WIDTH * 0.6f).coerceIn(-MAX_SPIN, MAX_SPIN)
                    }
                    v.recycle()
                }
                velocity = null
                if (travel < TAP_SLOP * resources.displayMetrics.density) performClick()
                else if (abs(velYaw) > 4f && ownerHaptics) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                syncLoop()
            }
            MotionEvent.ACTION_CANCEL -> {
                touching = false
                velocity?.recycle(); velocity = null
                syncLoop()
            }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    // -------------------------------------------------------------- frame loop

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            val dt = if (lastFrameNanos == 0L) 0.016f else ((frameTimeNanos - lastFrameNanos) / 1e9f).coerceIn(0f, 0.1f)
            lastFrameNanos = frameTimeNanos
            advance(dt)
            syncLoop()
            invalidate()
            scheduleNext()
        }
    }

    private fun scheduleNext() {
        if (!running) return
        // ~30 fps keeps the floating overlay light; the in-app hologram is ambient and owner-driven.
        Choreographer.getInstance().postFrameCallbackDelayed(frameCallback, 32L)
    }

    private fun spinning() = abs(velYaw) > 0.02f || abs(velPitch) > 0.02f || abs(spinPitch) > 0.01f

    private fun syncLoop() {
        val shouldRun = attached && isShown && !paused && !reducedMotion && !ambient &&
            (ownerMotion == "expressive" || state != OrbState.IDLE || mix < 1f ||
                touching || spinning() || ampTarget > 0.02f)
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
        if (mix < 1f) mix = (mix + dt / TRANSITION_SECONDS).coerceAtMost(1f)
        ampSmooth += (ampTarget - ampSmooth) * (dt * AMP_SMOOTH_RATE).coerceAtMost(1f)

        if (!touching) {
            spinYaw += velYaw * dt
            spinPitch = (spinPitch + velPitch * dt).coerceIn(-MAX_PITCH, MAX_PITCH)
            val decay = exp(-SPIN_DECAY * dt)
            velYaw *= decay; velPitch *= decay
            // Pitch eases home so the hologram always settles upright.
            spinPitch += (0f - spinPitch) * (dt * 1.6f).coerceAtMost(1f)
            if (abs(velYaw) < 0.02f) velYaw = 0f
            if (abs(velPitch) < 0.02f) velPitch = 0f
        }
        if (spinYaw > 1000f || spinYaw < -1000f) spinYaw %= TAU
        tiltX += (tiltTargetX - tiltX) * (dt * 4f).coerceAtMost(1f)
        tiltY += (tiltTargetY - tiltY) * (dt * 4f).coerceAtMost(1f)

        if (state == OrbState.ALERT) {
            val bucket = (timeSec * 0.8f).toInt()
            if (bucket != lastPulseBucket) {
                lastPulseBucket = bucket
                if (ownerHaptics) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
        }
        fillFrame()
        renderer.step(dt, frame)
    }

    private fun fillFrame() {
        frame.time = timeSec
        frame.state = state
        frame.prev = prevState
        frame.mix = mix
        frame.amp = ampSmooth
        frame.yaw = spinYaw + tiltX * TILT_YAW
        frame.pitch = spinPitch + tiltY * TILT_PITCH
    }

    // ---------------------------------------------------------------- drawing

    override fun onDraw(canvas: Canvas) {
        if (width <= 0 || height <= 0) return
        fillFrame()
        renderer.draw(canvas, width, height, frame)
    }

    // ---------------------------------------------------------------- helpers

    private fun refreshReducedMotion() {
        reducedMotion = ownerMotion == "reduced" || try {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        } catch (e: Exception) {
            false
        }
    }

    private fun updateContentDescription() {
        contentDescription = "ELLIOT orb, ${state.name.lowercase()}"
    }

    internal fun isAnimating() = running

    private companion object {
        const val TRANSITION_SECONDS = 0.9f
        const val AMP_SMOOTH_RATE = 14f
        const val TIME_WRAP_SECONDS = 36000f
        const val TAU = 6.2831855f
        const val SPIN_PER_WIDTH = 3.6f
        const val MAX_SPIN = 14f
        const val MAX_PITCH = 0.9f
        const val SPIN_DECAY = 1.6f
        const val TAP_SLOP = 8f
        const val TILT_YAW = 0.45f
        const val TILT_PITCH = 0.30f
    }
}
