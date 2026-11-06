package com.eiliot.app.ui

import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eiliot.app.core.*
import com.eiliot.app.capabilities.*
import com.eiliot.app.llm.*
import com.eiliot.app.overlay.OverlayManager
import com.eiliot.app.tools.PhoneTools
import com.eiliot.app.voice.*
import kotlinx.coroutines.*

@Composable fun ControlSwitch(title:String,detail:String,checked:Boolean,onChange:(Boolean)->Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=64.dp).padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end=12.dp).semantics {contentDescription="$title. $detail"}) {
            Text(title,color=EiliotColors.TextPrimary,fontSize=15.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
            Text(detail,color=EiliotColors.TextSecondary,fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
        }
        Switch(checked,onChange,colors=SwitchDefaults.colors(checkedThumbColor=EiliotColors.Teal,checkedTrackColor=EiliotColors.CyanDim))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun SettingsScreen() {
    val context=LocalContext.current;val store=SentinelRuntime.store;val scope=rememberCoroutineScope()
    val enabled by SentinelRuntime.enabled.collectAsStateWithLifecycle()
    val capabilities by CapabilityRegistry.snapshot.collectAsStateWithLifecycle()
    val voiceReady by NeuralSpeechPlayer.ready.collectAsStateWithLifecycle()
    val maleVoices by VoiceIo.maleVoices.collectAsStateWithLifecycle()
    val nano by NanoClient.status.collectAsStateWithLifecycle()
    var revision by remember {mutableIntStateOf(0)}
    var status by remember {mutableStateOf("")};var busy by remember {mutableStateOf(false)}
    var connect by remember {mutableStateOf(store.apiKey.isBlank() || store.inferenceMode=="offline")}
    var provider by remember {mutableStateOf("Custom")};var mode by remember {mutableStateOf(store.inferenceMode)}
    var root by remember {mutableStateOf(store.baseUrl)};var key by remember {mutableStateOf(store.apiKey)};var model by remember {mutableStateOf(store.model)}
    var voiceOptions by remember {mutableStateOf(false)};var pitch by remember {mutableFloatStateOf(store.ttsPitch)};var rate by remember {mutableFloatStateOf(store.ttsRate)}
    var calibrating by remember {mutableStateOf(false)}
    var checkingStt by remember {mutableStateOf(false)}
    var showApps by remember {mutableStateOf(false)};var notices by remember {mutableStateOf("")}
    var showNotices by remember {mutableStateOf(false)}
    var permissionEpoch by remember {mutableLongStateOf(0)}
    val microphone=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if(permissionEpoch==SentinelRuntime.epoch && SentinelRuntime.alive() && store.wakeEnabled &&
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)SentinelRuntime.startAndArmMic(context)
        revision++
    }
    fun arm() {
        permissionEpoch=SentinelRuntime.epoch
        val list=mutableListOf(Manifest.permission.RECORD_AUDIO);if(Build.VERSION.SDK_INT>=33)list+=Manifest.permission.POST_NOTIFICATIONS
        microphone.launch(list.toTypedArray())
    }
    fun changed() {revision++;CapabilityRegistry.requestRefresh()}
    LifecycleResumeEffect(Unit) {revision++;VoiceIo.refreshVoices();CapabilityRegistry.refresh();onPauseOrDispose{}}
    LaunchedEffect(showNotices) {if(showNotices)notices=withContext(Dispatchers.IO) {runCatching {context.assets.open("models/NOTICES.md").bufferedReader().use {it.readText()}}.getOrDefault("Model notices are included with the complete source.")}}
    // Reading revision makes external grant callbacks re-read current persisted switches.
    val redraw=revision
    if(calibrating)VoiceCalibrationDialog(transcriptionOnly=checkingStt) {calibrating=false;changed()}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {ScreenTitle("Make him yours.","Control room")}
        item {GlassPanel {
            Row(Modifier.fillMaxWidth().clickable {connect=!connect}.heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Intelligence",color=EiliotColors.TextPrimary,maxLines=1)
                    Text(when(store.inferenceMode) {"nano"->"On-device Nano";"offline"->"Offline commands";else->if(store.apiKey.isBlank())"Connect an AI provider" else "Cloud connection saved"},color=EiliotColors.TextSecondary,maxLines=1,overflow=TextOverflow.Ellipsis)
                }
                Text(if(connect)"−" else "+",color=EiliotColors.Teal)
            }
            if(connect) {
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    ProviderPresets.options.forEach {p->FilterChip(mode=="cloud" && provider==p.title,{
                        provider=p.title;mode="cloud";if(p.root.isNotBlank())root=p.root;model=""
                    },label={Text(p.title,maxLines=1)})}
                    FilterChip(mode=="nano",{mode="nano"},label={Text("Nano",maxLines=1)})
                    FilterChip(mode=="offline",{mode="offline"},label={Text("Offline",maxLines=1)})
                }
                when(mode) {
                    "cloud"->{
                        if(provider=="Custom")HudTextField(root,{root=it},"HTTPS API address")
                        HudTextField(key,{key=it},"API key",secret=true)
                        HudTextField(model,{model=it},"Model · blank to find one")
                        TextButton(onClick={
                            busy=true;EiliotBrain.cancelCurrent()
                            scope.launch {
                                val result=withContext(Dispatchers.IO) {runCatching {
                                    val client=LlmClient();val chosen=model.trim().ifBlank {ProviderPresets.choose(client.discoverModels(root,key).getOrThrow())?:error("Choose a model")}
                                    client.testChat(root,key,chosen).getOrThrow();client.testTools(root,key,chosen).getOrThrow();chosen
                                }}
                                busy=false
                                result.onSuccess {model=it;store.baseUrl=root;store.apiKey=key;store.model=it;store.inferenceMode="cloud";status="Connected";connect=false;changed()}
                                    .onFailure {status=FriendlyStatus.error(it.message)}
                            }
                        },enabled=!busy && key.isNotBlank() && root.isNotBlank()) {Text(if(busy)"Connecting…" else "Verify & save",maxLines=1,overflow=TextOverflow.Ellipsis)}
                    }
                    "nano"->{
                        Text("Nano runs while ELLIOT is open, on supported devices. Its system model is separate from the bundled voice.",color=EiliotColors.TextSecondary)
                        Text(nano,color=EiliotColors.TextSecondary,maxLines=2,overflow=TextOverflow.Ellipsis)
                        FlowRow {
                            TextButton(onClick={store.inferenceMode="nano";EiliotBrain.cancelCurrent();NanoClient.setup(false);changed()},enabled=enabled) {Text("Check support",maxLines=1)}
                            TextButton(onClick={store.inferenceMode="nano";EiliotBrain.cancelCurrent();NanoClient.setup(true);changed()},enabled=enabled) {Text("Prepare Nano",maxLines=1)}
                        }
                    }
                    else->TextButton(onClick={store.inferenceMode="offline";EiliotBrain.cancelCurrent();connect=false;changed()}) {Text("Use offline commands",maxLines=1)}
                }
            }
            if(status.isNotBlank())Text(status,color=EiliotColors.Teal)
        }}
        item {GlassPanel(accent=EiliotColors.Teal) {
            SettingRow("Voice & wake word","Say Hey Elliot to interrupt and speak",store.wakeEnabled,capabilities.entry("wake_word")) {
                store.wakeEnabled=it
                if(it)arm()else {WakeListener.stop();VoiceIo.stopListening()}
                changed()
            }
            // The status text points here for worker failures too, so offer it in that state as well.
            if(!voiceReady || com.eiliot.app.voice.remote.NativeSpeechClient.blocked(context))
                TextButton(onClick={ModelAssets.retry(context);com.eiliot.app.voice.remote.NativeSpeechClient.retry(context)}) {Text("Repair offline voice",maxLines=1)}
            ControlSwitch("Listen after a reply","A short follow-up window without another wake phrase",store.followUpEnabled) {store.followUpEnabled=it;changed()}
            TextButton(onClick={SentinelRuntime.startAndArmMic(context){checkingStt=false;calibrating=true}},enabled=enabled) {Text("Calibrate my wake phrase")}
            TextButton(onClick={SentinelRuntime.startAndArmMic(context){checkingStt=true;calibrating=true}},enabled=enabled) {Text("Speech recognition & languages")}
            if(VoiceCalibrationStore.load(context)!=null)TextButton(onClick={
                VoiceCalibrationStore.clear(context);WakeListener.stop();WakeListener.start(context);changed()
            }) {Text("Reset wake calibration")}
            TextButton(onClick={voiceOptions=!voiceOptions}) {Text(if(voiceOptions)"Hide voice options" else "Voice options",maxLines=1)}
            if(voiceOptions) {
                Text("ELLIOT · Ryan · offline male voice",color=EiliotColors.TextSecondary,maxLines=1,overflow=TextOverflow.Ellipsis)
                Text("Speed",color=EiliotColors.TextSecondary);Slider(rate,{rate=it;store.ttsRate=it},valueRange=.7f..1.4f)
                Text("Pitch",color=EiliotColors.TextSecondary);Slider(pitch,{pitch=it;store.ttsPitch=it},valueRange=.7f..1.3f)
                TextButton(onClick={VoiceIo.speak("I'm ELLIOT. I'm here when you need me.")},enabled=enabled) {Text("Hear my voice",maxLines=1)}
                if(!voiceReady && maleVoices.isNotEmpty()) {
                    Text("Offline voice unavailable. Choose a system voice:",color=EiliotColors.TextSecondary,fontSize=13.sp)
                    maleVoices.filter {it.id!=NeuralSpeechPlayer.VOICE_ID}.forEach {v->
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            RadioButton(store.ttsVoice==v.id,{store.ttsVoice=v.id;VoiceIo.applyVoice(v.id,pitch,rate);changed()})
                            Text(v.id,color=EiliotColors.TextSecondary,maxLines=1,overflow=TextOverflow.Ellipsis)
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical=12.dp),color=EiliotColors.Teal.copy(alpha=.2f))
                SectionLabel("Microphone",EiliotColors.Teal)
                ControlSwitch("Require Hey Elliot","Listen for the full phrase instead of the name alone. Clears wake calibration.",WakeCapturePreference.requireHey(context)) {
                    WakeCapturePreference.setRequireHey(context,it)
                    WakeListener.stop();WakeListener.start(context);changed()
                }
                ControlSwitch("Direct microphone","Skip call processing so speech from across the room is not flattened. Clears wake calibration.",
                    WakeCapturePreference.reference(context)) {
                    WakeCapturePreference.setReference(context,it)
                    WakeListener.stop();WakeListener.start(context);changed()
                }
                ControlSwitch("Reduce fan and AC noise","Filter steady background noise locally. The wake phrase is matched before this filter. Clears wake calibration.",WakeCapturePreference.softwareNoiseReduction(context)) {
                    WakeCapturePreference.setSoftwareNoiseReduction(context,it)
                    WakeListener.stop();WakeListener.start(context);changed()
                }
            }
        }}
        item {HandsFreeControls(enabled)}
        item {GlassPanel(accent=EiliotColors.Violet) {
            ControlSwitch("Room mode","Allow eligible speech with the display off or locked",store.roomMode) {store.roomMode=it;changed()}
            ControlSwitch("Optional action review","Off means your matching request is enough",store.reviewActions) {store.reviewActions=it;EiliotBrain.cancelCurrent();changed()}
            ControlSwitch("Memory","Save conversations, notes and web references",store.memoryEnabled) {SentinelRuntime.setMemoryEnabled(it);changed()}
            SettingRow("Background learning","Public reference feeds, while charging on unmetered data",store.learningEnabled,capabilities.entry("web_learning")) {
                store.learningEnabled=it;SentinelRuntime.refreshFeatures();changed()
            }
        }}
        item {GlassPanel {
            SettingRow("Floating companion","A small orb above other apps",store.orbEnabled,capabilities.entry("floating_orb")) {
                store.orbEnabled=it
                if(!it)OverlayManager.hide()else if(!OverlayManager.canDraw(context))OverlayManager.requestPermission(context)else if(enabled)OverlayManager.show(context)
                changed()
            }
            ControlSwitch("Orb inside ELLIOT","Also show the floating orb over this app",store.orbInApp) {
                store.orbInApp=it;if(enabled && store.orbEnabled && it)OverlayManager.show(context)else if(SentinelRuntime.foreground)OverlayManager.hide();changed()
            }
        }}
        item {OrbAppearanceControls()}
        item {GlassPanel {
            ControlSwitch("Companion tools","Phone facts, apps, timers and supported actions",store.phoneToolsEnabled) {store.phoneToolsEnabled=it;if(!it)EiliotBrain.cancelCurrent();changed()}
            ControlSwitch("Internet tools","Search and read public HTTPS pages",store.webToolsEnabled) {store.webToolsEnabled=it;if(!it)EiliotBrain.cancelCurrent();changed()}
            SettingRow("Screen reading","Eligible visible text, only when requested",store.screenEnabled,capabilities.entry("read_screen")) {
                store.screenEnabled=it
                if(!it) {store.controlEnabled=false;EiliotBrain.cancelCurrent();ScreenReader.clear()}
                else if(!ScreenReader.connected.value)ScreenReader.settings(context)
                changed()
            }
            SettingRow("Screen actions","Requested taps, scrolling and non-password text",store.controlEnabled,capabilities.entry("click_screen")) {
                store.controlEnabled=it;if(it) {store.screenEnabled=true;if(!ScreenReader.connected.value)ScreenReader.settings(context)}else EiliotBrain.cancelCurrent();changed()
            }
        }}
        item {GlassPanel {
            SettingRow("Notification awareness","Ask about allowed notices during your active hours",store.notificationAwarenessEnabled,capabilities.entry("notification_awareness")) {
                store.notificationAwarenessEnabled=it
                if(it && !EiliotPermissions.isNotificationAccessGranted(context))EiliotPermissions.openNotificationAccessSettings(context)
                changed()
            }
            TextButton(onClick={showApps=true}) {Text("Choose notification apps",maxLines=1,overflow=TextOverflow.Ellipsis)}
            ControlSwitch("Read notice contents","Otherwise I ask before reading eligible text",store.notificationContents) {store.notificationContents=it;changed()}
            if(store.roomMode)ControlSwitch("Read notices while locked","Allow eligible contents to be spoken in the room",store.noticesWhenLocked) {store.noticesWhenLocked=it;changed()}
        }}
        item {Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            Text("ELLIOT 0.6.2",color=EiliotColors.TextSecondary,maxLines=1)
            TextButton(onClick={showNotices=true}) {Text("Voice & licenses",maxLines=1,overflow=TextOverflow.Ellipsis)}
        }}
    }
    if(showApps) {
        var selected by remember {mutableStateOf(store.notificationApps.split(',').filter(String::isNotBlank).toSet())}
        val apps=remember {PhoneTools.apps()}
        AlertDialog(onDismissRequest={showApps=false},title={Text("Allowed notification apps")},text={
            LazyColumn(Modifier.heightIn(max=420.dp)) {items(apps,key={it.packageName}) {app->
                Row(Modifier.fillMaxWidth().clickable {selected=if(app.packageName in selected)selected-app.packageName else selected+app.packageName},verticalAlignment=Alignment.CenterVertically) {
                    Checkbox(app.packageName in selected,{selected=if(it)selected+app.packageName else selected-app.packageName})
                    Text(app.name,maxLines=1,overflow=TextOverflow.Ellipsis)
                }
            }}
        },confirmButton={TextButton(onClick={store.notificationApps=selected.joinToString(",");showApps=false;changed()}) {Text("Save")}},dismissButton={TextButton(onClick={showApps=false}) {Text("Cancel")}})
    }
    if(showNotices)AlertDialog(onDismissRequest={showNotices=false},title={Text("Voice & licenses")},text={Text(notices,modifier=Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()))},confirmButton={TextButton(onClick={showNotices=false}) {Text("Done")}})
}

@Composable private fun SettingRow(title:String,detail:String,checked:Boolean,entry:CapabilityEntry?,onChange:(Boolean)->Unit) {
    val attention=checked && entry?.availability in setOf(Availability.SETUP,Availability.UNAVAILABLE,Availability.UNKNOWN) || checked && SentinelRuntime.alive() && entry?.availability==Availability.PAUSED
    Column {
        ControlSwitch(title,detail,checked,onChange)
        if(attention)Text(entry?.recovery?.label?.ifBlank {"Check this setting"}?:"Check this setting",color=EiliotColors.Amber,fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis,
            modifier=Modifier.clickable {onChange(true)}.heightIn(min=48.dp).padding(vertical=12.dp))
    }
}

