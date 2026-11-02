package com.eiliot.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Hand-authored vector glyphs (24 dp viewport, classic Material geometry) so
 * the HUD gets its mic and power icons without pulling the 2 MB
 * material-icons-extended pack into the APK.
 */
object EiliotIcons {

    /** Microphone — the voice-first heart of the Conversation screen. */
    val Mic: ImageVector by lazy {
        ImageVector.Builder(
            name = "EiliotMic",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).path(fill = SolidColor(Color.Black)) {
            // Capsule.
            moveTo(12f, 14f)
            curveTo(13.66f, 14f, 15f, 12.66f, 15f, 11f)
            lineTo(15f, 5f)
            curveTo(15f, 3.34f, 13.66f, 2f, 12f, 2f)
            curveTo(10.34f, 2f, 9f, 3.34f, 9f, 5f)
            lineTo(9f, 11f)
            curveTo(9f, 12.66f, 10.34f, 14f, 12f, 14f)
            close()
            // Cradle + stem.
            moveTo(17.3f, 11f)
            curveTo(17.3f, 14f, 14.76f, 16.1f, 12f, 16.1f)
            curveTo(9.24f, 16.1f, 6.7f, 14f, 6.7f, 11f)
            lineTo(5f, 11f)
            curveTo(5f, 14.41f, 7.72f, 17.23f, 11f, 17.72f)
            lineTo(11f, 21f)
            lineTo(13f, 21f)
            lineTo(13f, 17.72f)
            curveTo(16.28f, 17.23f, 19f, 14.41f, 19f, 11f)
            lineTo(17.3f, 11f)
            close()
        }.build()
    }

    /** Power — wake the Sentinel Core. */
    val Power: ImageVector by lazy {
        ImageVector.Builder(
            name = "EiliotPower",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(13f, 3f)
            lineTo(11f, 3f)
            lineTo(11f, 13f)
            lineTo(13f, 13f)
            lineTo(13f, 3f)
            close()
            moveTo(17.83f, 5.17f)
            lineTo(16.41f, 6.59f)
            curveTo(17.99f, 7.86f, 19f, 9.81f, 19f, 12f)
            curveTo(19f, 15.87f, 15.87f, 19f, 12f, 19f)
            curveTo(8.13f, 19f, 5f, 15.87f, 5f, 12f)
            curveTo(5f, 9.81f, 6.01f, 7.86f, 7.58f, 6.58f)
            lineTo(6.17f, 5.17f)
            curveTo(4.23f, 6.82f, 3f, 9.26f, 3f, 12f)
            curveTo(3f, 16.97f, 7.03f, 21f, 12f, 21f)
            curveTo(16.97f, 21f, 21f, 16.97f, 21f, 12f)
            curveTo(21f, 9.26f, 19.77f, 6.82f, 17.83f, 5.17f)
            close()
        }.build()
    }
}
