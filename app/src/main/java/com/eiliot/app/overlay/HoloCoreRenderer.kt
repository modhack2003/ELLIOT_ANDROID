package com.eiliot.app.overlay

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The Holo Core: a real-time 3D particle hologram drawn with plain Canvas
 * primitives, so it runs the same on API 26 and API 36 without shaders.
 *
 * Scene, back to front:
 *  1. an optional dark contrast disc and a soft halo,
 *  2. the far half of the particle cloud and the far arcs of three gyroscope
 *     rings,
 *  3. the plasma core with a white singularity,
 *  4. the near half of the cloud and the near arcs of the rings, with beads
 *     orbiting on each ring,
 *  5. an anamorphic lens streak.
 *
 * Every state in [OrbState] is a different 3D form (sphere, wave sphere,
 * torus, banded voice shell, spiked star, double helix). Changing state
 * captures each particle's current position and flies it to its new form on a
 * staggered, arcing path, so transitions never pop, even mid-morph.
 *
 * Depth is real: points are rotated in 3D, perspective-projected, and shaded
 * by distance to the camera (size, brightness and a near-camera colour shift).
 *
 * The renderer owns no Android lifecycle; [ArcCoreView] drives it. Per-frame
 * work allocates nothing except gradients whose colour or radius changed.
 */
internal class HoloCoreRenderer {

    /** Inputs for one frame. Mutated in place by the view. */
    class Frame {
        var time = 0f
        var state = OrbState.IDLE
        var prev = OrbState.IDLE
        /** 0 = previous form, 1 = current form. */
        var mix = 1f
        var amp = 0f
        /** User spin (radians) and device tilt, already smoothed. */
        var yaw = 0f
        var pitch = 0f
        var contrastDisc = true
    }

    private val from = FloatArray(N * 3)
    private val cur = FloatArray(N * 3)
    private val tmp = FloatArray(3)
    private val stagger = FloatArray(N) { hash(it.toFloat(), 3.7f) }
    private val seed = FloatArray(N) { hash(it.toFloat(), 8.1f) }

    // Projected point cloud.
    private val px = FloatArray(N)
    private val py = FloatArray(N)
    private val pf = FloatArray(N) // 0 = far, 1 = near
    private val ps = FloatArray(N) // perspective scale

    // Projected rings: RINGS x (SEG + 1).
    private val rx = Array(RINGS) { FloatArray(SEG + 1) }
    private val ry = Array(RINGS) { FloatArray(SEG + 1) }
    private val rf = Array(RINGS) { FloatArray(SEG + 1) }
    private val ringPhase = FloatArray(RINGS) { it * 2.1f }
    private var spinPhase = 0f

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val shaded = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val halo = GradientCache()
    private val core = GradientCache()
    private val disc = GradientCache()
    private val streak = GradientCache()

    init {
        shape(OrbState.IDLE, 0f, 0f, cur)
        cur.copyInto(from)
    }

    /** Freeze the visible positions so the next morph starts from them. */
    fun snapshot() {
        cur.copyInto(from)
    }

    /** Advance phase accumulators so speed changes never jump. */
    fun step(dt: Float, f: Frame) {
        val m = ease(f.mix)
        for (k in 0 until RINGS) {
            val speed = lerp(RING[f.prev.ordinal][k][SPEED], RING[f.state.ordinal][k][SPEED], m)
            ringPhase[k] = wrap(ringPhase[k] + speed * dt)
        }
        val spin = lerp(SPIN[f.prev.ordinal], SPIN[f.state.ordinal], m)
        spinPhase = wrap(spinPhase + spin * dt)
    }

