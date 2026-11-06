package com.eiliot.app.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp

/**
 * Haptic vocabulary for the HUD. Motion you can *feel*: a soft virtual key on
 * every tap, a long press for the mic, clock ticks for incoming proactive
 * messages, and a platform confirm when configuration lands.
 */
object HudHaptics {
    fun tap(view: View) {
        if (!com.eiliot.app.overlay.OverlayLayoutStore.hapticsEnabled(view.context)) return
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    fun longPress(view: View) {
        if (!com.eiliot.app.overlay.OverlayLayoutStore.hapticsEnabled(view.context)) return
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    fun tick(view: View) {
        if (!com.eiliot.app.overlay.OverlayLayoutStore.hapticsEnabled(view.context)) return
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    fun confirm(view: View) {
        if (!com.eiliot.app.overlay.OverlayLayoutStore.hapticsEnabled(view.context)) return
        if (Build.VERSION.SDK_INT >= 30) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            tap(view)
        }
    }
}

/**
 * Primary action: a glass lens with an aurora edge and a light bar across its
 * top. Presses sink into the glass (scale plus a small drop), with a haptic.
 */
@Composable
fun GlowButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    compact: Boolean = false,
    icon: ImageVector? = null,
    accent: Color = EiliotColors.Cyan,
) {
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reduced = rememberReducedMotion()
    val press by animateFloatAsState(if (pressed && !reduced) 1f else 0f, EiliotMotion.springSoft(), label = "glowPress")
    val shape = RoundedCornerShape(if (compact) 14.dp else 18.dp)
    Box(
        modifier
            .graphicsLayer {
                scaleX = 1f - 0.04f * press; scaleY = 1f - 0.04f * press
                translationY = 2.dp.toPx() * press
            }
            .shadow(if (enabled) 12.dp else 0.dp, shape, clip = false, ambientColor = accent.copy(alpha = 0.3f), spotColor = accent.copy(alpha = 0.45f))
            .clip(shape)
            .background(
                if (enabled) Brush.verticalGradient(listOf(accent.copy(alpha = 0.34f), Color(0xE00A1430), accent.copy(alpha = 0.16f)))
                else Brush.verticalGradient(listOf(Color(0x1F3A4A60), Color(0x1A2A3A50))),
            )
            .drawWithCache {
                val bar = Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = if (enabled) 0.55f else 0.1f), Color.Transparent))
                onDrawWithContent {
                    drawContent()
                    drawRect(bar, topLeft = Offset(size.width * 0.15f, 0f), size = Size(size.width * 0.7f, 1.2.dp.toPx()))
                }
            }
            .border(
                1.dp,
                if (enabled) Brush.linearGradient(listOf(accent.copy(alpha = 0.95f), EiliotColors.Violet.copy(alpha = 0.5f), EiliotColors.Magenta.copy(alpha = 0.55f)))
                else Brush.linearGradient(listOf(Color(0x33414F63), Color(0x33414F63))),
                shape,
            )
            .clickable(interactionSource = interaction, indication = LocalIndication.current, enabled = enabled, onClickLabel = text, role = Role.Button) {
                HudHaptics.tap(view)
                onClick()
            }
            .padding(horizontal = if (compact) 14.dp else 20.dp, vertical = if (compact) 9.dp else 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (icon != null) Icon(icon, null, tint = if (enabled) accent else EiliotColors.TextMuted, modifier = Modifier.size(17.dp))
            Text(
                text = text.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, letterSpacing = 2.2.sp),
                color = if (enabled) EiliotColors.TextPrimary else EiliotColors.TextMuted,
            )
        }
    }
}

/**
 * Circular control rendered as a lit glass sphere: dark body, accent
 * subsurface glow from below, a specular highlight up-left and a fine rim.
 * Presses push the sphere into the surface.
 */
