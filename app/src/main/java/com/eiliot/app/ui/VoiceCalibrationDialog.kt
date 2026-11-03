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

@OptIn(ExperimentalLayoutApi::class)
@Composable fun VoiceCalibrationDialog(transcriptionOnly:Boolean=false,onClose:()->Unit) {
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
    var lastClip by remember {mutableStateOf<FloatArray?>(null)}
    var referenceStt by remember {mutableStateOf(false)}
    var multilingual by remember {mutableStateOf(MultilingualModelPack.available(context))}
    var selectedPack by remember {mutableStateOf(MultilingualModelPack.SMALL)}
    var language by remember {mutableStateOf("en")}
    var packReady by remember {mutableStateOf(MultilingualModelPack.available(context))}
    var clipNumber by remember {mutableIntStateOf(0)}
    var clipMicrophone by remember {mutableStateOf("")}
    var job by remember {mutableStateOf<Job?>(null)}
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {onClose()}
    val priorLive=remember {WakeListener.liveCall.value}
    fun discard() {trials.forEach {it.audio.fill(0f)};trials.clear()}
    DisposableEffect(Unit) {
        onDispose {
            job?.cancel();discard();lastClip?.fill(0f)
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
                status="Preparing the microphone. Wait for Speak now…"
                val reference=if(step<12)step>=6 else checkNotNull(candidate).setting.referenceCapture
                val clip=WakeCalibration.capture(context,reference,onReady={status="Speak now. Recording for four seconds…"});audio=clip
                lastClip?.fill(0f);lastClip=clip.copyOf()
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
                        status=if(candidate==null)"No setting passed both wake phrases and the non-wake checks. Your previous settings are unchanged. The results below show which attempts were missed."
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
    fun checkSentence(recordNew:Boolean) {
        val model=selectedPack;val useWhisper=multilingual;val selectedLanguage=language
        busy=true
        job=scope.launch {
            var clip:FloatArray?=null
            try {
                val audio=if(recordNew) {
                    status="Preparing the microphone. Wait for Speak now…"
                    WakeCalibration.capture(context,referenceStt,6,onReady={status="Speak now. Say one sentence. Capturing six seconds…"})
                }else checkNotNull(lastClip).copyOf()
                clip=audio
                if(recordNew) {
                    lastClip?.fill(0f);lastClip=audio.copyOf();clipNumber++
                    clipMicrophone=if(referenceStt)"Direct"else "Processed"
                    transcript=""
                }
                if(!WakeCalibration.usable(audio)) {status="Recording is too quiet or distorted. Listen back, adjust your distance and record again.";return@launch}
                status="${if(useWhisper)model.label else "English Zipformer"} is checking recording $clipNumber…"
                val started=android.os.SystemClock.elapsedRealtime()
                val text=if(useWhisper)withContext(Dispatchers.IO) {
                    val recognizer=com.eiliot.app.voice.remote.RemoteWhisper(selectedLanguage,model.id)
                    try {recognizer.transcribe(audio)}finally{runCatching{recognizer.release()}}
                }else WakeCalibration.transcribe(audio)
                val seconds=(android.os.SystemClock.elapsedRealtime()-started)/1000.0
                val label=if(useWhisper)"${model.label} · ${selectedLanguage.ifEmpty {"Auto"}}"else "English Zipformer"
                val result="Recording $clipNumber · $clipMicrophone · $label (${String.format(java.util.Locale.US,"%.1f",seconds)} seconds):\n${text.ifBlank {"No words recognized"}}"
                transcript=(listOf(result)+transcript.split("\n\n").filter{it.isNotBlank()}).take(6).joinToString("\n\n")
                status="Result below. Change model or language, then tap Transcribe last recording to compare the same audio. Nothing was executed or saved."
            }catch(e:CancellationException){throw e}
            catch(e:Exception){status="Transcription check failed. Verify or repair the selected model, then retry."}
            finally{clip?.fill(0f);busy=false}
        }
    }
    Dialog(onDismissRequest={if(!busy)onClose()}) {
        Surface(shape=MaterialTheme.shapes.large) {
            Column(Modifier.padding(20.dp).heightIn(max=650.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text(if(transcriptionOnly)"Check speech recognition" else "Calibrate my wake phrase",style=MaterialTheme.typography.titleLarge)
                Text(if(transcriptionOnly)"Record a sentence, compare the recognized words, and listen back. Audio stays in memory until you record again or close. No command will run."
                    else "We compare two microphone paths using your normal accent. Audio stays in memory and is discarded when you close. This does not train the speech model.")
                Text(status)
                if(!transcriptionOnly && step>=12)assessments.forEachIndexed {i,result->
                    Text("Microphone ${if(result.setting.referenceCapture)2 else 1}, sensitivity ${i%3+1}: Hey Elliot ${result.phraseHits["hey"]?:0}/2; Elliot ${result.phraseHits["name"]?:0}/2; unwanted wakes ${result.falseHits}/2.",style=MaterialTheme.typography.bodySmall)
                }
                if(!transcriptionOnly && (step<12 || (step<16 && candidate!=null))) {
                    val index=if(step<12)step%6 else step-12
                    val phrase=if(step<12)prompts[index]else listOf("Hey Elliot","Elliot","Hello, how are you?","What is my battery level?")[index]
                    Text(if(step<12)"Microphone check ${step/6+1} · sample ${step%6+1} of 6" else "Fresh validation ${step-11} of 4")
                    Text("Say: $phrase",style=MaterialTheme.typography.titleMedium)
                    Text("Tap Record, then speak once when “Speak now” appears. Stay quiet for the rest of the four seconds.")
                    Button(onClick={record()},enabled=ready && !busy){Text(if(busy)"Checking…" else "Record sample")}
                }
                if(!transcriptionOnly && validated)Button(onClick={
                    try {VoiceCalibrationStore.save(context,checkNotNull(candidate).setting);onClose()}
                    catch(e:Exception){status="The setting could not be saved. Please retry or close this check."}
                },enabled=!busy){Text("Save calibrated setting")}
                if(transcriptionOnly && ready && !busy) {
                    Text("Speech model · diagnostic only")
                    MultilingualModelPack.models.forEach {model->
                        FilterChip(multilingual && selectedPack==model,{
                            multilingual=true;selectedPack=model;packReady=MultilingualModelPack.available(context,model)
                        },label={Text(model.label)})
                    }
                    FilterChip(!multilingual,{multilingual=false},label={Text("Current English model")})
                    if(multilingual)TextButton(onClick={
                        val model=selectedPack
                        busy=true;job=scope.launch {
                            try {
                                MultilingualModelPack.prepare(context,model){progress->scope.launch {status=progress}}
                                packReady=true;status="${model.label} is ready. Record a sentence or transcribe the last recording."
                            }
                            catch(e:CancellationException){throw e}
                            catch(e:Exception){status="Speech pack could not be prepared. Check connection and storage, then retry."}
                            finally{busy=false}
                        }
                    }){Text(if(packReady)"Verify or repair selected model" else "Download model · ${selectedPack.bytes/1000000} MB")}
                    if(multilingual)FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        listOf("en" to "English","hi" to "Hindi","bn" to "Bengali","" to "Auto").forEach {(code,label)->
                            FilterChip(language==code,{language=code},label={Text(label)})
                        }
                    }
                    Text(when(if(multilingual)language else "en") {
                        "hi"->"Say: Elliot, मेरे फोन का battery level कितना है?"
                        "bn"->"Say: Elliot, আমার ফোনের battery level কত?"
                        ""->"Say one sentence in English, Hindi or Bengali."
                        else->"Say: Hey Elliot, what is my battery level?"
                    },style=MaterialTheme.typography.titleMedium)
                    Text("Choose a microphone, tap Record sentence, and wait for Speak now. Say the whole sentence once in your natural accent.")
                    Text("Microphone")
                    Row {
                        FilterChip(!referenceStt,{referenceStt=false},label={Text("Processed")})
                        Spacer(Modifier.width(8.dp))
                        FilterChip(referenceStt,{referenceStt=true},label={Text("Direct")})
                    }
                }
                if(transcriptionOnly && ready && !busy) {
                    Button(onClick={checkSentence(true)},enabled=!multilingual || packReady){Text("Record sentence")}
                    if(lastClip!=null)Button(onClick={checkSentence(false)},enabled=!multilingual || packReady){Text("Transcribe last recording")}
                }
                if(transcript.isNotBlank())Text(transcript)
                if(lastClip!=null)TextButton(onClick={
                    val clip=checkNotNull(lastClip);busy=true
                    job=scope.launch {
                        try {status="Playing your last recording…";WakeCalibration.play(clip);status="Playback complete. Was your sentence clear and complete?"}
                        catch(e:CancellationException){throw e}
                        catch(e:Exception){status="Playback could not finish. Check media volume and try again."}
                        finally {busy=false}
                    }
                },enabled=!busy){Text("Play last recording")}
                TextButton(onClick={onClose()}){Text(if(busy)"Cancel and close" else if(transcriptionOnly)"Close" else "Close without saving")}
            }
        }
    }
}