    fun draw(canvas: Canvas, w: Int, h: Int, f: Frame) {
        val cx = w / 2f
        val cy = h / 2f
        val half = min(cx, cy)
        val radius = half * 0.48f
        val dot = (radius / 70f).coerceIn(0.55f, 2.4f)
        val m = ease(f.mix)
        val base = lerpColor(f.prev.baseColor, f.state.baseColor, m)
        val hot = lerpColor(f.prev.hotColor, f.state.hotColor, m)
        val rim = lerpColor(f.prev.rimColor, f.state.rimColor, m)
        val breathe = 0.5f + 0.5f * sin(f.time * 1.3f)

        val yaw = spinPhase + f.yaw
        val pitch = BASE_PITCH + f.pitch
        val cyaw = cos(yaw)
        val syaw = sin(yaw)
        val cp = cos(pitch)
        val sp = sin(pitch)

        // ---- particle cloud: morph, rotate, project
        for (i in 0 until N) {
            shapePoint(f.state, i, f.time, f.amp, tmp)
            var x = tmp[0]
            var y = tmp[1]
            var z = tmp[2]
            if (f.mix < 1f) {
                val e = ease(((f.mix - stagger[i] * 0.35f) / 0.65f).coerceIn(0f, 1f))
                val o = i * 3
                x = lerp(from[o], x, e)
                y = lerp(from[o + 1], y, e)
                z = lerp(from[o + 2], z, e)
                // Arc outward mid-flight so particles visibly travel.
                val lift = 1f + sin(e * PI.toFloat()) * (0.22f + 0.18f * seed[i])
                x *= lift; y *= lift; z *= lift
            }
            val o = i * 3
            cur[o] = x; cur[o + 1] = y; cur[o + 2] = z

            val x1 = x * cyaw + z * syaw
            val z1 = -x * syaw + z * cyaw
            val y2 = y * cp - z1 * sp
            val z2 = y * sp + z1 * cp
            val s = CAMERA / (CAMERA - z2)
            px[i] = cx + x1 * s * radius
            py[i] = cy - y2 * s * radius
            ps[i] = s
            pf[i] = ((z2 / 1.25f + 1f) * 0.5f).coerceIn(0f, 1f)
        }

        // ---- gyroscope rings: rotate, project
        val pulse = f.amp * (if (f.state == OrbState.LISTENING || f.state == OrbState.SPEAKING) 1f else 0.25f)
        for (k in 0 until RINGS) {
            val a = RING[f.prev.ordinal][k]
            val b = RING[f.state.ordinal][k]
            val tiltX = lerp(a[TILT_X], b[TILT_X], m)
            val tiltZ = lerp(a[TILT_Z], b[TILT_Z], m)
            val rr = lerp(a[RADIUS], b[RADIUS], m) * (1f + 0.10f * pulse * (k + 1)) + 0.015f * breathe
            val ctx = cos(tiltX); val stx = sin(tiltX)
            val ctz = cos(tiltZ); val stz = sin(tiltZ)
            val ry0 = ringPhase[k] + f.yaw * 0.6f
            val cry = cos(ry0); val sry = sin(ry0)
            for (j in 0..SEG) {
                val ang = j * TAU / SEG
                // Ring in its own plane (XZ), spun about its axis.
                var x = rr * cos(ang)
                var y = 0f
                var z = rr * sin(ang)
                val xs = x * cry + z * sry
                z = -x * sry + z * cry
                x = xs
                // Tilt about X, then Z.
                val y1 = y * ctx - z * stx
                val z1 = y * stx + z * ctx
                y = y1; z = z1
                val x2 = x * ctz - y * stz
                val y2 = x * stz + y * ctz
                x = x2; y = y2
                // Global pitch.
                val y3 = y * cp - z * sp
                val z3 = y * sp + z * cp
                val s = CAMERA / (CAMERA - z3)
                rx[k][j] = cx + x * s * radius
                ry[k][j] = cy - y3 * s * radius
                rf[k][j] = ((z3 / 1.5f + 1f) * 0.5f).coerceIn(0f, 1f)
            }
        }

        // ---- 1. contrast disc and halo
        if (f.contrastDisc) {
            fill.shader = disc.radial(cx, cy, half * 0.98f, Color.argb(110, 0, 0, 0), Color.argb(0, 0, 0, 0), 0.62f)
            canvas.drawCircle(cx, cy, half * 0.98f, fill)
        }
        val haloR = radius * (2.0f + 0.15f * breathe + 0.35f * pulse)
        fill.shader = halo.radial(cx, cy, haloR, withA(base, 0.20f), withA(base, 0f), 0.05f)
        canvas.drawCircle(cx, cy, haloR, fill)
        fill.shader = null

        // ---- 2. far side
        drawRings(canvas, f, rim, hot, dot, near = false)
        drawLinks(canvas, f, base, dot, near = false)
        drawPoints(canvas, base, hot, dot, near = false)

        // ---- 3. plasma core
        val speakBoost = if (f.state == OrbState.SPEAKING) f.amp * 0.35f * m else 0f
        val coreR = radius * (0.62f + 0.08f * breathe + speakBoost)
        fill.shader = core.radial(cx, cy, coreR, withA(hot, 0.95f), withA(base, 0f), 0.10f, mid = withA(base, 0.55f))
        canvas.drawCircle(cx, cy, coreR, fill)
        fill.shader = null
        fill.color = withA(Color.WHITE, 0.85f)
        canvas.drawCircle(cx, cy, radius * (0.075f + 0.02f * breathe), fill)

        // ---- 4. near side
        drawLinks(canvas, f, base, dot, near = true)
        drawPoints(canvas, base, hot, dot, near = true)
        drawRings(canvas, f, rim, hot, dot, near = true)

        // ---- 5. anamorphic lens streak
        val sw = radius * 1.9f
        val sh = max(1.5f, radius * 0.022f)
        shaded.shader = streak.linear(cx - sw, cx + sw, withA(rim, 0f), withA(hot, 0.55f + 0.25f * breathe))
        canvas.drawRect(cx - sw, cy - sh, cx + sw, cy + sh, shaded)
        shaded.shader = null
    }

