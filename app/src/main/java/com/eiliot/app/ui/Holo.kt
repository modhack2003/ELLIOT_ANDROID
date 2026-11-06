package com.eiliot.app.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eiliot.app.overlay.ArcCoreView
import com.eiliot.app.overlay.OrbState
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * Singularity's 3D layer. Everything animated here reads state only in the
 * draw or layer phase, so time and tilt changes redraw pixels without
 * recomposing any screen.
 */

// ------------------------------------------------------------------ clock

/** Seconds since the holographic layer started; frozen at 0 in reduced motion. */
@Stable
class HoloClock {
    var seconds by mutableFloatStateOf(0f)
        internal set
}

/** Motion parallax from the device's gravity vector, -1..1 on each axis. */
@Stable
class Parallax {
    var x by mutableFloatStateOf(0f)
        internal set
    var y by mutableFloatStateOf(0f)
        internal set
}

val LocalHoloClock = staticCompositionLocalOf { HoloClock() }
val LocalParallax = staticCompositionLocalOf { Parallax() }

/**
 * Provides one shared clock (about 30 fps, enough for ambient light) and
 * device-tilt parallax for the whole app. The clock uses an infinite-animation
 * frame so UI tests and the system's animation policy can suspend it. The
 * sensor is only registered while the activity is resumed.
 */
@Composable
fun ProvideHolo(content: @Composable () -> Unit) {
    val clock = remember { HoloClock() }
    val parallax = remember { Parallax() }
    val reduced = rememberReducedMotion()
    val context = LocalContext.current
    LaunchedEffect(reduced) {
        if (reduced) { clock.seconds = 0f; return@LaunchedEffect }
        var start = -1L
        var last = 0L
        while (true) {
            withInfiniteAnimationFrameMillis { ms ->
                if (start < 0) start = ms
                if (ms - last >= 33) {
                    last = ms
                    clock.seconds = ((ms - start) % 3_600_000L) / 1000f
                }
            }
        }
    }
    LifecycleResumeEffect(reduced) {
        val listener = if (reduced) null else GravityParallax(context, parallax).also { it.start() }
        onPauseOrDispose {
            listener?.stop()
            parallax.x = 0f; parallax.y = 0f
        }
    }
    CompositionLocalProvider(LocalHoloClock provides clock, LocalParallax provides parallax, content = content)
}

/**
 * Parallax reacts to movement, not posture: a slow baseline follows the
 * gravity vector, and the offset from it drives the scene. Holding the phone
 * still drifts back to centre.
 */
private class GravityParallax(context: Context, private val out: Parallax) : SensorEventListener {
    private val manager = context.getSystemService(SensorManager::class.java)
    private val sensor = manager?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var bx = Float.NaN
    private var by = 0f
    private var sx = 0f
    private var sy = 0f

