package com.eiliot.app.ui

import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * ELLIOT design system — "Sentinel Glass".
 *
 * A neon-on-black HUD palette befitting a cyber-sentinel: deep void blacks,
 * an electric cyan primary (matching the Arc Core orb's IDLE state), violet
 * and ultraviolet secondaries (THINKING / LEARNING), teal (SPEAKING) and a
 * hot amber/red alert pair. Every surface in the app is built on these tokens
 * so the whole experience reads as one instrument.
 */
object EiliotColors {
    /** Deepest background black. */
    val Void = Color(0xFF03060C)

    /** Raised background. */
    val Abyss = Color(0xFF070D18)

    /** Panel base. */
    val Panel = Color(0xFF0C1424)

    /** Primary neon — Arc Core IDLE cyan. */
    val Cyan = Color(0xFF00E5FF)
    val CyanDim = Color(0xFF0091B3)

    /** Secondary — THINKING violet. */
    val Violet = Color(0xFF7C8CFF)

    /** Tertiary — LEARNING ultraviolet. */
    val Ultraviolet = Color(0xFFA78BFF)

    /** SPEAKING teal. */
    val Teal = Color(0xFF3EF2D8)

    /** Warning amber. */
    val Amber = Color(0xFFFFB54F)

    /** ALERT hot orange-red. */
    val Alert = Color(0xFFFF5A2A)

    val TextPrimary = Color(0xFFE6F2FF)
    val TextSecondary = Color(0xFF9FB4CC)
    val TextMuted = Color(0xFF5E738A)
}

/**
 * The motion vocabulary. One easing family across the whole app so every
 * transition feels choreographed by the same hand: fast attack, long silky
 * settle (easeOutExpo) for entrances, soft springs for spatial movement and
 * press states.
 */
object EiliotMotion {
    /** Cinematic entrance easing — fast out of the gate, long elegant settle. */
    val easeOutExpo = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

    /** Standard UI easing for small state changes. */
    val standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Soft spring for presses, scale and position. */
    fun <T> springSoft() = spring<T>(dampingRatio = 0.82f, stiffness = 300f)

    /** Snappy spring for toggles and chips. */
    fun <T> springSnappy() = spring<T>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 600f)

    /** Entrance tween (fade/slide) — the signature ELLIOT reveal. */
    fun <T> tweenEnter() = tween<T>(420, easing = easeOutExpo)

    /** Quick state tween. */
    fun <T> tweenFast() = tween<T>(220, easing = standard)
}

private val HudTypography = Typography(
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Light,
        fontSize = 30.sp,
        letterSpacing = 6.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        letterSpacing = 1.5.sp,
    ),
    bodyMedium = TextStyle(
        fontSize = 15.sp,
        lineHeight = 21.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 10.sp,
        letterSpacing = 1.6.sp,
    ),
)

/**
 * True when the user has disabled animations at the system level
 * (animator duration scale = 0). Every cinematic layer in the app consults
 * this and drops to a calm tier: frozen shader clock, snap transitions,
 * static glow. Cinematic must never mean inaccessible.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    val system = LocalSystemMotionOff.current ?: remember {
        try {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
        } catch (t: Throwable) {
            false
        }
    }
    return system || LocalOverlayAppearance.current.motion == "reduced"
}

@Composable
fun EiliotTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    // Material You dynamic colour on Android 12+ (wallpaper-derived), with the
    // ELLIOT neon scheme as the fallback. The HUD keeps its cyan identity via
    // EiliotColors; dynamic colour tints the Material components.
    val scheme = if (Build.VERSION.SDK_INT >= 31) {
        dynamicDarkColorScheme(context)
    } else {
        darkColorScheme(
            primary = EiliotColors.Cyan,
            onPrimary = Color(0xFF00242B),
            primaryContainer = Color(0xFF06303A),
            onPrimaryContainer = EiliotColors.TextPrimary,
            secondary = EiliotColors.Violet,
            onSecondary = Color(0xFF0B1030),
            tertiary = EiliotColors.Ultraviolet,
            background = EiliotColors.Void,
            onBackground = EiliotColors.TextPrimary,
            surface = EiliotColors.Abyss,
            onSurface = EiliotColors.TextPrimary,
            surfaceVariant = EiliotColors.Panel,
            onSurfaceVariant = EiliotColors.TextSecondary,
            outline = Color(0xFF24364E),
            error = EiliotColors.Alert,
        )
    }
    MaterialTheme(colorScheme = scheme, typography = HudTypography, content = content)
}