    private fun drawPoints(canvas: Canvas, base: Int, hot: Int, dot: Float, near: Boolean) {
        for (i in 0 until N) {
            val d = pf[i]
            if ((d >= 0.5f) != near) continue
            val lit = d.pow(1.4f)
            val r = (0.9f + 2.1f * d) * ps[i] * dot
            val c = lerpColor(base, hot, d * d * d * 0.85f)
            if (near && d > 0.72f) {
                fill.color = withA(c, 0.12f * lit)
                canvas.drawCircle(px[i], py[i], r * 3.2f, fill)
            }
            fill.color = withA(c, 0.16f + 0.84f * lit)
            canvas.drawCircle(px[i], py[i], r, fill)
        }
    }

    /** Helix rungs (LEARNING) and torus flow lines (THINKING), weighted by the morph. */
    private fun drawLinks(canvas: Canvas, f: Frame, base: Int, dot: Float, near: Boolean) {
        val m = ease(f.mix)
        val helix = weight(f, OrbState.LEARNING, m)
        if (helix > 0.01f) {
            line.strokeWidth = 1.1f * dot
            var i = 0
            while (i + 1 < N) {
                if ((i / 2) % 3 == 0) {
                    val d = (pf[i] + pf[i + 1]) * 0.5f
                    if ((d >= 0.5f) == near) {
                        line.color = withA(base, helix * (0.10f + 0.45f * d))
                        canvas.drawLine(px[i], py[i], px[i + 1], py[i + 1], line)
                    }
                }
                i += 2
            }
        }
        val torus = weight(f, OrbState.THINKING, m)
        if (torus > 0.01f) {
            line.strokeWidth = 0.9f * dot
            for (i in 0 until N - 7 step 7) {
                val d = (pf[i] + pf[i + 7]) * 0.5f
                if ((d >= 0.5f) != near) continue
                line.color = withA(base, torus * (0.06f + 0.30f * d))
                canvas.drawLine(px[i], py[i], px[i + 7], py[i + 7], line)
            }
        }
    }

