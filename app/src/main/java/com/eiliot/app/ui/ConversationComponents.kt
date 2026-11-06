package com.eiliot.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eiliot.app.core.*
import com.eiliot.app.data.MemoryMessage
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Locale

@Composable fun PowerHeader() {
    val context = LocalContext.current
    val enabled by SentinelRuntime.enabled.collectAsStateWithLifecycle()
    val presence by com.eiliot.app.capabilities.CapabilityRegistry.session.collectAsStateWithLifecycle()
    val erasing by SentinelRuntime.erasing.collectAsStateWithLifecycle()
    var resumeRequest by remember { mutableStateOf<PermissionRequest?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val request = resumeRequest; resumeRequest = null
        if (request?.isCurrent(SentinelRuntime.epoch, SentinelRuntime.alive(), SentinelRuntime.erasing.value) == true)
            SentinelRuntime.start(context, granted)
    }
    val clock = LocalHoloClock.current
    val view = androidx.compose.ui.platform.LocalView.current
    val beacon = if (enabled) EiliotColors.Teal else EiliotColors.TextMuted
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 16.dp, top = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            // The wordmark is a logo, so it keeps its drawn size; status text below still scales.
            val mark = with(androidx.compose.ui.platform.LocalDensity.current) { 26.dp.toSp() to 10.dp.toSp() }
            Text("ELLIOT", style = MaterialTheme.typography.displaySmall.copy(fontSize = mark.first, letterSpacing = mark.second), color = EiliotColors.TextPrimary,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.aurora(clock, speed = 0.05f))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Box(Modifier.size(8.dp).drawBehind {
                    val pulse = if (enabled) (clock.seconds * 0.7f) % 1f else 0f
                    if (enabled) drawCircle(beacon.copy(alpha = 0.5f * (1f - pulse)), radius = size.minDimension * (0.5f + 0.9f * pulse))
                    drawCircle(beacon, radius = size.minDimension * 0.32f)
                })
                Text(presence.phase.label.uppercase(), style = MaterialTheme.typography.labelSmall, color = beacon, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        val tone = if (enabled) EiliotColors.Magenta else EiliotColors.Teal
        val shape = RoundedCornerShape(50)
        Row(
            Modifier.heightIn(min = 48.dp)
                .shadow(if (erasing) 0.dp else 10.dp, shape, clip = false, ambientColor = tone.copy(alpha = 0.4f), spotColor = tone.copy(alpha = 0.6f))
                .clip(shape)
                .background(Brush.verticalGradient(listOf(tone.copy(alpha = 0.26f), Color(0xE0080D20))))
                .border(1.dp, Brush.horizontalGradient(listOf(tone.copy(alpha = 0.9f), EiliotColors.Violet.copy(alpha = 0.4f))), shape)
                .clickable(enabled = !erasing, role = androidx.compose.ui.semantics.Role.Button) {
                    HudHaptics.tap(view)
                    if (enabled) SentinelRuntime.stop()
                    else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) SentinelRuntime.start(context, true)
                    else {
                        resumeRequest = PermissionRequest(SentinelRuntime.epoch, needsRunning = false)
                        launcher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
                .padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(EiliotIcons.Power, null, tint = if (erasing) EiliotColors.TextMuted else tone, modifier = Modifier.size(18.dp))
            Text(if (enabled) "Turn off" else "Resume", style = MaterialTheme.typography.labelLarge, color = if (erasing) EiliotColors.TextMuted else EiliotColors.TextPrimary,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable fun InlineRichText(text: String, color: Color = EiliotColors.TextPrimary) {
    val annotated = remember(text) {
        buildAnnotatedString {
            val pattern = Regex("(\\*\\*[^*]+\\*\\*|`[^`]+`)")
            var index = 0
            for (m in pattern.findAll(text)) {
                append(text.substring(index, m.range.first))
                val code = m.value.startsWith('`')
                withStyle(SpanStyle(fontWeight = if (code) FontWeight.Normal else FontWeight.SemiBold, fontFamily = if (code) FontFamily.Monospace else FontFamily.Default,
                    color = if (code) EiliotColors.Teal else color)) { append(if (code) m.value.removeSurrounding("`") else m.value.removeSurrounding("**")) }
                index = m.range.last + 1
            }
            append(text.substring(index))
        }
    }
    Text(annotated, color = color, fontSize = 15.sp, lineHeight = 23.sp)
}

@Composable fun StructuredOutput(text: String, modifier: Modifier = Modifier) {
    val blocks = remember(text) { OutputFormat.blocks(text) }
    SelectionContainer { Column(modifier, verticalArrangement = Arrangement.spacedBy(9.dp)) {
        blocks.forEach { block -> when (block.kind) {
            BlockKind.HEADING -> Text(block.text.replace("**", ""), color = EiliotColors.Cyan, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            BlockKind.BULLET -> Row { Text("•  ", color = EiliotColors.Teal); InlineRichText(block.text) }
            BlockKind.CODE -> Box(Modifier.fillMaxWidth().background(Color(0xE6030616), RoundedCornerShape(14.dp)).border(1.dp, Brush.horizontalGradient(listOf(EiliotColors.Teal.copy(alpha = 0.45f), EiliotColors.Violet.copy(alpha = 0.25f))), RoundedCornerShape(14.dp)).padding(12.dp).horizontalScroll(rememberScrollState())) {
                Text(block.text, fontFamily = FontFamily.Monospace, color = Color(0xFFB9E9D8), fontSize = 12.sp, lineHeight = 18.sp)
            }
            else -> InlineRichText(block.text)
        } }
    } }
}

@Composable fun MessageCard(message: MemoryMessage, live: Boolean = false) {
    var shown by remember(message.id) { mutableIntStateOf(if (live) 0 else message.text.length) }
    val appearance = LocalOverlayAppearance.current
    val reduced = rememberReducedMotion() || appearance.motion == "reduced"
    LaunchedEffect(message.text, live, reduced, appearance.typing) {
        if (!live || reduced || !appearance.typing) { shown = message.text.length; return@LaunchedEffect }
        if (shown > message.text.length) shown = 0
        while (shown < message.text.length) {
            shown = OutputFormat.advance(message.text, shown, if (message.text.length - shown > 150) 16 else 3)
            delay(18)
        }
    }
    var expanded by remember(message.id) { mutableStateOf(false) }
    val visible = message.text.take(shown.coerceAtMost(message.text.length))
    val user = message.role == "user"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
        val tone = if (user) EiliotColors.Magenta else EiliotColors.Cyan
        GlassPanel(modifier = Modifier.fillMaxWidth(if (user) 0.88f else 1f).then(if(reduced) Modifier else Modifier.animateContentSize()),
            shape = if (user) RoundedCornerShape(24.dp, 24.dp, 8.dp, 24.dp) else RoundedCornerShape(24.dp, 24.dp, 24.dp, 8.dp),
            accent = tone, glow = 8.dp,
            contentPadding = PaddingValues(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(6.dp).background(Brush.radialGradient(listOf(Color.White, tone)), androidx.compose.foundation.shape.CircleShape))
                    Text(if (user) "YOU" else "ELLIOT", color = tone, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(message.time), color = EiliotColors.TextMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
            Spacer(Modifier.height(10.dp))
            val body = if (visible.isBlank() && live) "Thinking…" else visible
            val preview = OutputFormat.prefix(body, 1800, 18)
            val truncated = preview.length < body.length
            Box(Modifier.fillMaxWidth()) {
                StructuredOutput(if (!expanded && truncated) "$preview…" else body)
                if(!expanded && truncated)Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(28.dp).background(Brush.verticalGradient(listOf(Color.Transparent,Color(0xF0070C1E)))))
            }
            if (truncated) OutlinedButton(onClick = { expanded = !expanded },shape=RoundedCornerShape(50)) { Text(if (expanded) "Collapse message" else "Expand full message", color = EiliotColors.Cyan,maxLines=1,overflow=TextOverflow.Ellipsis) }
            if (live) Text("▎", color = EiliotColors.Cyan, fontSize = 14.sp)
        }
    }
}