    fun start() {
        if (sensor != null) manager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        manager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val gx = event.values[0]
        val gy = event.values[1]
        if (bx.isNaN()) { bx = gx; by = gy }
        bx += (gx - bx) * 0.02f
        by += (gy - by) * 0.02f
        sx += ((-(gx - bx) / 3.2f).coerceIn(-1f, 1f) - sx) * 0.25f
        sy += (((gy - by) / 3.2f).coerceIn(-1f, 1f) - sy) * 0.25f
        out.x = sx
        out.y = sy
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}

// --------------------------------------------------------------- backdrop

private class Star(val x: Float, val y: Float, val z: Float, val twinkle: Float, val tint: Int)

/**
 * Deep space: drifting aurora nebulae, a 3D starfield flying slowly toward
 * the camera (near stars shift more with tilt, so depth is real), and a
 * perspective horizon grid scrolling beneath the content.
 */
@Composable
fun CosmicBackdrop(modifier: Modifier = Modifier) {
    val clock = LocalHoloClock.current
    val parallax = LocalParallax.current
    val stars = remember {
        var s = 0x2545F491L
        fun rnd(): Float { s = (s * 6364136223846793005L + 1442695040888963407L); return ((s ushr 33) and 0xFFFFFF).toFloat() / 0xFFFFFF }
        List(150) { Star(rnd() * 2f - 1f, rnd() * 2f - 1f, rnd(), rnd() * 6.28f, (rnd() * 3f).toInt()) }
    }
    val tints = remember { listOf(Color.White, EiliotColors.Cyan, EiliotColors.Ultraviolet) }
    val gridBrush = remember { Brush.verticalGradient(0f to Color.Transparent, 1f to EiliotColors.Cyan.copy(alpha = 0.20f)) }
    Canvas(modifier.fillMaxSize()) {
        val t = clock.seconds
        val px = parallax.x
        val py = parallax.y
        val w = size.width
        val h = size.height
        val unit = min(w, h)

        drawRect(Brush.verticalGradient(listOf(Color(0xFF020309), Color(0xFF060A1F), Color(0xFF0B0722))))

        // Aurora nebulae.
        nebula(Offset(w * (0.12f + 0.04f * sin(t * 0.05f)) + px * 18f, h * 0.16f + py * 12f), unit * 0.95f, EiliotColors.Cyan, 0.13f)
        nebula(Offset(w * (0.92f + 0.03f * cos(t * 0.04f)) + px * 26f, h * 0.38f + py * 16f), unit * 1.05f, EiliotColors.Violet, 0.15f)
        nebula(Offset(w * 0.5f + px * 34f, h * (1.02f + 0.02f * sin(t * 0.06f))), unit * 1.1f, EiliotColors.Magenta, 0.10f)

        // Starfield in true perspective.
        val cx = w / 2f
        val cy = h * 0.42f
        val spread = max(w, h) * 0.55f
        for (star in stars) {
            val depth = frac(star.z - t * 0.010f) * 0.92f + 0.08f
            val inv = 1f / depth
            val sx = cx + star.x * spread * inv * 0.35f + px * inv * 6f
            val sy = cy + star.y * spread * inv * 0.35f + py * inv * 5f
            if (sx < -4f || sx > w + 4f || sy < -4f || sy > h + 4f) continue
            val fadeIn = ((1f - depth) * 4f).coerceIn(0f, 1f)
            val fadeOut = (depth * 7f).coerceIn(0f, 1f)
            val twinkle = 0.65f + 0.35f * sin(t * 1.7f + star.twinkle)
            val r = (0.45f * inv).coerceIn(0.5f, 3.2f) * density / 2.2f
            val c = tints[star.tint]
            val a = fadeIn * fadeOut * twinkle * 0.85f
            if (inv > 4f) drawCircle(c.copy(alpha = a * 0.18f), r * 3.4f, Offset(sx, sy))
            drawCircle(c.copy(alpha = a), r, Offset(sx, sy))
        }

        // Perspective horizon grid.
        val horizon = h * 0.64f + py * 10f
        val focal = (h - horizon) * 1.25f
        val vpX = cx - px * 30f
        val far = 14f
        val scroll = frac(t * 0.12f)
        for (k in 1..13) {
            val z = k - scroll + 0.6f
            val y = horizon + focal / z
            if (y > h) continue
            val a = (1f - z / far).coerceIn(0f, 1f)
            drawLine(EiliotColors.Cyan.copy(alpha = 0.16f * a * a), Offset(0f, y), Offset(w, y), strokeWidth = 1f + a)
        }
        for (xw in -9..9) {
            val top = Offset(vpX + xw * focal / far, horizon + focal / far)
            val bottom = Offset(vpX + xw * focal / 0.6f, horizon + focal / 0.6f)
            drawLine(gridBrush, top, bottom, strokeWidth = 1.2f)
        }
        // Horizon light.
        drawRect(
            Brush.verticalGradient(
                0f to Color.Transparent, 0.5f to EiliotColors.Violet.copy(alpha = 0.10f), 1f to Color.Transparent,
                startY = horizon - unit * 0.12f, endY = horizon + unit * 0.06f,
            ),
            topLeft = Offset(0f, horizon - unit * 0.12f), size = Size(w, unit * 0.18f),
        )
        drawLine(
            Brush.horizontalGradient(listOf(Color.Transparent, EiliotColors.Cyan.copy(alpha = 0.55f), EiliotColors.Magenta.copy(alpha = 0.45f), Color.Transparent)),
            Offset(0f, horizon), Offset(w, horizon), strokeWidth = 1.5f,
        )
        // Vignette keeps text contrast at the edges.
        drawRect(Brush.radialGradient(listOf(Color.Transparent, Color(0xAA010206)), center = Offset(cx, h * 0.45f), radius = max(w, h) * 0.85f))
    }
}

private fun DrawScope.nebula(center: Offset, radius: Float, color: Color, alpha: Float) {
    drawCircle(Brush.radialGradient(listOf(color.copy(alpha = alpha), color.copy(alpha = alpha * 0.35f), Color.Transparent), center, radius), radius, center)
}

private fun frac(v: Float) = v - floor(v)

// ------------------------------------------------------------- the stage

/** Compose colour for an orb state, matching the hologram's particles. */
fun OrbState.accent(): Color = Color(baseColor)

/**
 * The 3D orb as a Compose element. In the app it is ambient (always alive
 * while visible), spins under a finger with inertia, and tilts with the phone.
 */
@Composable
fun HoloOrb(
    state: OrbState,
    size: Dp,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    description: String = "ELLIOT presence",
    interactive: Boolean = true,
) {
    val appearance = LocalOverlayAppearance.current
    val parallax = LocalParallax.current
    val reduced = rememberReducedMotion()
    val amplitude by com.eiliot.app.voice.NeuralSpeechPlayer.amplitude.collectAsStateWithLifecycle()
    var orb by remember { mutableStateOf<ArcCoreView?>(null) }
    // Every vsync while visible; suspended by the platform's infinite-animation policy.
    LaunchedEffect(orb, enabled, reduced) {
        val view = orb ?: return@LaunchedEffect
        if (!enabled || reduced) return@LaunchedEffect
        while (true) withInfiniteAnimationFrameNanos { view.advanceFrame(it) }
    }
    AndroidView(
        factory = { ArcCoreView(it).apply { setContrastDisc(false) }.also { orb = it } },
        update = {
            it.setState(state)
            it.setAmplitude(amplitude)
            it.setPresentation(appearance.motion, appearance.haptics)
            it.setPaused(!enabled)
            it.setAmbient(enabled)
            it.setInteractive(interactive)
            it.setTilt(parallax.x, parallax.y)
        },
        modifier = modifier.size(size).semantics { contentDescription = description },
    )
}

/**
 * A hologram projector: the orb floats and bobs above a glowing emitter
 * (concentric perspective discs, a rotating dashed track), lifted by a cone
 * of light with scan rings rising through it. The stage colour follows the
 * orb state, and the whole projector leans with device tilt.
 */
@Composable
fun HoloStage(
    state: OrbState,
    orbSize: Dp,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    description: String = "ELLIOT presence",
) {
    val clock = LocalHoloClock.current
    val parallax = LocalParallax.current
    val reduced = rememberReducedMotion()
    val accent by animateColorAsState(state.accent(), EiliotMotion.tweenEnter(), label = "stageAccent")
    val rim by animateColorAsState(Color(state.rimColor), EiliotMotion.tweenEnter(), label = "stageRim")
    val bob = with(LocalDensity.current) { 5.dp.toPx() }
    Box(
        modifier
            .size(width = orbSize * 1.45f, height = orbSize * 1.32f)
            .graphicsLayer {
                rotationY = parallax.x * 9f
                rotationX = -parallax.y * 7f
                cameraDistance = 14f * density
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val t = clock.seconds
            val w = size.width
            val h = size.height
            val baseY = h * 0.885f
            val rx = w * 0.36f
            val ry = rx * 0.24f
            val orbCy = orbSize.toPx() * 0.5f

            // Light cone from emitter to orb.
            val cone = Path().apply {
                moveTo(w / 2f - rx * 0.92f, baseY)
                lineTo(w / 2f - orbSize.toPx() * 0.30f, orbCy)
                lineTo(w / 2f + orbSize.toPx() * 0.30f, orbCy)
                lineTo(w / 2f + rx * 0.92f, baseY)
                close()
            }
            drawPath(cone, Brush.verticalGradient(listOf(Color.Transparent, accent.copy(alpha = 0.05f), accent.copy(alpha = 0.20f)), startY = orbCy, endY = baseY))

            // Scan rings rising through the cone.
            if (!reduced) for (k in 0 until 3) {
                val p = frac(t * 0.35f + k / 3f)
                val y = baseY - (baseY - orbCy) * p
                val span = rx * (0.92f - 0.62f * p)
                drawOval(accent.copy(alpha = 0.35f * (1f - p) * (1f - p)), Offset(w / 2f - span, y - span * 0.22f), Size(span * 2f, span * 0.44f), style = Stroke(1.2f))
            }

            // Emitter discs.
            drawOval(Brush.radialGradient(listOf(accent.copy(alpha = 0.32f), Color.Transparent), Offset(w / 2f, baseY), rx * 1.25f), Offset(w / 2f - rx * 1.25f, baseY - ry * 1.25f), Size(rx * 2.5f, ry * 2.5f))
            for (k in 0 until 3) {
                val s = 1f - k * 0.24f
                drawOval(accent.copy(alpha = 0.55f - k * 0.13f), Offset(w / 2f - rx * s, baseY - ry * s), Size(rx * 2f * s, ry * 2f * s), style = Stroke(if (k == 0) 1.8f else 1.1f))
            }
            drawOval(
                rim.copy(alpha = 0.85f),
                Offset(w / 2f - rx * 1.12f, baseY - ry * 1.12f), Size(rx * 2.24f, ry * 2.24f),
                style = Stroke(2.2f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(rx * 0.18f, rx * 0.30f), t * rx * 0.6f)),
            )
            drawOval(Color.White.copy(alpha = 0.75f), Offset(w / 2f - rx * 0.10f, baseY - ry * 0.10f), Size(rx * 0.20f, ry * 0.20f))
        }
        HoloOrb(
            state, orbSize,
            Modifier.graphicsLayer {
                translationY = if (reduced) 0f else sin(clock.seconds * 1.15f) * bob
                translationX = parallax.x * bob * 1.4f
            },
            enabled = enabled, description = description,
        )
    }
}

