package com.eiliot.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eiliot.app.capabilities.*

private fun capabilityColor(state: Availability) = when (state) {
    Availability.READY -> EiliotColors.Teal
    Availability.SETUP, Availability.UNAVAILABLE -> EiliotColors.Amber
    Availability.CONDITIONAL, Availability.UNKNOWN -> EiliotColors.Violet
    else -> EiliotColors.TextSecondary
}

@Composable fun CapabilityBadge(label: String, state: Availability, modifier: Modifier = Modifier) {
    val tint = capabilityColor(state)
    Text(label, color = tint, fontSize = 11.sp, modifier = modifier
        .background(tint.copy(alpha = 0.09f), RoundedCornerShape(8.dp)).padding(horizontal = 9.dp, vertical = 6.dp)
        .semantics { stateDescription = state.label })
}

/** Readiness is observed, never changed by expanding/searching this screen. */
@Composable fun CapabilityDashboard(onControls: () -> Unit) {
    val snapshot by CapabilityRegistry.snapshot.collectAsStateWithLifecycle()
    val session by CapabilityRegistry.session.collectAsStateWithLifecycle()
    CapabilityDashboardContent(snapshot, session, onControls = onControls,
        onRefresh = { CapabilityRegistry.refresh() },
        onRecover = { CapabilityRegistry.recovery(it, onControls) })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun CapabilityDashboardContent(
    snapshot: CapabilitySnapshot, session: SessionPresentation,
    onControls: () -> Unit, onRefresh: () -> Unit, onRecover: (Recovery) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("All") }
    val groups = listOf("All", "Needs setup", "Phone", "Voice", "Screen", "Internet", "Memory", "Models", "Initiative", "Interface")
    val entries = snapshot.entries.filter { e ->
        (filter == "All" || (filter == "Needs setup" && !e.canAttempt && e.availability != Availability.DISABLED) || filter == e.group) &&
            (query.isBlank() || "${e.title} ${e.id} ${e.reason}".contains(query.trim(), ignoreCase = true))
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassPanel(accent = EiliotColors.Teal, contentPadding = PaddingValues(16.dp)) {
            Text("WHAT I CAN DO", color = EiliotColors.Teal, fontSize = 10.sp, letterSpacing = 1.sp)
            Spacer(Modifier.height(8.dp))
            Text("${snapshot.readyTools} ready · ${snapshot.availableTools - snapshot.readyTools} conditional", color = EiliotColors.TextPrimary,
                style = MaterialTheme.typography.titleLarge)
            Text("All 16 tools are retained. Ready means prerequisites are met; the result is verified when the tool runs.", color = EiliotColors.TextSecondary, fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp))
            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = EiliotColors.Cyan.copy(alpha = 0.15f))
            Text("${session.phase.label} · ${session.activityCount} task steps", color = EiliotColors.Teal, fontSize = 12.sp)
            Text(session.detail, color = EiliotColors.TextSecondary, fontSize = 12.sp, maxLines = 3,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onRefresh) { Text("Refresh readiness") }
                TextButton(onClick = onControls) { Text("Settings") }
            }
        }
        HudTextField(query, { query = it }, "Find a capability or missing permission", modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            groups.forEach { group -> FilterChip(filter == group, { filter = group }, label = { Text(group) }) }
        }
        Text("${entries.size} capabilities · ${if (snapshot.observedAt == 0L) "checking device" else "live device observations"}", color = EiliotColors.TextMuted, fontSize = 11.sp)
        if (entries.isEmpty()) GlassPanel { Text("No matching capabilities. Try another search or choose All.", color = EiliotColors.TextSecondary) }
        entries.forEach { entry -> key(entry.id) { CapabilityCard(entry, onRecover) } }
        Text("Settings do not prove provider access, acoustic accuracy or task completion. Permissions are checked again when actions run.", color = EiliotColors.TextMuted, fontSize = 11.sp)
    }
}

@Composable private fun CapabilityCard(entry: CapabilityEntry, onRecover: (Recovery) -> Unit) {
    val tint = capabilityColor(entry.availability)
    GlassPanel(accent = tint, glow = 4.dp, contentPadding = PaddingValues(15.dp), modifier = Modifier.fillMaxWidth()) {
        // Stack the label and state so large text never competes for a narrow row.
        Text(entry.group.uppercase(), color = EiliotColors.TextMuted, fontSize = 10.sp, letterSpacing = 0.8.sp)
        Text(entry.title, color = EiliotColors.TextPrimary, style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp, bottom = 7.dp))
        CapabilityBadge(entry.availability.label, entry.availability)
        Text(entry.reason, color = EiliotColors.TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 9.dp))
        if (entry.recovery != Recovery.NONE) TextButton(onClick = { onRecover(entry.recovery) }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(entry.recovery.label, color = tint)
        }
    }
}

@Composable fun ReadinessStrip(snapshot: CapabilitySnapshot, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).clickable(role = Role.Button, onClick = onClick)
        .semantics { contentDescription = "Open live capabilities" }.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("voice_input" to "Voice", "wake_word" to "Wake", "read_screen" to "Screen", "recall_memory" to "Memory").forEach { (id, title) ->
            snapshot.entry(id)?.let { CapabilityBadge("$title · ${it.availability.label}", it.availability) }
        }
    }
}
