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
    val requireHey=remember {WakeCapturePreference.requireHey(context)}
    val initialSteps=if(requireHey)6 else 12
    val finalStep=initialSteps+4
    val currentReference=remember {WakeCapturePreference.reference(context)}
    val secondWake=if(requireHey)"Hey Elliot"else "Elliot"
    val finalControl=if(requireHey)"Elliot"else "What is my battery level?"
    val prompts=listOf(
        "Hey Elliot",secondWake,"Hey Elliot",secondWake,"Hello, how are you?",
        if(requireHey)"Hey Ellie"else "What is my battery level?"
    )
    var step by remember {mutableIntStateOf(0)}
    var busy by remember {mutableStateOf(false)};var ready by remember {mutableStateOf(false)}
    var status by remember {mutableStateOf("Preparing microphone checks…")}
    val trials=remember {mutableListOf<WakeTrial>()};val assessments=remember {mutableListOf<WakeAssessment>()}
    var eligible by remember {mutableStateOf(emptyList<WakeAssessment>())}
    var candidate by remember {mutableStateOf<WakeAssessment?>(null)}
    var validated by remember {mutableStateOf(false)}
    var transcript by remember {mutableStateOf("")}
    var lastClip by remember {mutableStateOf<FloatArray?>(null)}
    var cleanedClip by remember {mutableStateOf<FloatArray?>(null)}
    var referenceStt by remember {mutableStateOf(VoiceCalibrationStore.load(context)?.referenceCapture?:WakeCapturePreference.reference(context))}
    var multilingual by remember {mutableStateOf(MultilingualModelPack.available(context))}
    var selectedPack by remember {mutableStateOf(MultilingualModelPack.SMALL)}
    var language by remember {mutableStateOf("en")}
    var packReady by remember {mutableStateOf(MultilingualModelPack.available(context))}
    var clipNumber by remember {mutableIntStateOf(0)}
    var clipMicrophone by remember {mutableStateOf("")}
    var clipEffects by remember {mutableStateOf("")}
    var clipEnhanced by remember {mutableStateOf(false)}
    var job by remember {mutableStateOf<Job?>(null)}
    val exportFile=remember {context.getExternalFilesDir(null)?.let {java.io.File(it,"voice-evaluation/consented-sample.wav")}}
    var exported by remember {mutableStateOf(exportFile?.exists()==true)}
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {onClose()}
    val priorLive=remember {WakeListener.liveCall.value}
    fun discard() {trials.forEach {it.audio.fill(0f)};trials.clear()}
    fun discardCleaned() {cleanedClip?.fill(0f);cleanedClip=null}
    DisposableEffect(Unit) {
        onDispose {
            job?.cancel();discard();lastClip?.fill(0f);discardCleaned()
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
    fun checkWake(recordNew:Boolean) {
        busy=true
        job=scope.launch {
            var clip:FloatArray?=null
            try {
                if(recordNew)clipEnhanced=WakeCapturePreference.softwareNoiseReduction(context)
                val audio=if(recordNew)WakeCalibration.capture(context,referenceStt,4,onEffects={clipEffects=it},onReady={status="Speak now. Say Hey Elliot once. Capturing four seconds…"})else checkNotNull(lastClip).copyOf()
                clip=audio
                if(recordNew) {discardCleaned();lastClip?.fill(0f);lastClip=audio.copyOf();clipNumber++;clipMicrophone=if(referenceStt)"Direct"else "Processed";transcript=""}
                check(WakeCalibration.usable(audio)){"Quiet or distorted recording"}
                status="Checking the same recording with both wake models…"
                val settings=WakeCalibration.settings(referenceStt,WakeCapturePreference.noiseReduction(context),clipEnhanced,requireHey)
                val learned=if(requireHey)WakeCalibration.learnedSetting(settings.first(),WakeCalibration.observePhones(audio))else null
                val rows=(settings+listOfNotNull(learned)).map {setting->
                    val hits=WakeCalibration.detectHits(setting,audio)
                    val label=if(setting.learnedPronunciations.isEmpty())setting.label else "Learned from this recording"
                    "$label · ${setting.score}/${setting.threshold}: ${hits.joinToString().ifEmpty {"missed"}}"
                }
                transcript="Wake replay · recording $clipNumber · $clipMicrophone · $clipEffects:\n"+rows.joinToString("\n")
                status="Wake check complete. These results do not change your settings. Ordinary speech and fresh wake checks must pass before saving sensitivity."
            }catch(e:CancellationException){throw e}
            catch(e:Exception){status="Wake check failed. Listen back and retry or repair offline voice."}
            finally {clip?.fill(0f);busy=false}
        }
    }
    fun cleanWake() {
        val original=checkNotNull(lastClip).copyOf();busy=true
        job=scope.launch {
            var cleaned:FloatArray?=null
            try {
                status="Cleaning the same recording locally…"
                val audio=withContext(Dispatchers.IO) {
                    val denoiser=com.eiliot.app.voice.remote.RemoteDenoiser()
                    try {denoiser.clean(original)}finally{runCatching{denoiser.release()}}
                }
                cleaned=audio
                status="Checking wake detection on the cleaned recording…"
                val rows=WakeCalibration.settings(referenceStt,requireHey=requireHey).map {setting->
                    val hits=WakeCalibration.detectHits(setting,audio)
                    "${setting.label} · ${setting.score}/${setting.threshold}: ${hits.joinToString().ifEmpty {"missed"}}"
                }
                discardCleaned();cleanedClip=audio;cleaned=null
                val report="Cleaned wake replay · recording $clipNumber · GTCRN:\n"+rows.joinToString("\n")
                transcript=(listOf(report)+transcript.split("\n\n")).take(3).joinToString("\n\n")
                status="Compare Play last recording and Play cleaned recording. Cleaning stays in memory and is not enabled for normal listening."
            }catch(e:CancellationException){throw e}
            catch(e:Exception){status="Software noise check failed. Original recording is unchanged."}
            finally {original.fill(0f);cleaned?.fill(0f);busy=false}
        }
    }
    fun record() {
        busy=true
        job=scope.launch {
            var audio:FloatArray?=null
            try {
                status="Preparing the microphone. Wait for Speak now…"
                val reference=if(step<initialSteps) {if(requireHey)currentReference else step>=6} else eligible.first().setting.referenceCapture
                val clip=WakeCalibration.capture(context,reference,onEffects={clipEffects=it},onReady={status="Speak now. Recording for four seconds…"});audio=clip
                discardCleaned();lastClip?.fill(0f);lastClip=clip.copyOf()
                if(!WakeCalibration.usable(clip)){status="Too quiet or distorted. Adjust your distance and retry.";return@launch}
                if(step<initialSteps) {
                    val i=step%6
                    trials+=WakeTrial(if(requireHey || i%2==0)"hey" else "name",i<4,clip);audio=null
                    if(step==5 || step==11) {
                        status="Checking this microphone against your samples…"
                        assessments+=WakeCalibration.assess(reference,trials,WakeCapturePreference.noiseReduction(context),WakeCapturePreference.softwareNoiseReduction(context),requireHey)
                        discard()
                    }
                    step++
                    if(step==initialSteps) {
                        val ranked=WakeCalibrationPolicy.ranked(assessments)
                        // Fresh audio must use the same microphone path as its initial samples.
                        val referenceForValidation=ranked.firstOrNull()?.setting?.referenceCapture
                        eligible=ranked.filter {it.setting.referenceCapture==referenceForValidation}
                        status=if(eligible.isEmpty())"No setting passed the wake and ordinary speech checks. The results below show which attempts were missed."
                            else "Record four fresh samples to check the ${eligible.size} eligible settings on this microphone."
                    }else status="Sample accepted. Use your normal voice for the next one."
                }else {
                    val i=step-initialSteps
                    trials+=WakeTrial(if(i==1 && !requireHey)"name"else "hey",i<2,clip);audio=null
                    step++
                    if(step==finalStep) {
                        status="Checking the fresh recordings against the eligible settings…"
                        val (passed,validation)=WakeCalibration.validate(eligible,trials)
                        discard()
                        candidate=passed
                        validated=passed!=null
                        status=if(validated)"Both fresh wake attempts worked and both ordinary phrases were rejected. You can save this setting."
                            else "No setting passed the fresh checks. The top initial setting found ${validation.take(2).count {it}} of 2 wakes and ${validation.drop(2).count {it}} unwanted wakes."
                    }else status="Sample accepted. Continue with the next phrase."
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
                    clipEnhanced=WakeCapturePreference.softwareNoiseReduction(context)
                    status="Preparing the microphone. Wait for Speak now…"
                    WakeCalibration.capture(context,referenceStt,6,onEffects={clipEffects=it},onReady={status="Speak now. Say one sentence. Capturing six seconds…"})
                }else checkNotNull(lastClip).copyOf()
                clip=audio
                if(recordNew) {
                    discardCleaned();lastClip?.fill(0f);lastClip=audio.copyOf();clipNumber++
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
                val result="Recording $clipNumber · $clipMicrophone · $clipEffects · $label (${String.format(java.util.Locale.US,"%.1f",seconds)} seconds):\n${text.ifBlank {"No words recognized"}}"
                transcript=(listOf(result)+transcript.split("\n\n").filter{it.isNotBlank()}).take(6).joinToString("\n\n")
                status="Result below. Change model or language, then tap Transcribe last recording to compare the same audio. No command ran. Saving audio requires the separate local-save button."
            }catch(e:CancellationException){throw e}
            catch(e:Exception){status="Transcription check failed. Verify or repair the selected model, then retry."}
            finally{clip?.fill(0f);busy=false}
        }
    }
    Dialog(onDismissRequest={if(!busy)onClose()}) {
        Surface(shape=MaterialTheme.shapes.large) {
            Column(Modifier.padding(20.dp).heightIn(max=650.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text(if(transcriptionOnly)"Check speech recognition" else "Calibrate my wake phrase",style=MaterialTheme.typography.titleLarge)
                Text(if(transcriptionOnly)"Record a sentence, compare the recognized words, and listen back. Audio stays in memory unless you tap Save one local test recording. No command will run."
                    else if(requireHey)"Use your usual voice and microphone. Speak each prompt after Speak now. Calibration tests a pronunciation learned from your samples. The recordings stay in memory."
                    else "We compare two microphone paths using your normal accent. Audio stays in memory and is discarded when you close.")
                Text(status)
                if(!transcriptionOnly && step>=initialSteps)assessments.forEach {result->
                        Text("Microphone ${if(result.setting.referenceCapture)2 else 1}, ${result.setting.label}, ${result.setting.score}/${result.setting.threshold}: Hey Elliot ${result.phraseHits["hey"]?:0}/${if(requireHey)4 else 2}. ${if(requireHey)""else "Elliot ${result.phraseHits["name"]?:0}/2. "}Unwanted wakes ${result.falseHits}/2.",style=MaterialTheme.typography.bodySmall)
                }
                if(!transcriptionOnly && (step<initialSteps || (step<finalStep && eligible.isNotEmpty()))) {
                    val index=if(step<initialSteps)step%6 else step-initialSteps
                    val phrase=if(step<initialSteps)prompts[index]else listOf("Hey Elliot",secondWake,"Hello, how are you?",finalControl)[index]
                    Text(if(step<initialSteps)"Microphone check ${step/6+1} · sample ${step%6+1} of 6" else "Fresh validation ${step-initialSteps+1} of 4")
                    Text("Say: $phrase",style=MaterialTheme.typography.titleMedium)
                    Text("Tap Record, then speak once when “Speak now” appears. Stay quiet for the rest of the four seconds.")
                    Button(onClick={record()},enabled=ready && !busy){Text(if(busy)"Checking…" else "Record sample")}
                }
                if(!transcriptionOnly && validated && candidate?.setting?.learnedPronunciations?.isNotEmpty()==true)
                    Text("Saving stores short pronunciation sequences on this phone.")
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
                    Button(onClick={checkWake(true)}){Text("Record wake check")}
                    if(lastClip!=null)Button(onClick={checkWake(false)}){Text("Recheck wake on recording")}
                    if(lastClip!=null && !clipEnhanced)Button(onClick={cleanWake()}){Text("Clean and recheck wake")}
                    Button(onClick={checkSentence(true)},enabled=!multilingual || packReady){Text("Record sentence")}
                    if(lastClip!=null)Button(onClick={checkSentence(false)},enabled=!multilingual || packReady){Text("Transcribe last recording")}
                }
                if(transcript.isNotBlank())Text(transcript)
                if(transcriptionOnly && lastClip!=null && !exported && exportFile!=null)TextButton(onClick={
                    val clip=checkNotNull(lastClip).copyOf();busy=true
                    job=scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                val bytes=DiagnosticWav.encode(clip)
                                check(exportFile.parentFile!!.isDirectory || exportFile.parentFile!!.mkdirs())
                                check(exportFile.createNewFile()) {"A local test recording already exists"}
                                try {exportFile.writeBytes(bytes)}catch(e:Exception){exportFile.delete();throw e}
                            }
                            exported=true;status="Saved one local WAV for computer comparison. It stays on this phone until deleted; no upload was made."
                        }catch(e:CancellationException){throw e}
                        catch(e:Exception){status="Could not save the local test recording."}
                        finally{clip.fill(0f);busy=false}
                    }
                },enabled=!busy){Text("Save one local test recording")}
                if(transcriptionOnly && exported)Text("One test recording is saved locally. New recordings will not overwrite it.")
                if(lastClip!=null)TextButton(onClick={
                    val clip=checkNotNull(lastClip);busy=true
                    job=scope.launch {
                        try {status="Playing your last recording…";WakeCalibration.play(clip);status="Playback complete. Was your sentence clear and complete?"}
                        catch(e:CancellationException){throw e}
                        catch(e:Exception){status="Playback could not finish. Check media volume and try again."}
                        finally {busy=false}
                    }
                },enabled=!busy){Text("Play last recording")}
                if(cleanedClip!=null)TextButton(onClick={
                    busy=true
                    job=scope.launch {
                        try {WakeCalibration.play(checkNotNull(cleanedClip));status="Cleaned playback complete. Compare it with the original recording."}
                        catch(e:CancellationException){throw e}
                        catch(e:Exception){status="Cleaned playback could not finish."}
                        finally{busy=false}
                    }
                },enabled=!busy){Text("Play cleaned recording")}
                TextButton(onClick={onClose()}){Text(if(busy)"Cancel and close" else if(transcriptionOnly)"Close" else "Close without saving")}
            }
        }
    }
}
