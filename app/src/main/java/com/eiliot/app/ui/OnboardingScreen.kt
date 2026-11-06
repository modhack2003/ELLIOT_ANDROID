package com.eiliot.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eiliot.app.core.*
import com.eiliot.app.llm.*
import com.eiliot.app.overlay.*
import com.eiliot.app.voice.*
import kotlinx.coroutines.*

/** One completed wizard per installation. Missing optional grants never cause a startup loop. */
@OptIn(ExperimentalLayoutApi::class)
@Composable fun OnboardingScreen(onDone:()->Unit) {
    val context=LocalContext.current;val store=SentinelRuntime.store;val scope=rememberCoroutineScope()
    var step by rememberSaveable { mutableIntStateOf(0) }
    var provider by rememberSaveable { mutableStateOf("Custom") }
    var root by rememberSaveable { mutableStateOf(store.baseUrl) }
    // API-key draft deliberately isn't saved into instance-state Bundles.
    var key by remember { mutableStateOf(store.apiKey) }
    var model by rememberSaveable { mutableStateOf(store.model) }
    var mode by rememberSaveable { mutableStateOf(if(store.inferenceMode=="nano")"nano" else "cloud") }
    var busy by remember { mutableStateOf(false) };var feedback by remember { mutableStateOf("") }
    var mic by remember { mutableStateOf(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) }
    var overlay by remember { mutableStateOf(OverlayManager.canDraw(context)) }
    val connected by ScreenReader.connected.collectAsStateWithLifecycle()
    val assets by ModelAssets.ready.collectAsStateWithLifecycle()
    val voice by NeuralSpeechPlayer.ready.collectAsStateWithLifecycle()
    val assetStatus by ModelAssets.status.collectAsStateWithLifecycle()
    val voiceStatus by NeuralSpeechPlayer.status.collectAsStateWithLifecycle()
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        mic=context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED
    }
    LifecycleResumeEffect(Unit) {
        mic=context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED
        overlay=OverlayManager.canDraw(context)
        onPauseOrDispose { }
    }
    fun finish(speech:Boolean) {
        store.isInitialSetupComplete=true;store.onboarded=true
        store.orbInApp=false
        if(mic)store.wakeEnabled=true
        SentinelRuntime.start(context,mic)
        onDone()
        if(speech && voice)VoiceIo.speak("I'm online. Say Hey Elliot or tap Live Call whenever you need me.")
    }
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(20.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        Spacer(Modifier.height(8.dp))
        HoloStage(OrbState.IDLE,128.dp,description="ELLIOT presence")
        Text("Meet ELLIOT",style=MaterialTheme.typography.headlineLarge,color=EiliotColors.TextPrimary,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.aurora(LocalHoloClock.current,speed=.05f))
        Text("Your companion, ready for everyday life.",color=EiliotColors.TextSecondary)
        LinearProgressIndicator(progress={ (step+1)/3f },modifier=Modifier.fillMaxWidth())
        Text("STEP ${step+1} OF 3",color=EiliotColors.Teal)
        when(step) {
            0 -> GlassPanel {
                Text("Connect his mind",style=MaterialTheme.typography.titleLarge,color=EiliotColors.TextPrimary)
                Text("Choose where answers are generated. Voice and listening already come with the app.",color=EiliotColors.TextSecondary)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    ProviderPresets.options.forEach { p -> FilterChip(provider==p.title && mode=="cloud",{
                        provider=p.title;mode="cloud";if(p.root.isNotBlank())root=p.root;model="";feedback=""
                    },label={Text(p.title,maxLines=1,overflow=TextOverflow.Ellipsis)}) }
                    FilterChip(mode=="nano",{mode="nano";feedback=""},label={Text("Gemini Nano",maxLines=1)})
                }
                if(mode=="cloud") {
                    if(provider=="Custom")HudTextField(root,{root=it;feedback=""},"HTTPS API address")
                    HudTextField(key,{key=it;feedback=""},"API key",secret=true)
                    HudTextField(model,{model=it;feedback=""},"Model · leave blank to find one")
                    GlowButton(if(busy)"Connecting…" else "Verify & continue",enabled=!busy && key.isNotBlank() && root.isNotBlank(),modifier=Modifier.fillMaxWidth(),onClick={
                        busy=true;feedback=""
                        scope.launch {
                            val result=withContext(Dispatchers.IO) { runCatching {
                                val client=LlmClient()
                                val selected=model.trim().ifBlank { ProviderPresets.choose(client.discoverModels(root,key).getOrThrow())?:error("Choose a chat model") }
                                client.testChat(root,key,selected).getOrThrow()
                                client.testTools(root,key,selected).getOrThrow()
                                selected
                            } }
                            busy=false
                            result.onSuccess { chosen ->
                                model=chosen;store.baseUrl=root;store.apiKey=key;store.model=chosen;store.inferenceMode="cloud";step=1
                            }.onFailure { feedback=FriendlyStatus.error(it.message) }
                        }
                    })
                } else {
                    Text("Nano needs a supported device and ELLIOT open. Its system model may need a separate AICore download.",color=EiliotColors.TextSecondary)
                    GlowButton("Continue with Nano",modifier=Modifier.fillMaxWidth(),onClick={store.inferenceMode="nano";step=1})
                }
                if(feedback.isNotBlank())Text(feedback,color=EiliotColors.Amber)
                TextButton(onClick={store.inferenceMode="offline";step=1},enabled=!busy) { Text("Skip for offline commands only",maxLines=1,overflow=TextOverflow.Ellipsis) }
            }
            1 -> GlassPanel {
                Text("Make room for ELLIOT",style=MaterialTheme.typography.titleLarge,color=EiliotColors.TextPrimary)
                Text("You choose the access. Android asks once; any declined option stays available in Settings.",color=EiliotColors.TextSecondary)
                SetupAccessRow("Voice & wake word","Offline listening and spoken replies",mic) {
                    val grants=mutableListOf(Manifest.permission.RECORD_AUDIO)
                    if(Build.VERSION.SDK_INT>=33)grants+=Manifest.permission.POST_NOTIFICATIONS
                    permission.launch(grants.toTypedArray())
                }
                SetupAccessRow("Floating companion","A small orb above other apps",overlay) { OverlayManager.requestPermission(context) }
                SetupAccessRow("Screen assistance","Optional: read and act on eligible visible text",connected) {
                    store.screenEnabled=true;store.controlEnabled=true;ScreenReader.settings(context)
                }
                GlowButton("Continue",modifier=Modifier.fillMaxWidth(),onClick={step=2})
                TextButton(onClick={step=0}) { Text("Back") }
            }
            else -> GlassPanel {
                Text("Hello, friend.",style=MaterialTheme.typography.headlineLarge,color=EiliotColors.TextPrimary)
                Text(if(assets && voice)"My offline voice is ready. Say Hey Elliot, or start a Live Call to speak naturally." else "$assetStatus. $voiceStatus",color=EiliotColors.TextSecondary)
                if(!mic)Text("Microphone access is off. You can use text and enable voice later.",color=EiliotColors.Amber)
                if(assets && voice)GlowButton("Start ELLIOT",modifier=Modifier.fillMaxWidth(),onClick={finish(true)})
                else {
                    LinearProgressIndicator(modifier=Modifier.fillMaxWidth())
                    TextButton(onClick={ModelAssets.retry(context);com.eiliot.app.voice.remote.NativeSpeechClient.retry(context)}) { Text("Retry voice setup") }
                    TextButton(onClick={finish(false)}) { Text("Continue with text for now") }
                }
                TextButton(onClick={step=1}) { Text("Back") }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable private fun SetupAccessRow(title:String,subtitle:String,granted:Boolean,action:()->Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end=12.dp)) {
            Text(title,color=EiliotColors.TextPrimary,maxLines=1,overflow=TextOverflow.Ellipsis)
            Text(subtitle,color=EiliotColors.TextSecondary,maxLines=2,overflow=TextOverflow.Ellipsis)
        }
        TextButton(onClick=action,modifier=Modifier.heightIn(min=48.dp)) { Text(if(granted)"Allowed" else "Allow",maxLines=1) }
    }
}

