package com.eiliot.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.eiliot.app.data.SecureStore

/**
 * MORE — about + diagnostics. Room to grow (history, intel feeds, logs).
 */
@Composable
fun MoreScreen() {
    val ctx = LocalContext.current
    val store = remember { SecureStore(ctx) }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("MORE", style = MaterialTheme.typography.headlineLarge, color = EiliotColors.Cyan)

        GlassPanel {
            SectionLabel("About ELLIOT")
            Spacer(Modifier.height(10.dp))
            Text(
                "Embedded Intelligence Layer for Integrated Operations & Threat-hunting. " +
                    "A persistent, proactive, voice-first AI companion fused into Android.",
                style = MaterialTheme.typography.bodyMedium,
                color = EiliotColors.TextSecondary,
            )
            Spacer(Modifier.height(10.dp))
            Text("Version 0.1.0 · Phase 1", style = MaterialTheme.typography.labelSmall, color = EiliotColors.TextMuted)
        }

        GlassPanel(accent = EiliotColors.Teal) {
            SectionLabel("Diagnostics", accent = EiliotColors.Teal)
            Spacer(Modifier.height(10.dp))
            DiagRow("Endpoint", store.baseUrl.ifBlank { "—" })
            DiagRow("Model", store.model.ifBlank { "—" })
            DiagRow("API key", if (store.apiKey.isBlank()) "not set" else "set (vault)")
            DiagRow("Hover orb", if (store.orbEnabled) "enabled" else "disabled")
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun DiagRow(label: String, value: String) {
    Column {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = EiliotColors.TextMuted)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = EiliotColors.TextPrimary)
        Spacer(Modifier.height(8.dp))
    }
}
