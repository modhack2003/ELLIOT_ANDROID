package com.eiliot.app.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eiliot.app.core.*
import com.eiliot.app.data.MemoryMessage
import com.eiliot.app.overlay.*
import com.eiliot.app.voice.VoiceIo
import com.eiliot.app.capabilities.*

@Composable fun HomeScreen(onCapabilities:()->Unit={}) {
    val context=LocalContext.current
    val enabled by SentinelRuntime.enabled.collectAsStateWithLifecycle()
    val state by EiliotBrain.state.collectAsStateWithLifecycle()
    val messages by EiliotBrain.messages.collectAsStateWithLifecycle()
    val listening by VoiceIo.isListening.collectAsStateWithLifecycle()
    val speaking by VoiceIo.isSpeaking.collectAsStateWithLifecycle()
    val transcript by VoiceIo.transcript.collectAsStateWithLifecycle()
    val input by EiliotBrain.draft.collectAsStateWithLifecycle()
    val presence by CapabilityRegistry.session.collectAsStateWithLifecycle()
    val live by WakeListener.liveCall.collectAsStateWithLifecycle()
    val wakeStatus by WakeListener.status.collectAsStateWithLifecycle()
    val snapshot by ScreenReader.snapshot.collectAsStateWithLifecycle()
    val keyboard=WindowInsets.ime.getBottom(LocalDensity.current)>0
    var quick by remember { mutableStateOf(false) }
    var startCall by remember { mutableStateOf(false) }
    var permissionEpoch by remember { mutableLongStateOf(0) }
    val mic=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if(permissionEpoch==SentinelRuntime.epoch && SentinelRuntime.alive()) {
            if(granted) {
                val call=startCall
                SentinelRuntime.startAndArmMic(context) { if(call)WakeListener.setLiveCall(context,true)else EiliotBrain.tapToTalk(context) }
            }else EiliotBrain.notice("You can keep using text. Microphone access is in Settings.")
        }
    }
    val orbState=when(presence.phase) {
        SessionPhase.LISTENING->OrbState.LISTENING
        SessionPhase.SPEAKING->OrbState.SPEAKING
        SessionPhase.WORKING,SessionPhase.REVIEW->OrbState.THINKING
        else->OrbState.IDLE
    }
    val scroll=rememberLazyListState()
    val dragging by scroll.interactionSource.collectIsDraggedAsState()
    val atLatest by remember { derivedStateOf { scroll.firstVisibleItemIndex==0 && scroll.firstVisibleItemScrollOffset<48 } }
    var follow by remember { mutableStateOf(true) }
    LaunchedEffect(dragging,atLatest) { if(atLatest)follow=true else if(dragging)follow=false }
    LaunchedEffect(state.turnId) { follow=true;if(scroll.layoutInfo.totalItemsCount>0)scroll.scrollToItem(0) }
    LaunchedEffect(messages.lastOrNull()?.id,state.reply.length,state.busy,state.audioPending,follow,scroll.layoutInfo.viewportEndOffset) {
        if(follow && !dragging && scroll.layoutInfo.totalItemsCount>0)scroll.scrollToItem(0)
    }
    val partial=state.reply.isNotBlank() && (state.error!=null || state.status in setOf("Response stopped","Interrupted")) &&
        messages.lastOrNull()?.let { it.role=="assistant" && it.text==state.reply }!=true
    BoxWithConstraints(Modifier.fillMaxSize().imePadding()) {
        val split=maxWidth>=600.dp
        val shortWindow=maxHeight<400.dp
        Row(Modifier.fillMaxSize()) {
            if(split)Column(Modifier.weight(.34f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(16.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
                CompanionVisualizer(orbState,live,enabled,if(shortWindow)100 else 180)
                Text(if(live)"Live call" else "Here with you",style=MaterialTheme.typography.titleLarge,color=EiliotColors.TextPrimary,maxLines=1,overflow=TextOverflow.Ellipsis)
                Text(if(listening)transcript.ifBlank { "I'm listening" }else presence.detail,color=EiliotColors.TextSecondary,maxLines=3,overflow=TextOverflow.Ellipsis)
                if(state.activities.isNotEmpty())ToolActivityCard(state.activities)
            }
            Column(Modifier.weight(if(split).66f else 1f).fillMaxHeight().padding(horizontal=16.dp)) {
                if(live && !split && !keyboard)Row(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                    CompanionVisualizer(orbState,true,enabled,72)
                    Column(Modifier.weight(1f).padding(start=12.dp)) {
                        Text("Live call",color=EiliotColors.Teal,maxLines=1)
                        Text(if(listening)transcript.ifBlank { "Speak naturally" }else if(WakeListener.available())"You can interrupt by speaking." else wakeStatus,color=EiliotColors.TextSecondary,maxLines=2,overflow=TextOverflow.Ellipsis)
                    }
                }
                LazyColumn(Modifier.weight(1f).fillMaxWidth(),state=scroll,reverseLayout=true,verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(vertical=12.dp)) {
                    state.error?.let { error -> item("error") { GlassPanel(accent=EiliotColors.Amber) {
                        Text(FriendlyStatus.error(error),color=EiliotColors.TextPrimary)
                        TextButton(onClick=onCapabilities) { Text("Open Settings",maxLines=1) }
                    } } }
                    snapshot?.let { screen -> item("screen") { GlassPanel {
                        Text("Selected screen",color=EiliotColors.Teal,maxLines=1)
                        Text(screen.text.take(350),color=EiliotColors.TextSecondary,maxLines=5,overflow=TextOverflow.Ellipsis)
                        Text("Explain shares this selection with your chosen model.",color=EiliotColors.TextSecondary,fontSize=12.sp)
                        Row { TextButton(onClick={EiliotBrain.explainScreen()}) {Text("Explain",maxLines=1)};TextButton(onClick={ScreenReader.clear()}) {Text("Discard",maxLines=1)} }
                    } } }
                    if(state.busy || state.audioPending || partial)item("live_${state.turnId}") {
                        MessageCard(MemoryMessage(Long.MIN_VALUE,"assistant",state.reply,System.currentTimeMillis(),if(partial)"interrupted" else state.source),live=state.busy)
                    }
                    if(!split && state.activities.isNotEmpty())item("actions_${state.turnId}") {ToolActivityCard(state.activities)}
                    items(messages.asReversed(),key={it.id}) { MessageCard(it) }
                    if(messages.isEmpty() && !state.busy && !state.audioPending)item("welcome") { GlassPanel(contentPadding=PaddingValues(22.dp)) {
                        Text("Hello, friend.",style=MaterialTheme.typography.headlineSmall,color=EiliotColors.TextPrimary)
                        Spacer(Modifier.height(12.dp))
                        Text("Ask about your phone, find something online, or start a Live Call.",color=EiliotColors.TextSecondary)
                        Text("Your clear request is enough for supported actions.",color=EiliotColors.Teal,fontSize=13.sp,modifier=Modifier.padding(top=12.dp))
                    } }
                }
                if(!follow && !atLatest)TextButton(onClick={follow=true}) {Text("Latest ↓",maxLines=1)}
                Surface(color=EiliotColors.Panel,shape=MaterialTheme.shapes.large,modifier=Modifier.fillMaxWidth().padding(bottom=8.dp)) {
                    Column(Modifier.padding(10.dp)) {
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            HudTextField(input,{EiliotBrain.draft.value=it},if(enabled)"Message ELLIOT" else "ELLIOT is off",singleLine=false,maxLines=3,
                                imeAction=ImeAction.Send,onImeAction={if(enabled && EiliotBrain.send(input))EiliotBrain.draft.value=""},modifier=Modifier.weight(1f))
                            Spacer(Modifier.width(8.dp))
                            GlowIconButton(Icons.AutoMirrored.Filled.Send,"Send message",{if(EiliotBrain.send(input))EiliotBrain.draft.value=""},enabled=enabled && input.isNotBlank() && !state.busy)
                        }
                        Row(Modifier.fillMaxWidth().padding(top=6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                            GlowIconButton(Icons.Default.Build,"Action shortcuts",{quick=true},enabled=enabled)
                            GlowIconButton(if(listening || speaking || state.busy)Icons.Default.Close else EiliotIcons.Mic,if(listening || speaking || state.busy)"Stop response" else "Talk to ELLIOT",{
                                if(listening || speaking || state.busy)EiliotBrain.cancelCurrent()
                                else {startCall=false;permissionEpoch=SentinelRuntime.epoch;mic.launch(Manifest.permission.RECORD_AUDIO)}
                            },enabled=enabled,accent=EiliotColors.Teal)
                            FilterChip(live,{
                                if(live)WakeListener.setLiveCall(context,false)
                                else {startCall=true;permissionEpoch=SentinelRuntime.epoch;mic.launch(Manifest.permission.RECORD_AUDIO)}
                            },enabled=enabled,modifier=Modifier.weight(1f).heightIn(min=48.dp).semantics {contentDescription=if(live)"End live call" else "Start live call"},
                                label={Text(if(live)"End call" else "Live Call",maxLines=1,overflow=TextOverflow.Ellipsis)})
                        }
                    }
                }
            }
        }
    }
    if(quick)QuickActionsSheet(onDismiss={quick=false},onSettings=onCapabilities)
}

@Composable internal fun CompanionVisualizer(state:OrbState,live:Boolean,enabled:Boolean,size:Int) {
    val appearance=LocalOverlayAppearance.current
    val amplitude by com.eiliot.app.voice.NeuralSpeechPlayer.amplitude.collectAsStateWithLifecycle()
    AndroidView(factory={ArcCoreView(it)},update={it.setState(state);it.setAmplitude(amplitude);it.setPresentation(appearance.motion,appearance.haptics);it.setPaused(!enabled)},
        modifier=Modifier.size(size.dp).semantics {contentDescription=if(live)"Live voice activity" else "ELLIOT presence"})
}

