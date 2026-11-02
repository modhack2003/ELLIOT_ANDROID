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
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("ELLIOT", color = EiliotColors.TextPrimary, fontSize = 25.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 3.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(6.dp).background(if(enabled)EiliotColors.Teal else EiliotColors.TextSecondary,androidx.compose.foundation.shape.CircleShape))
                Text(presence.phase.label, color = if(enabled)EiliotColors.Teal else EiliotColors.TextSecondary, fontSize = 12.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
            }
        }
        Button(onClick = {
            if (enabled) SentinelRuntime.stop()
            else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) SentinelRuntime.start(context, true)
            else {
                resumeRequest = PermissionRequest(SentinelRuntime.epoch, needsRunning = false)
                launcher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }, modifier=Modifier.heightIn(min=48.dp),enabled = !erasing, shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = if (enabled) Color(0xFF3B1F24) else Color(0xFF103C40), contentColor = if (enabled) Color(0xFFFFA6A8) else EiliotColors.Teal)) {
            Text(if (enabled) "Turn off" else "Resume", fontSize = 12.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
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
            BlockKind.CODE -> Box(Modifier.fillMaxWidth().background(Color(0xFF050B15), RoundedCornerShape(12.dp)).border(1.dp, Color(0xFF20324B), RoundedCornerShape(12.dp)).padding(12.dp).horizontalScroll(rememberScrollState())) {
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
        GlassPanel(modifier = Modifier.fillMaxWidth(if (user) 0.90f else 1f).then(if(reduced) Modifier else Modifier.animateContentSize()),
            accent = if (user) EiliotColors.Violet else EiliotColors.Cyan, glow = 5.dp,
            contentPadding = PaddingValues(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (user) "YOU" else "ELLIOT", color = if (user) EiliotColors.Violet else EiliotColors.Teal, fontSize = 9.sp, letterSpacing = 1.sp,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f))
                Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(message.time), color = EiliotColors.TextMuted, fontSize = 10.sp,maxLines=1)
            }
            Spacer(Modifier.height(10.dp))
            val body = if (visible.isBlank() && live) "Thinking…" else visible
            val preview = OutputFormat.prefix(body, 1800, 18)
            val truncated = preview.length < body.length
            Box(Modifier.fillMaxWidth()) {
                StructuredOutput(if (!expanded && truncated) "$preview…" else body)
                if(!expanded && truncated)Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(24.dp).background(Brush.verticalGradient(listOf(Color.Transparent,EiliotColors.Panel))))
            }
            if (truncated) OutlinedButton(onClick = { expanded = !expanded },shape=RoundedCornerShape(50)) { Text(if (expanded) "Collapse message" else "Expand full message", color = EiliotColors.Cyan,maxLines=1,overflow=TextOverflow.Ellipsis) }
            if (live) Text("▎", color = EiliotColors.Cyan, fontSize = 14.sp)
        }
    }
}