@Composable
fun GlowIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Color = EiliotColors.Cyan,
    size: Dp = 48.dp,
) {
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reduced = rememberReducedMotion()
    val press by animateFloatAsState(if (pressed && !reduced) 1f else 0f, EiliotMotion.springSoft(), label = "iconPress")
    val light = if (enabled) 1f else 0.35f
    Box(
        modifier
            .size(size)
            .graphicsLayer {
                scaleX = 1f - 0.08f * press; scaleY = 1f - 0.08f * press
                translationY = 1.5.dp.toPx() * press
            }
            .shadow(if (enabled) 10.dp else 0.dp, CircleShape, clip = false, ambientColor = accent.copy(alpha = 0.35f), spotColor = accent.copy(alpha = 0.55f))
            .clip(CircleShape)
            .drawBehind {
                val r = this.size.minDimension / 2f
                drawCircle(Brush.radialGradient(listOf(Color(0xFF16224A), Color(0xFF070B1C)), Offset(r * 0.8f, r * 0.7f), r * 1.3f))
                drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.55f * light), Color.Transparent), Offset(r, r * 1.75f), r * 1.15f))
                drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.42f * light), Color.Transparent), Offset(r * 0.62f, r * 0.48f), r * 0.55f))
            }
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f * light), accent.copy(alpha = 0.75f * light))), CircleShape)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, enabled = enabled, onClickLabel = contentDescription, role = Role.Button) {
                HudHaptics.tap(view)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = if (enabled) Color.White.copy(alpha = 0.92f) else EiliotColors.TextMuted, modifier = Modifier.size(size * 0.42f))
    }
}

/** Small live-status pill: a glowing dot and a mono label. */
@Composable
fun StatusChip(
    label: String,
    ok: Boolean,
    modifier: Modifier = Modifier,
    okColor: Color = EiliotColors.Teal,
) {
    val tone = if (ok) okColor else EiliotColors.TextMuted
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Brush.horizontalGradient(listOf(tone.copy(alpha = 0.18f), Color(0x400A1226))))
            .border(1.dp, tone.copy(alpha = 0.45f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(7.dp).drawBehind {
            drawCircle(tone.copy(alpha = 0.35f), radius = this.size.minDimension)
            drawCircle(tone)
        })
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = if (ok) EiliotColors.TextPrimary else EiliotColors.TextSecondary)
    }
}

/** Section heading: a fading light beam and a mono uppercase label. */
@Composable
fun SectionLabel(text: String, accent: Color = EiliotColors.Cyan) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .size(width = 18.dp, height = 2.dp)
                .background(Brush.horizontalGradient(listOf(Color.Transparent, accent)), RoundedCornerShape(50)),
        )
        Box(Modifier.size(4.dp).background(accent, CircleShape))
        Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = accent)
    }
}

/** Tiny selectable chip (suggestions, pickers). */
@Composable
fun SelectChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    accent: Color = EiliotColors.Cyan,
) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Brush.verticalGradient(listOf(accent.copy(alpha = 0.32f), accent.copy(alpha = 0.12f))) else Brush.verticalGradient(listOf(Color(0x260E1834), Color(0x1A0A1226))))
            .border(1.dp, if (selected) accent.copy(alpha = 0.8f) else EiliotColors.TextMuted.copy(alpha = 0.3f), RoundedCornerShape(50))
            .clickable(onClickLabel = label, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = if (selected) Color.White else EiliotColors.TextSecondary)
    }
}

/** Shared glass styling for every text field in the app. */
@Composable
fun hudFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = EiliotColors.TextPrimary,
    unfocusedTextColor = EiliotColors.TextPrimary,
    focusedContainerColor = Color(0x400D1834),
    unfocusedContainerColor = Color(0x2A0A1226),
    focusedBorderColor = EiliotColors.Cyan.copy(alpha = 0.9f),
    unfocusedBorderColor = EiliotColors.Violet.copy(alpha = 0.30f),
    focusedLabelColor = EiliotColors.Cyan,
    unfocusedLabelColor = EiliotColors.TextMuted,
    cursorColor = EiliotColors.Cyan,
    focusedPlaceholderColor = EiliotColors.TextMuted,
    unfocusedPlaceholderColor = EiliotColors.TextMuted,
)

/** Glass-styled text field with rounded HUD corners. */
@Composable
fun HudTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    secret: Boolean = false,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else 4,
    placeholder: String? = null,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = if (placeholder != null) ({ Text(placeholder) }) else null,
        singleLine = singleLine,
        maxLines = maxLines,
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        keyboardActions = KeyboardActions(
            onSend = { onImeAction?.invoke() },
            onNext = { onImeAction?.invoke() },
            onDone = { onImeAction?.invoke() },
            onGo = { onImeAction?.invoke() },
            onSearch = { onImeAction?.invoke() },
        ),
        shape = RoundedCornerShape(14.dp),
        colors = hudFieldColors(),
        modifier = modifier.fillMaxWidth(),
    )
}
