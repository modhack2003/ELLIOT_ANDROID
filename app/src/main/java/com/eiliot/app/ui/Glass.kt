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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Frosted-glass panel — PERFORMANCE-FIRST rewrite.
 *
 * The previous version redrew the entire animated backdrop and GPU-blurred it
 * *per panel, every frame*. With 5-6 panels that was 5-6 full-screen shader +
 * RenderEffect passes per frame — the cause of the lag.
 *
 * This version keeps the glass aesthetic — a translucent dark gradient (the
 * live backdrop still glows softly through it), a neon hairline edge, and a
 * coloured glow shadow — with ZERO per-frame GPU work. Buttery smooth.
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
            .shadow(
                elevation = glow,
                shape = shape,
                clip = false,
                ambientColor = accent.copy(alpha = 0.10f),
                spotColor = accent.copy(alpha = 0.16f),
            )
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xCC0C1526), Color(0xB3081020)),
                ),
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(
                        accent.copy(alpha = 0.45f),
                        accent.copy(alpha = 0.12f),
                        accent.copy(alpha = 0.24f),
                    ),
                ),
                shape = shape,
            )
            .padding(contentPadding),
        content = content,
    )
}
