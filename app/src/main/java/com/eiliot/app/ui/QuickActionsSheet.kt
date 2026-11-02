package com.eiliot.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eiliot.app.core.*
import com.eiliot.app.llm.ToolCall
import com.eiliot.app.tools.*
import com.eiliot.app.overlay.OverlayManager
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Useful manual alternatives live in one optional drawer, not a duplicate diagnostic tab. */
@OptIn(ExperimentalMaterial3Api::class,ExperimentalLayoutApi::class)
@Composable fun QuickActionsSheet(onDismiss:()->Unit,onSettings:()->Unit) {
    val scope=rememberCoroutineScope()
    val context=LocalContext.current
    var section by remember { mutableStateOf("Phone") }
    var feedback by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf("5") }
    var volume by remember { mutableFloatStateOf(50f) }
    fun run(name:String,args:JSONObject=JSONObject()) {
        if(busy)return
        busy=true
        scope.launch {
            try {
                val raw=JSONObject(PhoneTools.execute(ToolCall("shortcut_${System.nanoTime()}",name,args.toString()),CommandGrant.direct(SentinelRuntime.epoch,name)))
                if(!raw.optBoolean("ok"))feedback=FriendlyStatus.error(raw.optString("error"))
                else feedback=if(name=="device_info") {
                    val data=JSONObject(raw.optString("result"));listOf("Device","Android","Battery","RAM","Internal storage").joinToString("\n") { "$it: ${data.optString(it)}" }
                }else raw.optString("result").take(1500)
            }finally {busy=false}
        }
    }
    ModalBottomSheet(onDismissRequest=onDismiss,containerColor=EiliotColors.Abyss) {
        LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item { Text("Action shortcuts",style=MaterialTheme.typography.titleLarge,color=EiliotColors.TextPrimary) }
            item { FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf("Phone","Apps","Web","Screen").forEach { title -> FilterChip(section==title,{section=title;feedback=""},label={Text(title,maxLines=1)}) }
            } }
            if(feedback.isNotBlank())item {Text(feedback,color=EiliotColors.TextSecondary)}
            if(busy)item {LinearProgressIndicator(Modifier.fillMaxWidth())}
            when(section) {
                "Phone" -> {
                    item { TextButton(onClick={run("device_info")},enabled=!busy) {Text("Phone status")} }
                    item {Text("Media volume · ${volume.toInt()}%",color=EiliotColors.TextSecondary);Slider(volume,{volume=it},valueRange=0f..100f);TextButton(onClick={run("set_media_volume",JSONObject().put("percent",volume.toInt()))},enabled=!busy) {Text("Set volume")} }
                    item {HudTextField(minutes,{minutes=it.filter(Char::isDigit).take(4)},"Timer minutes");TextButton(onClick={run("set_timer",JSONObject().put("seconds",(minutes.toIntOrNull()?:0)*60))},enabled=!busy && (minutes.toIntOrNull()?:0) in 1..1440) {Text("Start timer")} }
                    item {FlowRow { listOf("back","home","recents","notifications").forEach { action ->TextButton(onClick={run("navigate",JSONObject().put("action",action))},enabled=!busy) {Text(action.replaceFirstChar(Char::uppercase),maxLines=1)} } } }
                    item {TextButton(onClick={run("open_settings",JSONObject().put("page","internet"))}) {Text("Internet settings")}}
                }
                "Apps" -> {
                    item {HudTextField(query,{query=it},"Find an app")}
                    items(PhoneTools.apps(query).take(40),key={it.packageName}) { app ->TextButton(onClick={run("open_app",JSONObject().put("package",app.packageName))},enabled=!busy) {Text(app.name,maxLines=1,overflow=TextOverflow.Ellipsis)} }
                }
                "Web" -> {
                    item {HudTextField(query,{query=it},"Search the web");TextButton(onClick={EiliotBrain.send("Search the web for: $query");onDismiss()},enabled=query.isNotBlank()) {Text("Ask ELLIOT")}}
                    item {HudTextField(url,{url=it},"Public HTTPS address");FlowRow {
                        TextButton(onClick={run("open_url",JSONObject().put("url",url))},enabled=url.isNotBlank() && !busy) {Text("Open page")}
                        TextButton(onClick={EiliotBrain.send("Read this page and explain it: $url");onDismiss()},enabled=url.isNotBlank()) {Text("Read with ELLIOT")}
                    } }
                }
                else -> {
                    item {Text("Open your target app, then ask through Hey Elliot or the floating orb: Read this screen, Scroll down, or Tap the item you name. The orb also has manual screen controls.",color=EiliotColors.TextSecondary)}
                    item {Text("ELLIOT excludes its own screens and protected fields from screen reading.",color=EiliotColors.TextSecondary)}
                    item {TextButton(onClick={
                        SentinelRuntime.store.orbEnabled=true
                        onDismiss()
                        if(OverlayManager.canDraw(context))OverlayManager.show(context)else OverlayManager.requestPermission(context)
                    },enabled=SentinelRuntime.alive()) {Text("Enable floating companion",maxLines=1,overflow=TextOverflow.Ellipsis)}}
                }
            }
            item {TextButton(onClick={onDismiss();onSettings()}) {Text("Permissions & settings")}}
        }
    }
}
