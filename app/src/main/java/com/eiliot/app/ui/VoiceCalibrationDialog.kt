package com.eiliot.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.eiliot.app.core.*
import com.eiliot.app.voice.*
import com.eiliot.app.voice.remote.NativeSpeechClient
import kotlinx.coroutines.*

@Composable fun VoiceCalibrationDialog(onClose:()->Unit) {
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    val prompts=listOf("Hey Elliot","Elliot","Hey Elliot","Elliot","Hello, how are you?","What is my battery level?")
    var step by remember {mutableIntStateOf(0)}
    var busy by remember {mutableStateOf(false)};var ready by remember {mutableStateOf(false)}
    var status by remember {mutableStateOf("Preparing microphone checks…")}
    val trials=remember {mutableListOf<WakeTrial>()};val assessments=remember {mutableListOf<WakeAssessment>()}
    val validation=remember {mutableListOf<Boolean>()}
    var candidate by remember {mutableStateOf<WakeAssessment?>(null)}
    var validated by remember {mutableStateOf(false)}
    var transcript by remember {mutableStateOf("")}
    var job by remember {mutableStateOf<Job?>(null)}
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {onClose()}
    val priorLive=remember {WakeListener.liveCall.value}
    fun discard() {trials.forEach {it.audio.fill(0f)};trials.clear()}
    DisposableEffect(Unit) {
        onDispose {
            job?.cancel();discard()
            WakeListener.calibrationActive=false
            if(SentinelRuntime.alive() && SentinelRuntime.foreground) {
                if(priorLive)WakeListener.setLiveCall(context,true)else WakeListener.start(context)
            }
        }
    }
    LaunchedEffect(Unit) {
        WakeListener.calibrationActive=true
        EiliotBrain.cancelCurrent();WakeListener.stop()
        try {NativeSpeechClient.connect(context);ready=true;status="Use your normal accent and speaking distance."}
        catch(e:CancellationException){throw e}
        catch(e:Exception){status="Offline voice could not start. Close this check and use Repair offline voice."}
    }
    fun record() {
        busy=true
        job=scope.launch {
            var audio:FloatArray?=null
            try {
                status="Speak now. Recording for four seconds…"
                val reference=if(step<12)step>=6 else checkNotNull(candidate).setting.referenceCapture
                val clip=WakeCalibration.capture(context,reference);audio=clip
                if(!WakeCalibration.usable(clip)){status="Too quiet or distorted. Adjust your distance and retry.";return@launch}
                if(step<12) {
                    val i=step%6
                    trials+=WakeTrial(if(i%2==0)"hey" else "name",i<4,clip);audio=null
                    if(step==5 || step==11) {
                        status="Checking this microphone against your samples…"
                        assessments+=WakeCalibration.assess(reference,trials)
                        discard()
                    }
                    step++
                    if(step==12) {
                        candidate=WakeCalibrationPolicy.choose(assessments)
                        status=if(candidate==null)"No setting passed both wake phrases and the non-wake checks. Your previous settings are unchanged. Try again in a quieter room."
                            else "A candidate passed the initial checks. Now use four fresh samples to verify it."
                    }else status="Sample accepted. Use your normal voice for the next one."
                }else {
                    val hit=WakeCalibration.detect(checkNotNull(candidate).setting,clip)
                    validation+=hit;step++
                    if(step==16) {
                        validated=WakeCalibrationPolicy.validationPass(validation)
                        status=if(validated)"Both fresh wake attempts worked and both ordinary phrases were rejected. You can save this setting."
                            else "The fresh checks did not pass. Your previous settings are unchanged."
                    }else status="Check complete. Continue with the next phrase."
                }
            }catch(e:CancellationException){throw e}
            catch(e:Exception){ready=false;discard();status="Microphone check failed. Close and retry; no new settings were saved."}
            finally {audio?.fill(0f);busy=false}
        }
    }
    Dialog(onDismissRequest={if(!busy)onClose()}) {
        Surface(shape=MaterialTheme.shapes.large) {
            Column(Modifier.padding(20.dp).heightIn(max=650.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("Calibrate my wake phrase",style=MaterialTheme.typography.titleLarge)
                Text("We compare two microphone paths using your normal accent. Audio stays in memory and is discarded when you close. This does not train the speech model.")
                Text(status)
                if(step<12 || (step<16 && candidate!=null)) {
                    val index=if(step<12)step%6 else step-12
                    val phrase=if(step<12)prompts[index]else listOf("Hey Elliot","Elliot","Hello, how are you?","What is my battery level?")[index]
                    Text(if(step<12)"Microphone check ${step/6+1} · sample ${step%6+1} of 6" else "Fresh validation ${step-11} of 4")
                    Text("Say: $phrase",style=MaterialTheme.typography.titleMedium)
                    Text("Tap Record, then speak once when “Speak now” appears. Stay quiet for the rest of the four seconds.")
                    Button(onClick={record()},enabled=ready && !busy){Text(if(busy)"Checking…" else "Record sample")}
                }
                if(validated)Button(onClick={
                    try {VoiceCalibrationStore.save(context,checkNotNull(candidate).setting);onClose()}
                    catch(e:Exception){status="The setting could not be saved. Please retry or close this check."}
                },enabled=!busy){Text("Save calibrated setting")}
                if(ready && !busy)TextButton(onClick={
                    busy=true
                    job=scope.launch {
                        var clip:FloatArray?=null
                        try {
                            status="Speak one English sentence now. Capturing six seconds…"
                            val audio=WakeCalibration.capture(context,candidate?.setting?.referenceCapture?:VoiceCalibrationStore.load(context)?.referenceCapture?:false,6);clip=audio
                            transcript=WakeCalibration.transcribe(audio).ifBlank {"No words recognized"}
                            status="Raw STT result below. Nothing was executed or saved. Hindi and Bengali STT still require a different model."
                        }catch(e:CancellationException){throw e}
                        catch(e:Exception){status="Transcription check failed. Close and retry."}
                        finally{clip?.fill(0f);busy=false}
                    }
                }){Text("Check transcription instead")}
                if(transcript.isNotBlank())Text(transcript)
                TextButton(onClick={onClose()}){Text(if(busy)"Cancel and close" else "Close without saving")}
            }
        }
    }
}