    private fun drawRings(canvas: Canvas, f: Frame, rim: Int, hot: Int, dot: Float, near: Boolean) {
        val m = ease(f.mix)
        val dashed = weight(f, OrbState.ALERT, m) > 0.5f
        for (k in 0 until RINGS) {
            val alpha = lerp(RING[f.prev.ordinal][k][ALPHA], RING[f.state.ordinal][k][ALPHA], m)
            if (alpha < 0.01f) continue
            for (j in 0 until SEG) {
                if (dashed && (j / 3) % 2 == 1) continue
                val d = (rf[k][j] + rf[k][j + 1]) * 0.5f
                if ((d >= 0.5f) != near) continue
                line.strokeWidth = (0.7f + 1.7f * d) * dot
                line.color = withA(lerpColor(rim, hot, d * d * 0.6f), alpha * (0.12f + 0.88f * d))
                canvas.drawLine(rx[k][j], ry[k][j], rx[k][j + 1], ry[k][j + 1], line)
            }
            // A bead with a fading trail rides each ring.
            for (tIndex in 0 until 7) {
                val j = (k * 23 + SEG - tIndex * 2) % SEG
                val d = rf[k][j]
                if ((d >= 0.5f) != near) continue
                val fade = 1f - tIndex / 7f
                fill.color = withA(if (tIndex == 0) hot else rim, alpha * fade * (0.25f + 0.75f * d))
                canvas.drawCircle(rx[k][j], ry[k][j], (1.4f + 2.6f * d) * dot * (if (tIndex == 0) 1.15f else fade), fill)
            }
        }
    }

    // ------------------------------------------------------------------ forms

    private fun shape(state: OrbState, t: Float, amp: Float, out: FloatArray) {
        for (i in 0 until N) {
            shapePoint(state, i, t, amp, tmp)
            out[i * 3] = tmp[0]; out[i * 3 + 1] = tmp[1]; out[i * 3 + 2] = tmp[2]
        }
    }

    private fun shapePoint(state: OrbState, i: Int, t: Float, amp: Float, out: FloatArray) {
        when (state) {
            OrbState.IDLE -> {
                sphere(i, out)
                val r = 1f + 0.03f * sin(t * 1.2f + out[1] * 3.4f + seed[i] * 2f)
                scale(out, r)
            }
            OrbState.LISTENING -> {
                sphere(i, out)
                val wave = sin(out[1] * 7f - t * 6.5f)
                val sparkle = sin(t * 11f + seed[i] * 40f)
                scale(out, 1f + (0.05f + 0.24f * amp) * wave + 0.06f * amp * sparkle)
            }
            OrbState.THINKING -> {
                val u = TAU * frac(i * GOLDEN_FRACTION) + t * 0.55f
                val v = TAU * frac(i * PLASTIC_FRACTION) - t * 1.4f
                val ring = 0.80f + 0.30f * cos(v)
                val x = ring * cos(u)
                val z = ring * sin(u)
                val y = 0.30f * sin(v)
                // Lean the torus so it always reads as a ring, not a line.
                out[0] = x
                out[1] = y * TORUS_COS - z * TORUS_SIN
                out[2] = y * TORUS_SIN + z * TORUS_COS
            }
            OrbState.SPEAKING -> {
                val band = i * BANDS / N
                val start = band * N / BANDS
                val count = ((band + 1) * N / BANDS - start).coerceAtLeast(1)
                val k = i - start
                val lat = PI.toFloat() * (band + 0.5f) / BANDS - PI.toFloat() / 2f
                val dir = if (band % 2 == 0) 1f else -1f
                val lon = TAU * k / count + band * 0.37f + t * 0.45f * dir
                val wave = max(0f, sin(t * 8f - band * 0.9f))
                val r = 1f + 0.04f + amp * 0.38f * wave
                out[0] = cos(lat) * cos(lon) * r
                out[1] = sin(lat) * r
                out[2] = cos(lat) * sin(lon) * r
            }
            OrbState.ALERT -> {
                sphere(i, out)
                val peak = max(abs(out[0]), max(abs(out[1]), abs(out[2])))
                val spike = 0.88f + 0.55f * peak.pow(9f)
                val jitter = 0.035f * sin(t * 31f + seed[i] * 50f)
                val throb = 1f + 0.06f * sin(t * 9f)
                scale(out, (spike + jitter) * throb)
            }
            OrbState.LEARNING -> {
                val pair = i / 2
                val strand = i % 2
                val pairs = N / 2
                val y = -1.18f + 2.36f * pair / (pairs - 1)
                val a = pair * 0.43f + strand * PI.toFloat() + t * 0.75f
                val r = 0.50f + 0.04f * sin(t * 2f + pair * 0.3f)
                out[0] = r * cos(a)
                out[1] = y
                out[2] = r * sin(a)
            }
        }
    }

