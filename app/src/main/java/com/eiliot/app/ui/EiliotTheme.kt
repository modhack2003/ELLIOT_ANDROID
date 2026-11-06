package com.eiliot.app.ui

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * ELLIOT design system: "Singularity".
 *
 * ELLIOT is a hologram living inside a deep-space observatory. Surfaces are
 * smoked glass floating over a starfield; light comes from the orb itself.
 * The palette runs from near-black space through an aurora of cyan, violet
 * and magenta. Cyan stays the identity colour because it is the orb's idle
 * state, so the app and the floating overlay read as one instrument.
 *
 * Token names are shared by every screen; only their values define the look.
 */
object EiliotColors {
    /** Deep space behind everything. */
    val Void = Color(0xFF020309)

    /** Raised background. */
    val Abyss = Color(0xFF060A1C)

    /** Glass panel base. */
    val Panel = Color(0xFF0A1128)

    /** Primary: orb idle cyan. */
    val Cyan = Color(0xFF00E5FF)
    val CyanDim = Color(0xFF00839E)

    /** Thinking violet. */
    val Violet = Color(0xFF8C7DFF)

    /** Learning ultraviolet. */
    val Ultraviolet = Color(0xFFB48CFF)

    /** Aurora magenta: the far edge of the spectrum, used for the user's voice. */
    val Magenta = Color(0xFFFF4FD8)

    /** Speaking teal. */
    val Teal = Color(0xFF3EF2D8)

    /** Warning amber. */
    val Amber = Color(0xFFFFB54F)

    /** Alert hot red-orange. */
    val Alert = Color(0xFFFF5A2A)

    val TextPrimary = Color(0xFFEAF4FF)
    val TextSecondary = Color(0xFFA3B6D0)
    val TextMuted = Color(0xFF61748F)

    /** The aurora spectrum used by wordmarks, sweeps and active edges. */
    val Aurora = listOf(Cyan, Violet, Magenta, Cyan)

    fun auroraBrush(): Brush = Brush.linearGradient(Aurora)
}

/**
 * The motion vocabulary. One easing family across the whole app so every
 * transition feels choreographed by the same hand: fast attack, long silky
 * settle (easeOutExpo) for entrances, soft springs for spatial movement.
 */
object EiliotMotion {
    /** Cinematic entrance easing: fast out of the gate, long elegant settle. */
    val easeOutExpo = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

    /** Standard UI easing for small state changes. */
    val standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Soft spring for presses, scale and position. */
    fun <T> springSoft() = spring<T>(dampingRatio = 0.82f, stiffness = 300f)

    /** Snappy spring for toggles and chips. */
    fun <T> springSnappy() = spring<T>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 600f)

    /** Gently bouncing spring for the navigation light and dock lift. */
    fun <T> springLift() = spring<T>(dampingRatio = 0.62f, stiffness = 380f)

    /** Entrance tween (fade/slide): the signature ELLIOT reveal. */
    fun <T> tweenEnter() = tween<T>(520, easing = easeOutExpo)

    /** Quick state tween. */
    fun <T> tweenFast() = tween<T>(220, easing = standard)
}

private val Sans = FontFamily.SansSerif
private val Mono = FontFamily.Monospace

private val SingularityTypography = Typography(
    displaySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.ExtraLight, fontSize = 34.sp, letterSpacing = 8.sp),
    headlineLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Light, fontSize = 30.sp, letterSpacing = 4.sp),
    headlineSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Light, fontSize = 24.sp, letterSpacing = 1.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 21.sp, letterSpacing = 0.6.sp),
    titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 16.sp, letterSpacing = 1.2.sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.2.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontSize = 15.sp, lineHeight = 22.sp, letterSpacing = 0.2.sp),
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 14.sp, letterSpacing = 0.8.sp),
    labelSmall = TextStyle(fontFamily = Mono, fontSize = 10.sp, letterSpacing = 1.8.sp),
)

private val SingularityShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private val SingularityScheme = darkColorScheme(
    primary = EiliotColors.Cyan,
    onPrimary = Color(0xFF00222A),
    primaryContainer = Color(0xFF083447),
    onPrimaryContainer = EiliotColors.TextPrimary,
    secondary = EiliotColors.Violet,
    onSecondary = Color(0xFF0D0B33),
    secondaryContainer = Color(0xFF1A2257),
    onSecondaryContainer = EiliotColors.TextPrimary,
    tertiary = EiliotColors.Magenta,
    onTertiary = Color(0xFF33002A),
    tertiaryContainer = Color(0xFF3A0F3A),
    onTertiaryContainer = EiliotColors.TextPrimary,
    background = EiliotColors.Void,
    onBackground = EiliotColors.TextPrimary,
    surface = EiliotColors.Abyss,
    onSurface = EiliotColors.TextPrimary,
    surfaceVariant = EiliotColors.Panel,
    onSurfaceVariant = EiliotColors.TextSecondary,
    surfaceTint = EiliotColors.Cyan,
    surfaceBright = Color(0xFF17213F),
    surfaceDim = EiliotColors.Void,
    surfaceContainerLowest = Color(0xFF03050F),
    surfaceContainerLow = Color(0xFF070C1E),
    surfaceContainer = Color(0xFF0A1126),
    surfaceContainerHigh = Color(0xFF0E1630),
    surfaceContainerHighest = Color(0xFF131C3A),
    inverseSurface = EiliotColors.TextPrimary,
    inverseOnSurface = EiliotColors.Abyss,
    inversePrimary = EiliotColors.CyanDim,
    outline = Color(0xFF2A3A63),
    outlineVariant = Color(0xFF1A2546),
    scrim = Color(0xE6000000),
    error = EiliotColors.Alert,
    onError = Color(0xFF2B0700),
)

/**
 * True when the user has disabled animations at the system level
 * (animator duration scale = 0). Every cinematic layer in the app consults
 * this and drops to a calm tier: frozen clocks, snap transitions, static glow.
 * Cinematic must never mean inaccessible.
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

/**
 * The Singularity scheme is always used, including on Android 12+, because the
 * holographic look depends on a fixed spectrum. Wallpaper-derived colours
 * would tint the Material components away from the orb's light.
 */
@Composable
fun EiliotTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SingularityScheme,
        typography = SingularityTypography,
        shapes = SingularityShapes,
        content = content,
    )
}
