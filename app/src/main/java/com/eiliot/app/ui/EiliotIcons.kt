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

    private fun stroked(name: String, block: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.7f,
                strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
                strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
                pathBuilder = block,
            ).build()

    /** Companion: a core inside a tilted orbit. */
    val Orb: ImageVector by lazy {
        stroked("EiliotOrb") {
            // Core.
            moveTo(16f, 12f)
            arcTo(4f, 4f, 0f, true, true, 8f, 12f)
            arcTo(4f, 4f, 0f, true, true, 16f, 12f)
            close()
            // Orbit ellipse.
            moveTo(21.5f, 12f)
            arcTo(9.5f, 3.6f, 0f, true, true, 2.5f, 12f)
            arcTo(9.5f, 3.6f, 0f, true, true, 21.5f, 12f)
            close()
        }
    }

    /** Vault: a hexagonal safe with a keyhole. */
    val Vault: ImageVector by lazy {
        stroked("EiliotVault") {
            moveTo(12f, 2.5f)
            lineTo(20.3f, 7.25f)
            lineTo(20.3f, 16.75f)
            lineTo(12f, 21.5f)
            lineTo(3.7f, 16.75f)
            lineTo(3.7f, 7.25f)
            close()
            moveTo(13.6f, 10.4f)
            arcTo(1.6f, 1.6f, 0f, true, true, 10.4f, 10.4f)
            arcTo(1.6f, 1.6f, 0f, true, true, 13.6f, 10.4f)
            close()
            moveTo(12f, 12f)
            lineTo(12f, 15.2f)
        }
    }

    /** Settings: three tuning sliders. */
    val Tune: ImageVector by lazy {
        stroked("EiliotTune") {
            moveTo(4f, 6f); lineTo(20f, 6f)
            moveTo(4f, 12f); lineTo(20f, 12f)
            moveTo(4f, 18f); lineTo(20f, 18f)
            moveTo(10.8f, 6f)
            arcTo(1.8f, 1.8f, 0f, true, true, 7.2f, 6f)
            arcTo(1.8f, 1.8f, 0f, true, true, 10.8f, 6f)
            moveTo(17.3f, 12f)
            arcTo(1.8f, 1.8f, 0f, true, true, 13.7f, 12f)
            arcTo(1.8f, 1.8f, 0f, true, true, 17.3f, 12f)
            moveTo(9.3f, 18f)
            arcTo(1.8f, 1.8f, 0f, true, true, 5.7f, 18f)
            arcTo(1.8f, 1.8f, 0f, true, true, 9.3f, 18f)
        }
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