    /** Fibonacci sphere: evenly spread unit vectors. */
    private fun sphere(i: Int, out: FloatArray) {
        val y = 1f - 2f * (i + 0.5f) / N
        val r = sqrt(max(0f, 1f - y * y))
        val th = i * GOLDEN_ANGLE
        out[0] = cos(th) * r
        out[1] = y
        out[2] = sin(th) * r
    }

    private fun scale(v: FloatArray, s: Float) {
        v[0] *= s; v[1] *= s; v[2] *= s
    }

    private fun weight(f: Frame, s: OrbState, m: Float) =
        (if (f.state == s) m else 0f) + (if (f.prev == s) 1f - m else 0f)

    // ---------------------------------------------------------------- gradients

    /** Reuses a gradient until its colours or geometry change. */
    private class GradientCache {
        private var key = 0L
        private var shader: Shader? = null

        fun radial(cx: Float, cy: Float, r: Float, inner: Int, outer: Int, start: Float, mid: Int? = null): Shader {
            val k = hashKey(cx, cy, r, inner, outer, mid ?: 1)
            shader?.let { if (k == key) return it }
            val colors = if (mid == null) intArrayOf(inner, inner, outer) else intArrayOf(inner, mid, outer)
            val stops = if (mid == null) floatArrayOf(0f, start, 1f) else floatArrayOf(0f, 0.38f, 1f)
            return RadialGradient(cx, cy, max(1f, r), colors, stops, Shader.TileMode.CLAMP).also { key = k; shader = it }
        }

        fun linear(x0: Float, x1: Float, edge: Int, center: Int): Shader {
            val k = hashKey(x0, x1, 0f, edge, center, 7)
            shader?.let { if (k == key) return it }
            return LinearGradient(x0, 0f, x1, 0f, intArrayOf(edge, center, edge), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
                .also { key = k; shader = it }
        }

        private fun hashKey(a: Float, b: Float, c: Float, d: Int, e: Int, g: Int): Long {
            var h = 1469598103934665603L
            for (v in longArrayOf(a.toRawBits().toLong(), b.toRawBits().toLong(), (c * 2f).toInt().toLong(), d.toLong(), e.toLong(), g.toLong())) {
                h = (h xor v) * 1099511628211L
            }
            return h
        }
    }

    // --------------------------------------------------------------------- math

