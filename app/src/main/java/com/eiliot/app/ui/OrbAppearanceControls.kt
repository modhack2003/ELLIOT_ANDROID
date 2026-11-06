package com.eiliot.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eiliot.app.core.SentinelRuntime
import com.eiliot.app.overlay.*

val LocalOverlayAppearance = staticCompositionLocalOf { OverlayAppearance() }
val LocalSystemMotionOff = staticCompositionLocalOf<Boolean?> { null }

@Composable fun WithOverlayAppearance(content: @Composable () -> Unit) {
    val context=LocalContext.current
    val store=remember(context) { OverlayLayoutStore(context) }
    DisposableEffect(store) { onDispose { store.close() } }
    val changes by store.changes.collectAsStateWithLifecycle()
    val appearance=remember(changes) { store.appearance() }
    fun motionOff()=runCatching { android.provider.Settings.Global.getFloat(context.contentResolver,android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f }.getOrDefault(false)
    var systemMotionOff by remember { mutableStateOf(motionOff()) }
    DisposableEffect(context) {
        val observer=object:android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
            override fun onChange(selfChange:Boolean) { systemMotionOff=motionOff() }
        }
        context.contentResolver.registerContentObserver(android.provider.Settings.Global.getUriFor(android.provider.Settings.Global.ANIMATOR_DURATION_SCALE),false,observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    CompositionLocalProvider(LocalOverlayAppearance provides appearance,LocalSystemMotionOff provides systemMotionOff,content=content)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun OrbAppearanceControls() {
    val context=LocalContext.current
    val store=remember(context) { OverlayLayoutStore(context) }
    DisposableEffect(store) { onDispose { store.close() } }
    val changes by store.changes.collectAsStateWithLifecycle()
    val a=remember(changes) { store.appearance() }
    val enabled by SentinelRuntime.enabled.collectAsStateWithLifecycle()
    var opacity by remember(a.opacity) { mutableFloatStateOf(a.opacity) }
    var feedback by remember { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf(false) }
    GlassPanel(accent=EiliotColors.Teal,contentPadding=PaddingValues(16.dp)) {
        SectionLabel("Orb & appearance")
        Text("Make room for your day.",style=MaterialTheme.typography.titleLarge,color=EiliotColors.TextPrimary)
        Text("Keep captions close and the rest of your phone usable.",fontSize=14.sp,color=EiliotColors.TextSecondary,modifier=Modifier.padding(top=6.dp))
        AndroidView(factory={ ArcCoreView(it).apply { setContrastDisc(false) } },update={ it.setState(OrbState.IDLE);it.setPresentation(a.motion,a.haptics);it.setPaused(!enabled);it.alpha=a.opacity },modifier=Modifier.size(a.sizeDp.dp).padding(vertical=4.dp))
        TextButton(onClick={expanded=!expanded},modifier=Modifier.heightIn(min=48.dp)) { Text(if(expanded) "Hide appearance options" else "Customize orb & captions") }
        if(expanded) {
        Text("Orb size",color=EiliotColors.TextPrimary,fontSize=16.sp)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf(64,80,96).forEach { size -> FilterChip(a.sizeDp==size,{store.save(a.copy(sizeDp=size))},label={Text("$size dp")}) }
        }
        Text("Visibility · ${(opacity*100).toInt()}%",fontSize=14.sp,color=EiliotColors.TextSecondary)
        Slider(opacity,{opacity=it},valueRange=.6f..1f,onValueChangeFinished={store.save(a.copy(opacity=opacity))})
        ControlSwitch("Show floating captions","Visual text beside the orb. Hiding it does not mute voice.",a.captions) { store.save(a.copy(captions=it)) }
        Text("Caption text size",color=EiliotColors.TextPrimary,fontSize=16.sp)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf(14,16,18,20).forEach { size -> FilterChip(a.captionSp==size,{store.save(a.copy(captionSp=size))},label={Text("$size sp")}) }
        }
        Text("System text scaling still applies.",color=EiliotColors.TextSecondary,fontSize=12.sp)
        Text("I'll keep the important part close.",color=EiliotColors.Teal,fontSize=a.captionSp.sp,modifier=Modifier.padding(vertical=8.dp))
        Text("Keep finished captions for",color=EiliotColors.TextPrimary,fontSize=16.sp,modifier=Modifier.padding(top=8.dp))
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf(10,20,30,60).forEach { seconds -> FilterChip(a.timeoutSeconds==seconds,{store.save(a.copy(timeoutSeconds=seconds))},label={Text("$seconds sec")}) }
        }
        Text("Motion",color=EiliotColors.TextPrimary,fontSize=16.sp,modifier=Modifier.padding(top=8.dp))
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("system" to "System","reduced" to "Reduced","expressive" to "Expressive").forEach { (id,label) ->
                FilterChip(a.motion==id,{store.save(a.copy(motion=id))},label={Text(label)})
            }
        }
        Text("System is calm when idle. Expressive keeps the idle glow moving. Android's animation-off setting is always respected.",fontSize=13.sp,color=EiliotColors.TextSecondary)
        ControlSwitch("Reveal text as it arrives","Turn off for instant text. Reduced motion also shows text instantly.",a.typing) { store.save(a.copy(typing=it)) }
        ControlSwitch("Touch feedback","Allow gentle haptics, subject to your phone's settings.",a.haptics) { store.save(a.copy(haptics=it)) }
        HorizontalDivider(Modifier.padding(vertical=12.dp),color=EiliotColors.Teal.copy(alpha=.2f))
        Text("Position",color=EiliotColors.TextPrimary,fontSize=16.sp)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            TextButton(onClick={OverlayManager.move(DockEdge.LEFT);feedback="Left placement saved"},modifier=Modifier.heightIn(min=48.dp)) { Text("Move left") }
            TextButton(onClick={OverlayManager.move(DockEdge.RIGHT);feedback="Right placement saved"},modifier=Modifier.heightIn(min=48.dp)) { Text("Move right") }
            TextButton(onClick={OverlayManager.resetPosition();feedback="Position reset"},modifier=Modifier.heightIn(min=48.dp)) { Text("Reset position") }
        }
        Text("Placement is saved for each window orientation and size. You can also move it from the orb's More menu or its accessibility actions.",fontSize=13.sp,color=EiliotColors.TextSecondary)
        TextButton(onClick={store.reset();OverlayManager.resetPosition();feedback="Appearance reset; your tools, voice and memory settings are retained"},modifier=Modifier.heightIn(min=48.dp)) { Text("Reset appearance") }
        }
        if(feedback.isNotBlank())Text(feedback,color=EiliotColors.Teal,fontSize=13.sp)
    }
}