// ------------------------------------------------------------- typography

/**
 * Paints the content's own glyphs with a moving aurora gradient. Drawn
 * offscreen with SrcIn, so only text pixels take the colour.
 */
fun Modifier.aurora(clock: HoloClock, colors: List<Color> = EiliotColors.Aurora, speed: Float = 0.08f): Modifier =
    drawWithContent {
        // An explicit layer isolates the glyphs, so SrcIn colours only text pixels on any canvas.
        drawIntoCanvas { it.saveLayer(Rect(Offset.Zero, size), Paint()) }
        drawContent()
        val span = size.width.coerceAtLeast(1f) * 1.6f
        val shift = frac(clock.seconds * speed) * span
        drawRect(
            Brush.linearGradient(colors + colors.first(), start = Offset(-shift, 0f), end = Offset(span - shift, size.height * 0.4f), tileMode = androidx.compose.ui.graphics.TileMode.Repeated),
            blendMode = BlendMode.SrcIn,
        )
        drawIntoCanvas { it.restore() }
    }

/** Screen title: a mono kicker over an aurora headline. */
@Composable
fun ScreenTitle(text: String, kicker: String, modifier: Modifier = Modifier) {
    val clock = LocalHoloClock.current
    Column(modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        SectionLabel(kicker, accent = EiliotColors.Teal)
        Text(
            text,
            style = MaterialTheme.typography.headlineSmall,
            color = EiliotColors.TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp).aurora(clock, speed = 0.05f),
        )
    }
}

/**
 * Conversation depth. As a message travels toward the top of the viewport it
 * leans back into space (rotation about its lower edge, slight shrink and
 * fade), like text receding along a projection plane. New messages also flip
 * up out of the glass on arrival. Both are layer-only transforms.
 *
 * @param fraction 0 at the bottom of the viewport, 1 at the top.
 * @param entrance 0 when a message first appears, 1 once it has landed.
 */
fun Modifier.holoDepth(fraction: () -> Float, entrance: () -> Float, enabled: Boolean): Modifier =
    if (!enabled) this else graphicsLayer {
        val f = ((fraction() - 0.42f) / 0.58f).coerceIn(0f, 1f)
        val e = f * f
        val arrive = entrance().coerceIn(0f, 1f)
        val pending = 1f - arrive
        rotationX = 18f * e - 38f * pending
        scaleX = 1f - 0.08f * e - 0.06f * pending
        scaleY = scaleX
        translationY = 28.dp.toPx() * pending
        alpha = (1f - 0.5f * e) * (0.15f + 0.85f * arrive)
        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, if (pending > 0.001f) 1f else 0.5f + 0.5f * e)
        cameraDistance = 9f * density
    }