    private companion object {
        const val N = 240
        const val RINGS = 3
        const val SEG = 72
        const val BANDS = 9
        const val CAMERA = 4.4f
        const val BASE_PITCH = 0.38f
        const val TAU = 6.2831855f
        const val GOLDEN_ANGLE = 2.3999631f
        const val GOLDEN_FRACTION = 0.618034f
        const val PLASTIC_FRACTION = 0.7548777f
        val TORUS_COS = cos(0.55f)
        val TORUS_SIN = sin(0.55f)

        const val TILT_X = 0
        const val TILT_Z = 1
        const val SPEED = 2
        const val RADIUS = 3
        const val ALPHA = 4

        /** Per state, per ring: tiltX, tiltZ, spin speed (rad/s), radius, alpha. */
        val RING: Array<Array<FloatArray>> = arrayOf(
            // IDLE: a lazy gyroscope.
            arrayOf(floatArrayOf(1.25f, 0.30f, 0.30f, 1.30f, 0.70f), floatArrayOf(1.90f, -0.70f, -0.22f, 1.44f, 0.50f), floatArrayOf(0.45f, 1.10f, 0.16f, 1.58f, 0.32f)),
            // LISTENING: rings turn to face you and stack into halos.
            arrayOf(floatArrayOf(1.57f, 0.00f, 0.60f, 1.24f, 0.85f), floatArrayOf(1.57f, 0.00f, -0.45f, 1.42f, 0.60f), floatArrayOf(1.57f, 0.00f, 0.30f, 1.62f, 0.38f)),
            // THINKING: fast counter-rotating gimbal.
            arrayOf(floatArrayOf(0.20f, 0.90f, 1.60f, 1.32f, 0.80f), floatArrayOf(1.40f, -1.10f, -1.25f, 1.44f, 0.65f), floatArrayOf(2.40f, 0.20f, 0.95f, 1.56f, 0.45f)),
            // SPEAKING: horizontal rings like sound leaving the shell.
            arrayOf(floatArrayOf(0.12f, 0.05f, 0.45f, 1.30f, 0.70f), floatArrayOf(0.30f, -0.12f, -0.35f, 1.46f, 0.50f), floatArrayOf(-0.20f, 0.18f, 0.25f, 1.62f, 0.34f)),
            // ALERT: tight, fast, dashed.
            arrayOf(floatArrayOf(1.10f, 0.40f, 2.20f, 1.28f, 0.95f), floatArrayOf(1.80f, -0.60f, -1.80f, 1.40f, 0.80f), floatArrayOf(0.60f, 1.20f, 1.40f, 1.52f, 0.60f)),
            // LEARNING: one slow ring, the rest fade away around the helix.
            arrayOf(floatArrayOf(1.45f, 0.10f, 0.20f, 1.05f, 0.55f), floatArrayOf(1.70f, -0.30f, -0.12f, 1.30f, 0.20f), floatArrayOf(1.20f, 0.40f, 0.08f, 1.55f, 0.10f)),
        )

        /** Whole-cloud spin per state (rad/s). */
        val SPIN = floatArrayOf(0.22f, 0.16f, 0.65f, 0.30f, 0.90f, 0.35f)

        fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
        fun ease(v: Float): Float {
            val x = v.coerceIn(0f, 1f)
            return x * x * (3f - 2f * x)
        }
        fun frac(v: Float) = v - floor(v)
        /** Keep phases small for fp32 trig; wrapping by a full turn is invisible. */
        fun wrap(v: Float) = when {
            v > TAU -> v - TAU
            v < -TAU -> v + TAU
            else -> v
        }
        fun hash(x: Float, y: Float) = frac(sin(x * 127.1f + y * 311.7f) * 43758.547f)

        fun lerpColor(a: Int, b: Int, t: Float): Int {
            val u = t.coerceIn(0f, 1f)
            return Color.argb(
                (Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * u).toInt(),
                (Color.red(a) + (Color.red(b) - Color.red(a)) * u).toInt(),
                (Color.green(a) + (Color.green(b) - Color.green(a)) * u).toInt(),
                (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * u).toInt(),
            )
        }

        fun withA(color: Int, a: Float): Int =
            Color.argb((a.coerceIn(0f, 1f) * 255f).toInt(), Color.red(color), Color.green(color), Color.blue(color))
    }
}
