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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

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
 * Primary action button — a glowing gradient-glass pill with a spring press
 * scale, neon hairline, ripple and haptic. This is what "press me" feels like
 * in ELLIOT's world.
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
    val scale by animateFloatAsState(
        targetValue = if (pressed && !rememberReducedMotion()) 0.95f else 1f,
        animationSpec = EiliotMotion.springSoft(),
        label = "glowPress",
    )
    val shape = RoundedCornerShape(if (compact) 12.dp else 16.dp)
    Box(
        modifier
            .scale(scale)
            .clip(shape)
            .background(
                if (enabled) {
                    Brush.horizontalGradient(
                        listOf(accent.copy(alpha = 0.30f), accent.copy(alpha = 0.14f)),
                    )
                } else {
                    Brush.horizontalGradient(
                        listOf(Color(0x1A3A4A60), Color(0x1A3A4A60)),
                    )
                },
            )
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    if (enabled) {
                        listOf(accent.copy(alpha = 0.75f), accent.copy(alpha = 0.35f))
                    } else {
                        listOf(Color(0x33414F63), Color(0x33414F63))
                    },
                ),
                shape = shape,
            )
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled,
                onClickLabel = text,
                role = Role.Button,
            ) {
                HudHaptics.tap(view)
                onClick()
            }
            .padding(
                horizontal = if (compact) 14.dp else 20.dp,
                vertical = if (compact) 8.dp else 13.dp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) accent else EiliotColors.TextMuted,
                    modifier = Modifier.size(17.dp),
                )
            }
            Text(
                text = text.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = if (enabled) EiliotColors.TextPrimary else EiliotColors.TextMuted,
            )
        }
    }
}

/** Circular glass icon button (back, send) with press scale + haptic. */
@Composable
fun GlowIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Color = EiliotColors.Cyan,
) {
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !rememberReducedMotion()) 0.9f else 1f,
        animationSpec = EiliotMotion.springSoft(),
        label = "iconPress",
    )
    Box(
        modifier
            .scale(scale)
            .size(48.dp)
            .clip(CircleShape)
            .background(accent.copy(alpha = if (enabled) 0.14f else 0.06f))
            .border(
                1.dp,
                accent.copy(alpha = if (enabled) 0.45f else 0.18f),
                CircleShape,
            )
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled,
                onClickLabel = contentDescription,
                role = Role.Button,
            ) {
                HudHaptics.tap(view)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) accent else EiliotColors.TextMuted,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Small live-status pill: dot + mono label. Green-ish when [ok]. */
@Composable
fun StatusChip(
    label: String,
    ok: Boolean,
    modifier: Modifier = Modifier,
    okColor: Color = EiliotColors.Teal,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0x330A1424))
            .border(
                1.dp,
                (if (ok) okColor else EiliotColors.TextMuted).copy(alpha = 0.4f),
                RoundedCornerShape(50),
            )
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (ok) okColor else EiliotColors.TextMuted),
        )
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = if (ok) EiliotColors.TextPrimary else EiliotColors.TextSecondary,
        )
    }
}

/** Section heading — a cyan tick + mono uppercase label. */
@Composable
fun SectionLabel(text: String, accent: Color = EiliotColors.Cyan) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(width = 14.dp, height = 2.dp)
                .background(accent),
        )
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = accent,
        )
    }
}

/** Tiny selectable chip (orb-state picker, suggestions). */
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
            .background(if (selected) accent.copy(alpha = 0.22f) else Color(0x1A0B1626))
            .border(
                1.dp,
                if (selected) accent.copy(alpha = 0.7f) else EiliotColors.TextMuted.copy(alpha = 0.3f),
                RoundedCornerShape(50),
            )
            .clickable(onClickLabel = label, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) accent else EiliotColors.TextSecondary,
        )
    }
}

/** Shared glass styling for every text field in the app. */
@Composable
fun hudFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = EiliotColors.TextPrimary,
    unfocusedTextColor = EiliotColors.TextPrimary,
    focusedContainerColor = Color(0x2E0B1626),
    unfocusedContainerColor = Color(0x1F0A1424),
    focusedBorderColor = EiliotColors.Cyan.copy(alpha = 0.85f),
    unfocusedBorderColor = EiliotColors.Cyan.copy(alpha = 0.22f),
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
