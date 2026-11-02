package com.eiliot.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eiliot.app.core.*
import com.eiliot.app.capabilities.CapabilityRegistry
import com.eiliot.app.learning.LearningScheduler
import kotlinx.coroutines.*

@OptIn(ExperimentalLayoutApi::class)
@Composable fun VaultScreen(onSettings:()->Unit={},initial:Int=0) {
    val context=LocalContext.current;val store=SentinelRuntime.store;val memory=SentinelRuntime.memory;val scope=rememberCoroutineScope()
    val notes by memory.notes.collectAsStateWithLifecycle();val messages by memory.messages.collectAsStateWithLifecycle()
    val running by SentinelRuntime.enabled.collectAsStateWithLifecycle();val erasing by SentinelRuntime.erasing.collectAsStateWithLifecycle()
    val readiness by CapabilityRegistry.snapshot.collectAsStateWithLifecycle()
    val learningStatus by LearningScheduler.status.collectAsStateWithLifecycle()
    var selected by rememberSaveable {mutableIntStateOf(initial)};var draft by remember {mutableStateOf("")}
    var confirmErase by remember {mutableStateOf(false)};var feedback by remember {mutableStateOf("")}
    val canSave=store.memoryEnabled && !erasing
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item {
            Text("Your vault",style=MaterialTheme.typography.headlineSmall,color=EiliotColors.TextPrimary,maxLines=1,overflow=TextOverflow.Ellipsis)
            Text(if(canSave)"Notes, conversations and useful discoveries." else "Saving is off. Your existing records are still here.",color=EiliotColors.TextSecondary)
        }
        item {Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            listOf("Notes","History","Web feed").forEachIndexed {i,title->FilterChip(selected==i,{selected=i},modifier=Modifier.weight(1f).heightIn(min=48.dp),label={Text(title,maxLines=1,overflow=TextOverflow.Ellipsis)})}
        } }
        if(!canSave)item {TextButton(onClick=onSettings) {Text("Memory settings",maxLines=1)}}
        if(feedback.isNotBlank())item {Text(feedback,color=EiliotColors.Amber)}
        when(selected) {
            0 -> {
                item {GlassPanel {
                    HudTextField(draft,{draft=it},"Remember something",singleLine=false,maxLines=4)
                    TextButton(onClick={val text=draft.trim();scope.launch {val saved=withContext(Dispatchers.IO) {memory.addNote(text)};if(saved)draft="";feedback=if(saved)"Saved" else "Already saved, or Memory was turned off."}},enabled=draft.isNotBlank() && canSave) {Text("Save note",maxLines=1)}
                } }
                val saved=notes.filter {it.kind=="note"}
                if(saved.isEmpty())item {VaultEmpty("Keep what matters","Save a preference, a fact or a goal. You choose what ELLIOT remembers.")}
                items(saved,key={it.id}) {note->GlassPanel(accent=EiliotColors.Teal) {
                    StructuredOutput(note.text)
                    TextButton(onClick={EiliotBrain.cancelCurrent();scope.launch(Dispatchers.IO) {memory.deleteNote(note.id)}}) {Text("Forget note",maxLines=1)}
                } }
            }
            1 -> {
                if(messages.isEmpty())item {VaultEmpty("A fresh conversation","Saved conversations will appear here when Memory is on.")}
                items(messages.asReversed(),key={it.id}) {MessageCard(it)}
            }
            else -> {
                val entries=notes.filter {it.kind=="knowledge"}
                item {GlassPanel(accent=EiliotColors.Violet) {
                    Text("Source-backed discoveries",style=MaterialTheme.typography.titleMedium,color=EiliotColors.TextPrimary,maxLines=2,overflow=TextOverflow.Ellipsis)
                    Text(if(!canSave)"Turn on Memory to keep new references." else learningStatus,color=EiliotColors.TextSecondary)
                    FlowRow {
                        if(store.learningEnabled)TextButton(onClick={LearningScheduler.runNow()},enabled=running && canSave) {Text("Learn now",maxLines=1)}
                        else TextButton(onClick=onSettings) {Text("Learning settings",maxLines=1)}
                        TextButton(onClick={EiliotBrain.send("Summarize these recent source-backed references.","knowledge",entries.take(5).joinToString("\n\n") {"${it.text}\nSource: ${it.url}"})},enabled=running && entries.isNotEmpty()) {Text("Discuss latest",maxLines=1)}
                    }
                } }
                if(entries.isEmpty())item {VaultEmpty("Curiosity starts here","Enable background learning to keep public Android and CISA references. It saves reading material, not model training.")}
                items(entries,key={it.id}) {note->GlassPanel(accent=EiliotColors.Violet) {
                    Text(note.text.lineSequence().firstOrNull().orEmpty(),color=EiliotColors.TextPrimary,maxLines=2,overflow=TextOverflow.Ellipsis)
                    Text(note.text.lineSequence().drop(1).joinToString("\n").take(600),color=EiliotColors.TextSecondary,maxLines=7,overflow=TextOverflow.Ellipsis)
                    FlowRow {
                        TextButton(onClick={runCatching {context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(note.url)))}}) {Text("Source",maxLines=1)}
                        TextButton(onClick={scope.launch(Dispatchers.IO) {memory.deleteNote(note.id)}}) {Text("Forget",maxLines=1)}
                    }
                } }
            }
        }
        item {TextButton(onClick={confirmErase=true},enabled=!erasing) {Text("Erase local vault",color=EiliotColors.Amber,maxLines=1)}}
    }
    if(confirmErase)AlertDialog(onDismissRequest={confirmErase=false},title={Text("Erase your local vault?")},text={Text("This removes saved conversations, notes and web references from this phone. Your settings and API key remain. Provider-side records are unchanged.")},
        confirmButton={TextButton(onClick={confirmErase=false;draft="";SentinelRuntime.eraseMemory()}) {Text("Erase")}},dismissButton={TextButton(onClick={confirmErase=false}) {Text("Keep")}})
    if(erasing)AlertDialog(onDismissRequest={},title={Text("Clearing your vault")},text={LinearProgressIndicator(Modifier.fillMaxWidth())},confirmButton={})
}

@Composable private fun VaultEmpty(title:String,body:String) {GlassPanel {
    Text(title,style=MaterialTheme.typography.titleMedium,color=EiliotColors.TextPrimary)
    Text(body,color=EiliotColors.TextSecondary,modifier=Modifier.padding(top=10.dp))
} }
