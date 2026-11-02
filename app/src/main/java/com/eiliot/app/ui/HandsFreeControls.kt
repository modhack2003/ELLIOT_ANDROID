package com.eiliot.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eiliot.app.core.*
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Schedule controls only: voice and permission switches have a single home in Settings. */
@Composable fun HandsFreeControls(enabled:Boolean,onArm:()->Unit={}) {
    val store=SentinelRuntime.store
    var proactive by remember {mutableStateOf(store.proactiveEnabled)}
    var interval by remember {mutableStateOf(store.checkInInterval.toString())}
    fun time(value:Int)="%02d:%02d".format(value/60,value%60)
    var start by remember {mutableStateOf(time(store.checkInStart))}
    var end by remember {mutableStateOf(time(store.checkInEnd))}
    var feedback by remember {mutableStateOf("")}
    GlassPanel(accent=EiliotColors.Violet) {
        ControlSwitch("ELLIOT approaches me","A short spoken check-in on your schedule",proactive) {proactive=it;ProactiveEngine.enabled=it}
        if(proactive) {
            HudTextField(interval,{interval=it.filter(Char::isDigit).take(4)},"Minutes between check-ins")
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                HudTextField(start,{start=it.take(5)},"From",modifier=Modifier.weight(1f))
                HudTextField(end,{end=it.take(5)},"Until",modifier=Modifier.weight(1f))
            }
            Text("Uses your local time. Overnight hours work; equal times mean all day. DND, snooze and active calls stay quiet.",color=EiliotColors.TextSecondary)
            TextButton(onClick={
                runCatching {
                    val from=LocalTime.parse(start,DateTimeFormatter.ofPattern("HH:mm"));val until=LocalTime.parse(end,DateTimeFormatter.ofPattern("HH:mm"))
                    val minutes=interval.toIntOrNull()?:error("Enter minutes");require(minutes in 5..1440)
                    store.checkInStart=from.hour*60+from.minute;store.checkInEnd=until.hour*60+until.minute;store.checkInInterval=minutes;ProactiveEngine.resetSchedule()
                }.onSuccess {feedback="Schedule saved"}.onFailure {feedback="Use HH:mm times and 5–1440 minutes."}
            }) {Text("Save schedule",maxLines=1,overflow=TextOverflow.Ellipsis)}
            Row {
                TextButton(onClick={feedback=if(ProactiveEngine.checkInNow())"I'm here." else "Turn ELLIOT on and check voice access."},enabled=enabled) {Text("Ask me now",maxLines=1,overflow=TextOverflow.Ellipsis)}
                TextButton(onClick={ProactiveEngine.snooze();feedback="Quiet for one hour"},enabled=enabled) {Text("Snooze",maxLines=1)}
            }
            if(feedback.isNotBlank())Text(feedback,color=EiliotColors.Teal)
        }
    }
}

