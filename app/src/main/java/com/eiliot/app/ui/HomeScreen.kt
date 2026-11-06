package com.eiliot.app.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eiliot.app.capabilities.*
import com.eiliot.app.core.*
import com.eiliot.app.data.MemoryMessage
import com.eiliot.app.overlay.*
import com.eiliot.app.voice.VoiceIo
import kotlin.math.sin

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
    val reduced=rememberReducedMotion()
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
    val title=if(live)"Live call" else "Here with you"
    val detail=when {
        listening->transcript.ifBlank { if(live)"Speak naturally" else "I'm listening" }
        live->if(WakeListener.available())"You can interrupt by speaking." else wakeStatus
        else->presence.detail
    }
    val window=remember { ListWindow() }
    val seen=remember { mutableSetOf<Any>() }
    val empty=messages.isEmpty() && !state.busy && !state.audioPending
    fun talk() {startCall=false;permissionEpoch=SentinelRuntime.epoch;mic.launch(Manifest.permission.RECORD_AUDIO)}
    fun liveCall() {
        if(live)WakeListener.setLiveCall(context,false)
        else {startCall=true;permissionEpoch=SentinelRuntime.epoch;mic.launch(Manifest.permission.RECORD_AUDIO)}
    }

    BoxWithConstraints(Modifier.fillMaxSize().imePadding()) {
        val split=maxWidth>=600.dp
        val shortWindow=maxHeight<400.dp
        val available=maxHeight
        Row(Modifier.fillMaxSize()) {
            if(split)Column(Modifier.weight(.36f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(16.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(10.dp)) {
                HoloStage(orbState,if(shortWindow)92.dp else 190.dp,enabled=enabled,description=if(live)"Live voice activity" else "ELLIOT presence")
                PresenceCaption(title,detail,orbState,live,centered=true)
                if(state.activities.isNotEmpty())ToolActivityCard(state.activities)
            }
            Column(Modifier.weight(if(split).64f else 1f).fillMaxHeight().padding(horizontal=16.dp)) {
                if(!split && !keyboard) AnimatedContent(empty,label="hero",transitionSpec={
                    if(reduced)fadeIn(EiliotMotion.tweenFast()) togetherWith fadeOut(EiliotMotion.tweenFast())
                    else (fadeIn(EiliotMotion.tweenEnter())+scaleIn(EiliotMotion.tweenEnter(),initialScale=.9f)) togetherWith fadeOut(EiliotMotion.tweenFast())
                }) { wide ->
                    if(wide)Column(Modifier.fillMaxWidth().padding(top=4.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                        HoloStage(orbState,when { available>=720.dp->168.dp; available>=560.dp->132.dp; else->96.dp },enabled=enabled,description=if(live)"Live voice activity" else "ELLIOT presence")
                        PresenceCaption(title,detail,orbState,live,centered=true)
                    } else Row(Modifier.fillMaxWidth().padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically) {
                        HoloStage(orbState,74.dp,enabled=enabled,description=if(live)"Live voice activity" else "ELLIOT presence")
                        PresenceCaption(title,detail,orbState,live,centered=false,modifier=Modifier.weight(1f).padding(start=6.dp))
                    }
                }
                LazyColumn(Modifier.weight(1f).fillMaxWidth().onGloballyPositioned { window.top=it.positionInRoot().y;window.height=it.size.height.toFloat() },
                    state=scroll,reverseLayout=true,verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(vertical=12.dp)) {
                    state.error?.let { error -> item("error") { GlassPanel(accent=EiliotColors.Amber) {
                        Text(FriendlyStatus.error(error),color=EiliotColors.TextPrimary)
                        TextButton(onClick=onCapabilities) { Text("Open Settings",maxLines=1) }
                    } } }
                    snapshot?.let { screen -> item("screen") { GlassPanel(accent=EiliotColors.Teal) {
                        SectionLabel("Selected screen",EiliotColors.Teal)
                        Text(screen.text.take(350),color=EiliotColors.TextSecondary,maxLines=5,overflow=TextOverflow.Ellipsis,modifier=Modifier.padding(top=8.dp))
                        Text("Explain shares this selection with your chosen model.",color=EiliotColors.TextSecondary,fontSize=12.sp)
                        Row { TextButton(onClick={EiliotBrain.explainScreen()}) {Text("Explain",maxLines=1)};TextButton(onClick={ScreenReader.clear()}) {Text("Discard",maxLines=1)} }
                    } } }
                    if(state.busy || state.audioPending || partial)item("live_${state.turnId}") {
                        DepthItem("live_${state.turnId}",window,seen,!reduced) {
                            MessageCard(MemoryMessage(Long.MIN_VALUE,"assistant",state.reply,System.currentTimeMillis(),if(partial)"interrupted" else state.source),live=state.busy)
                        }
                    }
                    if(!split && state.activities.isNotEmpty())item("actions_${state.turnId}") {ToolActivityCard(state.activities)}
                    items(messages.asReversed(),key={it.id}) { message -> DepthItem(message.id,window,seen,!reduced) { MessageCard(message) } }
                    if(empty)item("welcome") { WelcomeCard(enabled) }
                }
                if(!follow && !atLatest)TextButton(onClick={follow=true}) {Text("Latest ↓",maxLines=1)}
                CommandDeck(
                    input=input,enabled=enabled,busy=state.busy,listening=listening,speaking=speaking,live=live,
                    onSend={if(enabled && EiliotBrain.send(input))EiliotBrain.draft.value=""},
                    onQuick={quick=true},
                    onMic={if(listening || speaking || state.busy)EiliotBrain.cancelCurrent() else talk()},
                    onLive={liveCall()},
                )
            }
        }
    }
    if(quick)QuickActionsSheet(onDismiss={quick=false},onSettings=onCapabilities)
}

/** Viewport bounds of the conversation, in root coordinates. */
private class ListWindow {
    var top by mutableFloatStateOf(0f)
    var height by mutableFloatStateOf(1f)
    /** 0 at the bottom edge, 1 at the top edge. */
    fun fraction(center:Float)=if(center.isNaN() || height<=1f)0f else 1f-((center-top)/height)
}

@Composable private fun DepthItem(key:Any,window:ListWindow,seen:MutableSet<Any>,enabled:Boolean,content:@Composable ()->Unit) {
    val center=remember { mutableFloatStateOf(Float.NaN) }
    val first=remember(key) { seen.add(key) }
    val arrival=remember(key) { Animatable(if(enabled && first)0f else 1f) }
    LaunchedEffect(key) { if(arrival.value<1f)arrival.animateTo(1f,EiliotMotion.tweenEnter()) }
    Box(Modifier
        .onGloballyPositioned { center.floatValue=it.positionInRoot().y+it.size.height/2f }
        .holoDepth({ window.fraction(center.floatValue) },{ arrival.value },enabled)) { content() }
}

@Composable private fun PresenceCaption(title:String,detail:String,state:OrbState,live:Boolean,centered:Boolean,modifier:Modifier=Modifier) {
    val clock=LocalHoloClock.current
    Column(modifier,horizontalAlignment=if(centered)Alignment.CenterHorizontally else Alignment.Start) {
        Text(title.uppercase(),style=MaterialTheme.typography.labelSmall.copy(fontSize=11.sp,letterSpacing=3.sp),
            color=if(live)EiliotColors.Teal else state.accent(),maxLines=1,overflow=TextOverflow.Ellipsis,
            modifier=if(live)Modifier else Modifier.aurora(clock,listOf(state.accent(),EiliotColors.TextPrimary,state.accent()),.12f))
        Text(detail,color=EiliotColors.TextSecondary,maxLines=if(centered)3 else 2,overflow=TextOverflow.Ellipsis,
            textAlign=if(centered)TextAlign.Center else TextAlign.Start,style=MaterialTheme.typography.bodyMedium,modifier=Modifier.padding(top=4.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun WelcomeCard(enabled:Boolean) {
    val clock=LocalHoloClock.current
    GlassPanel(contentPadding=PaddingValues(22.dp),accent=EiliotColors.Violet) {
        SectionLabel("Link established",EiliotColors.Teal)
        Text("Hello, friend.",style=MaterialTheme.typography.headlineSmall,color=EiliotColors.TextPrimary,modifier=Modifier.padding(top=10.dp).aurora(clock,speed=.06f))
        Spacer(Modifier.height(10.dp))
        Text("Ask about your phone, find something online, or start a Live Call.",color=EiliotColors.TextSecondary)
        Text("Your clear request is enough for supported actions.",color=EiliotColors.Teal,fontSize=13.sp,modifier=Modifier.padding(top=12.dp))
        FlowRow(Modifier.padding(top=14.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("What is my battery level?","What can you do?","Set a timer for 5 minutes").forEach { prompt ->
                if(enabled)SelectChip(prompt,false,{EiliotBrain.send(prompt)},EiliotColors.Cyan)
            }
        }
    }
}

/**
 * The command deck: a floating glass console whose edge carries a slowly
 * rotating aurora. Text on top; shortcuts, a large voice sphere and the Live
 * Call pill below.
 */
@Composable private fun CommandDeck(
    input:String,enabled:Boolean,busy:Boolean,listening:Boolean,speaking:Boolean,live:Boolean,
    onSend:()->Unit,onQuick:()->Unit,onMic:()->Unit,onLive:()->Unit,
) {
    val clock=LocalHoloClock.current
    val shape=RoundedCornerShape(28.dp)
    val active=listening || speaking || busy
    Column(Modifier.fillMaxWidth().padding(bottom=8.dp)
        .clip(shape)
        .background(Brush.verticalGradient(listOf(Color(0xE20E1734),Color(0xEC060A1A))))
        .holoSurface(EiliotColors.Cyan,brackets=false)
        .drawWithContent {
            drawContent()
            val sw=1.3.dp.toPx();val r=28.dp.toPx()
            drawRoundRect(rotatingAurora(clock.seconds*.07f,center,if(active)1f else .7f),Offset(sw/2,sw/2),Size(size.width-sw,size.height-sw),CornerRadius(r,r),style=Stroke(sw))
        }
        .padding(10.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            HudTextField(input,{EiliotBrain.draft.value=it},if(enabled)"Message ELLIOT" else "ELLIOT is off",singleLine=false,maxLines=3,
                imeAction=ImeAction.Send,onImeAction=onSend,modifier=Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            GlowIconButton(Icons.AutoMirrored.Filled.Send,"Send message",onSend,enabled=enabled && input.isNotBlank() && !busy)
        }
        Row(Modifier.fillMaxWidth().padding(top=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            GlowIconButton(Icons.Default.Build,"Action shortcuts",onQuick,enabled=enabled,accent=EiliotColors.Violet)
            VoiceSphere(active,listening,enabled,onMic)
            LiveCallPill(live,enabled,onLive,Modifier.weight(1f))
        }
    }
}

/** Aurora sweep whose colour stops rotate around the deck's edge. */
private fun rotatingAurora(phase:Float,center:Offset,strength:Float):Brush {
    val base=listOf(EiliotColors.Cyan.copy(alpha=.95f*strength),EiliotColors.Violet.copy(alpha=.35f*strength),EiliotColors.Magenta.copy(alpha=.8f*strength),
        EiliotColors.Violet.copy(alpha=.12f*strength),EiliotColors.Cyan.copy(alpha=.95f*strength))
    val shift=phase-kotlin.math.floor(phase)
    val stops=base.mapIndexed { i,c -> ((i/(base.size-1f))+shift)%1f to c }.sortedBy { it.first }.toMutableList()
    val edge=base.first()
    if(stops.first().first>0f)stops.add(0,0f to edge)
    if(stops.last().first<1f)stops.add(1f to stops.first().second)
    return Brush.sweepGradient(*stops.toTypedArray(),center=center)
}

/** The voice control: a larger lit sphere ringed by sonar waves while ELLIOT is listening or speaking. */
@Composable private fun VoiceSphere(active:Boolean,listening:Boolean,enabled:Boolean,onClick:()->Unit) {
    val clock=LocalHoloClock.current
    val tone=if(active)EiliotColors.Magenta else EiliotColors.Teal
    Box(Modifier.size(60.dp).drawBehind {
        if(!active || !enabled)return@drawBehind
        val r=size.minDimension/2f
        for(k in 0 until 3) {
            val p=((clock.seconds*(if(listening).8f else .55f))+k/3f)%1f
            drawCircle(tone.copy(alpha=.45f*(1f-p)),r*(.82f+.45f*p),style=Stroke(1.6.dp.toPx()))
        }
    },contentAlignment=Alignment.Center) {
        GlowIconButton(if(active)Icons.Default.Close else EiliotIcons.Mic,if(active)"Stop response" else "Talk to ELLIOT",onClick,enabled=enabled,accent=tone,size=54.dp)
    }
}

/** Live Call toggle: a glass pill with an equaliser that dances while the call is open. */
@Composable private fun LiveCallPill(live:Boolean,enabled:Boolean,onClick:()->Unit,modifier:Modifier=Modifier) {
    val clock=LocalHoloClock.current
    val view=androidx.compose.ui.platform.LocalView.current
    val interaction=remember { MutableInteractionSource() }
    val shape=RoundedCornerShape(50)
    val tone=if(live)EiliotColors.Teal else EiliotColors.Cyan
    Row(modifier.heightIn(min=48.dp)
        .clip(shape)
        .background(if(live)Brush.horizontalGradient(listOf(EiliotColors.Teal.copy(alpha=.38f),EiliotColors.Cyan.copy(alpha=.14f)))
            else Brush.horizontalGradient(listOf(Color(0x33152446),Color(0x1A0A1226))))
        .border(1.dp,Brush.horizontalGradient(listOf(tone.copy(alpha=if(enabled).85f else .25f),EiliotColors.Magenta.copy(alpha=if(enabled).45f else .15f))),shape)
        .clickable(interaction,LocalIndication.current,enabled=enabled,role=Role.Switch) {HudHaptics.tap(view);onClick()}
        .semantics {contentDescription=if(live)"End live call" else "Start live call";selected=live}
        .padding(horizontal=14.dp,vertical=8.dp),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(width=22.dp,height=18.dp).drawBehind {
            val bars=5;val gap=size.width/(bars*2f-1f)
            for(i in 0 until bars) {
                val h=if(live)(.35f+.65f*kotlin.math.abs(sin(clock.seconds*(5f+i*1.3f)+i)))*size.height else size.height*(.25f+.12f*(i%3))
                val x=i*gap*2f+gap/2f
                drawLine(tone.copy(alpha=if(enabled)1f else .4f),Offset(x,size.height/2f-h/2f),Offset(x,size.height/2f+h/2f),gap,StrokeCap.Round)
            }
        })
        Text(if(live)"End call" else "Live Call",color=if(enabled)EiliotColors.TextPrimary else EiliotColors.TextMuted,maxLines=1,overflow=TextOverflow.Ellipsis,
            style=MaterialTheme.typography.labelLarge)
    }
}
