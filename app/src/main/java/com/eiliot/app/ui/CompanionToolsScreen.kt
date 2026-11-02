package com.eiliot.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eiliot.app.core.*
import com.eiliot.app.llm.ToolCall
import com.eiliot.app.tools.*
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable fun ActionApprovalDialog() {
    val action by ActionApprovals.pending.collectAsStateWithLifecycle()
    action?.let { pending ->
        AlertDialog(onDismissRequest = { ActionApprovals.resolve(pending.id, false) },
            title = { Text(pending.title) }, text = {
                Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                    Text("ELLIOT needs your approval for this step.", color = EiliotColors.Teal)
                    Spacer(Modifier.height(12.dp)); Text(pending.detail)
                    Spacer(Modifier.height(12.dp)); Text("Decline stops this action. Global Off cancels the task and pending approvals.", fontSize = 12.sp)
                }
            }, confirmButton = { TextButton(onClick = { ActionApprovals.resolve(pending.id, true) }) { Text("Allow this step") } },
            dismissButton = { TextButton(onClick = { ActionApprovals.resolve(pending.id, false) }) { Text("Decline") } })
    }
}

@Composable fun ToolActivityCard(activities: List<ToolActivity>) {
    GlassPanel(accent = EiliotColors.Teal, contentPadding = PaddingValues(12.dp)) {
        Text("TASK ACTIVITY", color = EiliotColors.Teal, fontSize = 10.sp)
        activities.forEach { entry ->
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(entry.name, color = EiliotColors.TextPrimary, fontSize = 12.sp, modifier = Modifier.weight(1f),maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(entry.status, color = if (entry.status == "Needs attention") EiliotColors.Amber else EiliotColors.TextSecondary, fontSize = 10.sp,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
        }
    }
}

