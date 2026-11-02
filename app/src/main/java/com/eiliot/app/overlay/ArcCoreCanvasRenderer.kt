package com.eiliot.app.overlay

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

/**
 * Canvas/gradient mirror of the AGSL master shader, used on API 26–32 (and on
 * any frame where the canvas is not hardware-accelerated). It reproduces the
 * same scene graph — core, segmented ring, HUD ticks, particles, waveform,
 * reticle, ripples, radar, constellation — with classic [Paint] primitives so
 * the orb keeps its identity on older devices. Allocation-free per frame: all
 * paints/arrays are reused.
 *
 * Purely internal; driven exclusively by [ArcCoreView].
 */
internal object ArcCoreCanvasRenderer {

    private const val TAU = 6.2831855f
    private const val PARTICLES = 24
    private const val BARS = 40
    private const val TICKS = 60
    private const val NODES = 8

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rect = RectF()
    private val nx = FloatArray(NODES)
    private val ny = FloatArray(NODES)

    fun draw(
        canvas: Canvas,
        w: Int,
        h: Int,
        t: Float,
        mode: OrbState,
        prevMode: OrbState,
        mix: Float,
        amp: Float,
    ) {
        val cx = w / 2f
        val cy = h / 2f
        val r0 = min(cx, cy)
        val m = smooth01(mix)
        if (m >= 0.999f) {
            scene(canvas, cx, cy, r0, t, mode, amp, 1f)
        } else {
            scene(canvas, cx, cy, r0, t, prevMode, amp, 1f - m)
            scene(canvas, cx, cy, r0, t, mode, amp, m)
        }
    }

