package com.eiliot.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Holographic glass. A smoked, slightly blue pane floating over deep space:
 *  - layered fill: dark translucent body with a cool light falling from the
 *    top edge, so the starfield still glows through;
 *  - a diagonal specular sheen across the upper-left, like light on glass;
 *  - a spectral hairline edge that fades from the accent into violet;
 *  - HUD corner brackets that frame the pane like a projected display;
 *  - a soft accent glow underneath.
 *
 * All of it is cached per size, so panels cost nothing per frame.
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    accent: Color = EiliotColors.Cyan,
    glow: Dp = 14.dp,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .shadow(glow, shape, clip = false, ambientColor = accent.copy(alpha = 0.16f), spotColor = accent.copy(alpha = 0.26f))
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xD20D1530), Color(0xC4070C1E), Color(0xD0060A19))))
            .holoSurface(accent)
            .border(
                1.dp,
                Brush.linearGradient(listOf(accent.copy(alpha = 0.70f), EiliotColors.Violet.copy(alpha = 0.18f), accent.copy(alpha = 0.10f), EiliotColors.Magenta.copy(alpha = 0.30f))),
                shape,
            )
            .padding(contentPadding),
        content = content,
    )
}

/** Sheen, top light and corner brackets shared by glass panels and the composer deck. */
fun Modifier.holoSurface(accent: Color, brackets: Boolean = true): Modifier = drawWithCache {
    val w = size.width
    val h = size.height
    val sheen = Brush.linearGradient(
        0f to Color.White.copy(alpha = 0.075f), 0.32f to Color.White.copy(alpha = 0.015f), 0.33f to Color.Transparent,
        start = Offset.Zero, end = Offset(w * 0.75f, h),
    )
    val topLight = Brush.verticalGradient(listOf(accent.copy(alpha = 0.09f), Color.Transparent), endY = h * 0.35f)
    val inset = 9.dp.toPx()
    val arm = 12.dp.toPx().coerceAtMost(w / 5f)
    val bracket = Path().apply {
        moveTo(inset, inset + arm); lineTo(inset, inset); lineTo(inset + arm, inset)
        moveTo(w - inset - arm, h - inset); lineTo(w - inset, h - inset); lineTo(w - inset, h - inset - arm)
    }
    val stroke = Stroke(1.4.dp.toPx(), cap = StrokeCap.Round)
    onDrawWithContent {
        drawRect(topLight)
        drawRect(sheen)
        if (brackets && w > arm * 4 && h > arm * 3) drawPath(bracket, accent.copy(alpha = 0.55f), style = stroke)
        drawContent()
    }
}
