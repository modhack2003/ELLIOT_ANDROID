package com.eiliot.app.core

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.ArrayDeque
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Rect
import android.os.Bundle
import android.app.KeyguardManager
import org.json.JSONObject
import org.json.JSONArray

data class ScreenSnapshot(val app: String, val text: String, val time: Long = System.currentTimeMillis())

/** Owner-triggered screen reads and separately enabled/approved node actions. No hidden recording. */
class ScreenReader : AccessibilityService() {
    override fun onServiceConnected() { instance = this; connected.value = true }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit
    override fun onDestroy() { instance = null; connected.value = false; super.onDestroy() }
    private data class Ref(val fingerprint: String, val label: String, val editable: Boolean)
    private var references = emptyMap<Int, Ref>()
    private var referencePackage = ""
    private var referenceTime = 0L
    private fun root(): AccessibilityNodeInfo {
        check(SentinelRuntime.alive() && SentinelRuntime.store.screenEnabled) { "Enable screen reading in Settings" }
        check(!getSystemService(KeyguardManager::class.java).isKeyguardLocked) { "Unlock the phone yourself before screen access" }
        val node = rootInActiveWindow ?: error("This app exposes no screen tree")
        check(ContentPolicy.screenPackageAllowed(node.packageName?.toString().orEmpty(), packageName)) {
            "Open a non-sensitive target app and use ELLIOT's floating orb there; the companion's own screen and protected apps are excluded"
        }
        return node
    }
    private fun nodes(root: AccessibilityNodeInfo): List<Pair<AccessibilityNodeInfo, Ref>> {
        val queue = ArrayDeque<AccessibilityNodeInfo>(); queue.add(root)
        val result = mutableListOf<Pair<AccessibilityNodeInfo, Ref>>(); var visited = 0
        while (queue.isNotEmpty() && visited++ < 1800 && result.size < 120) {
            val node = queue.removeFirst()
            if (node.isPassword || !node.isVisibleToUser) continue
            val label = (if (node.isEditable) node.hintText ?: node.contentDescription else node.text ?: node.contentDescription)?.toString()?.take(250).orEmpty()
            if (label.isNotBlank() || node.isClickable || node.isEditable || node.isScrollable) {
                val bounds = Rect(); node.getBoundsInScreen(bounds)
                val ref = Ref("${node.windowId}|${node.viewIdResourceName}|${node.className}|$bounds|$label|${node.isEditable}|${node.isClickable}", label, node.isEditable)
                result += node to ref
            }
            if (!node.isEditable) for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
        }
        return result
    }
    private fun toolRead(expected: ScreenTarget?): String {
        val root = root(); expected?.requireSame(ScreenTarget(root.packageName.toString(), root.windowId)); val rows = nodes(root)
        referencePackage = root.packageName.toString(); referenceTime = android.os.SystemClock.elapsedRealtime()
        val exported = mutableMapOf<Int, Ref>()
        val data = JSONArray()
        for ((i, pair) in rows.withIndex()) {
            val (node, ref) = pair
            val entry = JSONObject().put("ref", i + 1).put("label", ref.label).put("editable", node.isEditable).put("clickable", node.isClickable).put("scrollable", node.isScrollable)
            if (data.toString().length + entry.toString().length > 7000) break
            data.put(entry); exported[i + 1] = ref
        }
        references = exported
        return JSONObject().put("app", referencePackage).put("untrusted_visible_elements", data).put("references_expire_seconds", 120).toString()
    }
    private fun nodeAction(ref: Int, text: String?): String {
        check(SentinelRuntime.store.controlEnabled) { "Enable phone control in Settings" }
        check(android.os.SystemClock.elapsedRealtime() - referenceTime <= 120000) { "Screen references expired; read the screen again" }
        val root = root(); check(root.packageName.toString() == referencePackage) { "The foreground app changed; read the screen again" }
        val old = references[ref] ?: error("Unknown screen reference")
        val current = nodes(root).getOrNull(ref - 1) ?: error("The screen changed; read it again")
        check(old.fingerprint == current.second.fingerprint) { "The screen changed; read it again before acting" }
        val node = current.first
        val changed = if (text != null) {
            check(node.isEditable && !node.isPassword) { "This reference is not an editable non-password field" }
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) })
        } else {
            check(!Regex("(?i)\\b(send|pay|purchase|buy|delete|remove|uninstall|transfer|allow|grant|install|confirm|submit|post|publish|reset|erase|subscribe|call)\\b").containsMatchIn(old.label)) {
                "Finish this sensitive submission or permission control yourself in the app"
            }
            check(node.isClickable) { "This reference is not clickable; choose a clickable item from a fresh screen read" }
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        references = emptyMap()
        check(changed) { "Android or the target app rejected this action" }
        return "Android accepted the ${if (text == null) "tap" else "text replacement"}. Read the screen again to verify the result."
    }
    private fun captureNow(): ScreenSnapshot {
        val root = root()
        val pkg = root.packageName?.toString().orEmpty()
        check(ContentPolicy.screenPackageAllowed(pkg, packageName)) { "Protected screen excluded. Open a non-sensitive app, then use the orb's Read screen button." }
        val queue = ArrayDeque<AccessibilityNodeInfo>(); queue.add(root)
        val lines = linkedSetOf<String>(); var visited = 0; var length = 0
        while (queue.isNotEmpty() && visited++ < 1800 && length < 9000) {
            val node = queue.removeFirst()
            if (!node.isPassword && !node.isEditable && node.isVisibleToUser) {
                val value = (node.text ?: node.contentDescription)?.toString()?.trim().orEmpty()
                if (value.isNotBlank() && lines.add(value)) length += value.length
                for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
            }
        }
        check(lines.isNotEmpty()) { "No non-sensitive text is available on this screen" }
        return ScreenSnapshot(pkg, lines.joinToString("\n").take(9000))
    }
    companion object {
        private var instance: ScreenReader? = null
        val connected = MutableStateFlow(false)
        val snapshot = MutableStateFlow<ScreenSnapshot?>(null)
        val message = MutableStateFlow("")
        fun capture() {
            runCatching { (instance ?: error("Enable ELLIOT screen reading in Android Accessibility settings")).captureNow() }
                .onSuccess { snapshot.value = it; message.value = "Selection stays local until you choose Explain" }
                .onFailure { snapshot.value = null; message.value = it.message.orEmpty() }
        }
        fun clear() { snapshot.value = null; message.value = ""; instance?.references = emptyMap() }
        fun describe(ref: Int, text: String? = null): String {
            val service = instance ?: error("Accessibility is not connected")
            val item = service.references[ref] ?: error("Read the target screen first to get a fresh reference")
            check(item.label.isNotBlank()) { "This item has no readable label; handle it directly in the target app" }
            return "App: ${service.referencePackage}\nItem: ${item.label}\n" + if (text == null) "Tap this item once." else "Replace this field with:\n$text\nThis does not press Send."
        }
        fun label(ref: Int): String = (instance ?: error("Accessibility is not connected")).references[ref]?.label ?: error("Read the target screen first")
        fun target(): ScreenTarget {
            val root = (instance ?: error("Enable ELLIOT in Android Accessibility settings")).root()
            return ScreenTarget(root.packageName.toString(), root.windowId)
        }
        fun readForTool(expected: ScreenTarget? = null): String = (instance ?: error("Enable ELLIOT in Android Accessibility settings")).toolRead(expected)
        fun act(ref: Int, text: String? = null): String = (instance ?: error("Accessibility is not connected")).nodeAction(ref, text)
        fun navigate(action: String): String {
            check(SentinelRuntime.alive() && SentinelRuntime.store.controlEnabled) { "Resume ELLIOT and enable phone control" }
            val service = instance ?: error("Enable ELLIOT in Android Accessibility settings")
            check(!service.getSystemService(KeyguardManager::class.java).isKeyguardLocked) { "Unlock your phone first" }
            val value = when (action) { "back" -> GLOBAL_ACTION_BACK; "home" -> GLOBAL_ACTION_HOME; "recents" -> GLOBAL_ACTION_RECENTS; "notifications" -> GLOBAL_ACTION_NOTIFICATIONS; else -> error("Unknown navigation action") }
            check(service.performGlobalAction(value)) { "Android rejected this navigation action" }
            service.references = emptyMap(); return "Android accepted $action navigation."
        }
        fun scroll(direction: String, expected: ScreenTarget? = null): String {
            check(SentinelRuntime.store.controlEnabled) { "Enable phone control" }
            val service = instance ?: error("Accessibility is not connected")
            val root = service.root()
            expected?.requireSame(ScreenTarget(root.packageName.toString(), root.windowId))
            val action = if (direction == "down") AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            check(service.nodes(root).firstOrNull { it.first.isScrollable }?.first?.performAction(action) == true) { "No scrollable area accepted the action" }
            service.references = emptyMap(); return "Android accepted one $direction scroll. Read the screen again for fresh references."
        }
        fun settings(context: android.content.Context) { com.eiliot.app.overlay.OverlayManager.suspendForSettings();context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