    private fun scene(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        r0: Float,
        t: Float,
        m: OrbState,
        amp: Float,
        a: Float,
    ) {
        val base = m.baseColor
        val hot = m.hotColor

        // Dark glass disc for contrast on light screens.
        fill.shader = RadialGradient(
            cx, cy, 0.92f * r0,
            intArrayOf(withA(Color.BLACK, 0.30f * a), withA(Color.BLACK, 0f)),
            floatArrayOf(0.72f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(cx, cy, 0.92f * r0, fill)
        fill.shader = null

        val bSpeed = when (m) {
            OrbState.ALERT -> 1.6f
            OrbState.SPEAKING -> 0.9f
            else -> 0.35f
        }
        val breathe = 0.5f + 0.5f * sin(t * bSpeed * TAU * 0.25f)

        // --- energy core ---
        var coreR = 0.30f + 0.035f * breathe
        if (m == OrbState.SPEAKING) coreR += amp * 0.10f
        if (m == OrbState.LISTENING) coreR *= 0.92f
        val ox = cx + sin(t * 0.7f) * r0 * 0.02f
        val oy = cy + cos(t * 0.53f) * r0 * 0.02f
        val gradR = coreR * r0 * 1.9f
        fill.shader = RadialGradient(
            ox, oy, gradR,
            intArrayOf(
                withA(hot, 0.95f * a),
                withA(base, 0.55f * a),
                withA(base, 0f),
            ),
            floatArrayOf(0f, 0.42f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(ox, oy, gradR, fill)
        fill.shader = null

        // --- main segmented ring ---
        var ringR = 0.62f + 0.02f * breathe
        if (m == OrbState.LISTENING) ringR += 0.06f + 0.03f * amp
        if (m == OrbState.THINKING) ringR = 0.56f
        val ringW = if (m == OrbState.LISTENING) 0.030f else 0.022f
        val rPx = ringR * r0
        val circumference = TAU * rPx
        val dashN = when (m) {
            OrbState.IDLE -> 3f
            OrbState.ALERT -> 6f
            else -> 4f
        }
        val dashSpeed = when (m) {
            OrbState.ALERT -> 0.5f
            OrbState.THINKING -> -0.3f
            else -> 0.12f
        }
        stroke.strokeWidth = (ringW * 2f * r0).coerceAtLeast(2.5f)
        // faint full ring underneath
        stroke.pathEffect = null
        stroke.color = withA(base, 0.28f * a * if (m == OrbState.ALERT) 1.3f else 1f)
        canvas.drawCircle(cx, cy, rPx, stroke)
        // rotating dashes on top
        val on = circumference / dashN * 0.62f
        stroke.pathEffect = DashPathEffect(
            floatArrayOf(on, circumference / dashN - on),
            -t * dashSpeed * circumference,
        )
        stroke.color = withA(tint(base, 0.25f), 0.85f * a)
        canvas.drawCircle(cx, cy, rPx, stroke)
        stroke.pathEffect = null

        // --- outer fine HUD ticks ---
        val tickSpeed = if (m == OrbState.THINKING) 0.10f else 0.03f
        stroke.strokeWidth = 2f
        stroke.color = withA(base, (if (m == OrbState.LISTENING) 0.55f else 0.35f) * a)
        val tickR = 0.80f * r0
        val tickLen = r0 * 0.035f
        for (i in 0 until TICKS) {
            val ang = (i / TICKS.toFloat() + t * tickSpeed) * TAU
            val c = cos(ang)
            val s = sin(ang)
            canvas.drawLine(
                cx + c * (tickR - tickLen), cy + s * (tickR - tickLen),
                cx + c * tickR, cy + s * tickR, stroke,
            )
        }

        // --- mode-specific layers ---
        when (m) {
            OrbState.LISTENING -> waveform(canvas, cx, cy, r0, t, ringR, amp, base, a)
            OrbState.THINKING -> reticle(canvas, cx, cy, r0, t, base, a)
            OrbState.SPEAKING -> ripples(canvas, cx, cy, r0, t, amp, base, a)
            OrbState.ALERT -> radar(canvas, cx, cy, r0, t, base, hot, a)
            OrbState.LEARNING -> constellation(canvas, cx, cy, r0, t, base, hot, a)
            OrbState.IDLE -> Unit
        }
        particles(canvas, cx, cy, r0, t, m, base, a)
    }

    // LISTENING — waveform bars orbiting just outside the ring.
    private fun waveform(
        canvas: Canvas, cx: Float, cy: Float, r0: Float,
        t: Float, ringR: Float, amp: Float, base: Int, a: Float,
    ) {
        val inner = (ringR + 0.045f) * r0
        stroke.strokeWidth = (r0 * 0.02f).coerceAtLeast(2f)
        stroke.color = withA(tint(base, 0.65f), 0.9f * a)
        for (i in 0 until BARS) {
            val ang = (i + 0.5f) / BARS * TAU
            val n = smoothNoise(i.toFloat(), t * 9f)
            val len = (0.05f + 0.30f * n) * (0.35f + 0.75f * amp) * r0
            val c = cos(ang)
            val s = sin(ang)
            canvas.drawLine(
                cx + c * inner, cy + s * inner,
                cx + c * (inner + len), cy + s * (inner + len), stroke,
            )
        }
    }

    // THINKING — spinning reticle.
    private fun reticle(
        canvas: Canvas, cx: Float, cy: Float, r0: Float,
        t: Float, base: Int, a: Float,
    ) {
        val ra = t * 0.9f
        stroke.strokeWidth = 2.5f
        stroke.color = withA(base, 0.5f * a)
        for (k in 0 until 4) {
            val ang = ra + k * (TAU / 4f)
            val c = cos(ang)
            val s = sin(ang)
            canvas.drawLine(
                cx + c * 0.34f * r0, cy + s * 0.34f * r0,
                cx + c * 0.48f * r0, cy + s * 0.48f * r0, stroke,
            )
        }
        stroke.pathEffect = DashPathEffect(floatArrayOf(8f, 6f), ra * 30f)
        stroke.color = withA(base, 0.8f * a)
        canvas.drawCircle(cx, cy, 0.42f * r0, stroke)
        stroke.pathEffect = null
    }

    // SPEAKING — expanding ripple rings.
    private fun ripples(
        canvas: Canvas, cx: Float, cy: Float, r0: Float,
        t: Float, amp: Float, base: Int, a: Float,
    ) {
        for (k in 0 until 3) {
            val ph = (t * 0.55f + k * 0.3333f) % 1f
            val fade = 1f - ph
            stroke.strokeWidth = (0.012f + 0.02f * amp) * r0 + 1.5f
            stroke.color = withA(base, fade * fade * (0.25f + 0.85f * amp) * a)
            canvas.drawCircle(cx, cy, (0.30f + 0.66f * ph) * r0, stroke)
        }
    }

    // ALERT — radar sweep trail + expanding pulse ring.
    private fun radar(
        canvas: Canvas, cx: Float, cy: Float, r0: Float,
        t: Float, base: Int, hot: Int, a: Float,
    ) {
        val midR = 0.58f * r0
        rect.set(cx - midR, cy - midR, cx + midR, cy + midR)
        val sweepDeg = (t * 0.35f * 360f) % 360f
        stroke.strokeWidth = 0.42f * r0
        for (k in 0 until 20) {
            stroke.color = withA(base, exp(-k * 0.30f) * 0.85f * a)
            canvas.drawArc(rect, sweepDeg - k * 3.4f, -3.0f, false, stroke)
        }
        val ph = (t * 0.8f) % 1f
        val fade = 1f - ph
        stroke.strokeWidth = 0.03f * r0 + 1.5f
        stroke.color = withA(hot, fade * fade * 0.9f * a)
        canvas.drawCircle(cx, cy, (0.15f + 0.8f * ph) * r0, stroke)
    }

    // LEARNING — drifting constellation.
    private fun constellation(
        canvas: Canvas, cx: Float, cy: Float, r0: Float,
        t: Float, base: Int, hot: Int, a: Float,
    ) {
        for (i in 0 until NODES) {
            val fi = i.toFloat()
            val h1 = hash21(fi, 3.3f)
            val h2 = hash21(fi, 7.7f)
            val h3 = hash21(fi, 5.1f)
            nx[i] = 0.55f * sin(t * (0.10f + 0.12f * h1) + h2 * TAU)
            ny[i] = 0.55f * cos(t * (0.08f + 0.14f * h3) + h1 * TAU)
        }
        stroke.strokeWidth = 1.5f
        for (i in 0 until NODES) {
            for (j in i + 1 until NODES) {
                val dx = nx[i] - nx[j]
                val dy = ny[i] - ny[j]
                val len = kotlin.math.sqrt(dx * dx + dy * dy)
                if (len < 0.62f) {
                    stroke.color = withA(base, (1f - len / 0.62f) * 0.30f * a)
                    canvas.drawLine(
                        cx + nx[i] * r0, cy + ny[i] * r0,
                        cx + nx[j] * r0, cy + ny[j] * r0, stroke,
                    )
                }
            }
        }
        for (i in 0 until NODES) {
            fill.color = withA(base, 0.30f * a)
            canvas.drawCircle(cx + nx[i] * r0, cy + ny[i] * r0, 6f, fill)
            fill.color = withA(hot, 0.9f * a)
            canvas.drawCircle(cx + nx[i] * r0, cy + ny[i] * r0, 2.6f, fill)
        }
    }

    // Orbiting particle field. IDLE swirls, THINKING spirals inward.
    private fun particles(
        canvas: Canvas, cx: Float, cy: Float, r0: Float,
        t: Float, m: OrbState, base: Int, a: Float,
    ) {
        if (m == OrbState.LEARNING) return
        for (i in 0 until PARTICLES) {
            val fi = i.toFloat()
            val h1 = hash21(fi, 1.7f)
            val h2 = hash21(fi, 9.2f)
            val h3 = hash21(fi, 4.1f)
            val dir = if (h2 > 0.5f) 1f else -1f
            var ang = h1 * TAU + t * (0.15f + 0.45f * h2) * dir
            var rad = 0.40f + 0.42f * h3 + 0.04f * sin(t * (0.4f + h2) + fi)
            if (m == OrbState.THINKING) {
                val cyc = fract(h3 - t * (0.10f + 0.28f * h2))
                rad = 0.08f + 0.82f * cyc
                ang += (1f - cyc) * 4f
            }
            fill.color = withA(base, 0.55f * a)
            canvas.drawCircle(
                cx + cos(ang) * rad * r0,
                cy + sin(ang) * rad * r0,
                2.6f + 1.6f * h3,
                fill,
            )
        }
    }

    // ------------------------------------------------------------------ math

    private fun hash21(x: Float, y: Float): Float =
        fract(sin(x * 127.1f + y * 311.7f) * 43758.5453f)

    private fun fract(v: Float): Float = v - floor(v)

    private fun smooth01(v: Float): Float {
        val x = v.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }

    private fun smoothNoise(bin: Float, tt: Float): Float {
        val i0 = floor(tt)
        val f = tt - i0
        val fe = f * f * (3f - 2f * f)
        val n0 = hash21(bin, i0)
        val n1 = hash21(bin, i0 + 1f)
        return n0 + (n1 - n0) * fe
    }

    /** Blend [color] toward white by [f] (for hot ring/waveform accents). */
    private fun tint(color: Int, f: Float): Int {
        val r = Color.red(color) + ((255 - Color.red(color)) * f).toInt()
        val g = Color.green(color) + ((255 - Color.green(color)) * f).toInt()
        val b = Color.blue(color) + ((255 - Color.blue(color)) * f).toInt()
        return Color.rgb(r, g, b)
    }

    private fun withA(color: Int, a: Float): Int = Color.argb(
        (a.coerceIn(0f, 1f) * 255f).toInt(),
        Color.red(color),
        Color.green(color),
        Color.blue(color),
    )
}
