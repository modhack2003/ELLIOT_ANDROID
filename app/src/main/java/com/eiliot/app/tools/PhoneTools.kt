package com.eiliot.app.tools

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.AlarmClock
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.eiliot.app.core.*
import com.eiliot.app.capabilities.CapabilityRegistry
import com.eiliot.app.capabilities.CapabilityBlocked
import com.eiliot.app.llm.ToolCall
import com.eiliot.app.overlay.OverlayManager
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject

data class LaunchableApp(val name: String, val packageName: String)

object PhoneTools {
    private val context: Context get() = SentinelRuntime.context
    fun cancelAll() { ActionApprovals.cancel(); WebTools.cancelAll() }
    fun apps(query: String = ""): List<LaunchableApp> = context.packageManager.queryIntentActivities(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0).map {
        LaunchableApp(it.loadLabel(context.packageManager).toString(), it.activityInfo.packageName)
    }.distinctBy { it.packageName }.filter { it.name.contains(query, true) || it.packageName.contains(query, true) }.sortedBy { it.name.lowercase() }

    fun deviceInfo(): Map<String, String> {
        val memory = ActivityManager.MemoryInfo().also { context.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }
        val storage = StatFs(Environment.getDataDirectory().absolutePath)
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val charge = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val caps = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
        val network = when { caps == null -> "Disconnected"; caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"; caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile data"; caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"; else -> "Connected transport" }
        fun gb(bytes: Long) = "%.1f GB".format(java.util.Locale.US, bytes / 1073741824.0)
        val capabilities = CapabilityRegistry.refresh()
        return linkedMapOf(
            "Device" to "${Build.MANUFACTURER} ${Build.MODEL}", "Android" to "${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}",
            "Security patch" to Build.VERSION.SECURITY_PATCH, "CPU" to "${Runtime.getRuntime().availableProcessors()} cores · ${Build.SUPPORTED_ABIS.joinToString()}",
            "RAM" to "${gb(memory.availMem)} available / ${gb(memory.totalMem)} total",
            "Internal storage" to "${gb(storage.availableBytes)} free / ${gb(storage.totalBytes)} total",
            "Battery" to "${context.getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)}% · ${if (charge == BatteryManager.BATTERY_STATUS_CHARGING || charge == BatteryManager.BATTERY_STATUS_FULL) "charging/powered" else "on battery"}",
            "Network" to "$network · ${if (caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true) "internet validated" else "internet not validated"} · ${if (connectivity.isActiveNetworkMetered) "metered" else "unmetered"}",
            "Display" to "${context.resources.displayMetrics.widthPixels} × ${context.resources.displayMetrics.heightPixels} px",
            "Local time" to java.time.ZonedDateTime.now().toString(),
            "Companion" to if (SentinelRuntime.alive()) "Running" else "Off",
            "Phone tools" to if (SentinelRuntime.store.phoneToolsEnabled) "Enabled · ${if (SentinelRuntime.store.reviewActions) "optional review mode" else "direct command mode"}" else "Disabled",
            "Web tools" to if (SentinelRuntime.store.webToolsEnabled) "Enabled · public HTTPS" else "Disabled",
            "Microphone permission" to if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) "Granted" else "Not granted",
            "Background microphone" to if (SentinelRuntime.microphoneReady) "Armed" else "Not armed",
            "Floating overlay" to if (OverlayManager.canDraw(context)) "Granted" else "Not granted",
            "Accessibility" to if (ScreenReader.connected.value) "Connected" else "Not connected",
            "Screen reading" to if (SentinelRuntime.store.screenEnabled && ScreenReader.connected.value) "Ready when requested" else "Needs switch and Accessibility",
            "Screen actions" to if (SentinelRuntime.store.controlEnabled && ScreenReader.connected.value) "Enabled · scoped to your request" else "Disabled or permission missing",
            "Capability readiness" to "${capabilities.readyTools}/16 ready now · ${capabilities.availableTools}/16 can attempt with stated conditions",
            "Live capabilities" to capabilities.modelContext(),
            "System access boundary" to "Normal Android app sandbox. No root, other apps' private files, silent permission grants, or lock bypass."
        )
    }
    private fun detail(spec: ToolSpec, args: JSONObject): String = when (spec.name) {
        "read_screen" -> "Read the visible non-sensitive text in your current app and share it with ${if (SentinelRuntime.store.inferenceMode == "cloud") "your configured cloud model" else "the on-device model"} for this task. Passwords, editable text values and protected apps are excluded."
        "open_app" -> "Launch ${apps().firstOrNull { it.packageName == args.optString("package") }?.name ?: args.optString("package")}."
        "save_memory" -> "Remember this note on this phone:\n${args.optString("text")}"
        "click_screen" -> ScreenReader.describe(args.getInt("ref"))
        "type_text" -> ScreenReader.describe(args.getInt("ref"), args.getString("text"))
        else -> args.keys().asSequence().joinToString("\n") { "$it: ${args.get(it)}" }.ifBlank { spec.description }
    }
    suspend fun execute(call: ToolCall, grant: CommandGrant? = null): String {
        val epoch = SentinelRuntime.epoch
        return try {
            currentCoroutineContext().ensureActive()
            check(SentinelRuntime.alive(epoch) && SentinelRuntime.store.phoneToolsEnabled) { "Resume ELLIOT and enable companion tools in Settings" }
            val spec = ToolCatalog.get(call.name); require(call.arguments.length <= 12000) { "Arguments exceed limit" }
            val args = JSONObject(call.arguments); spec.validate(args)
            fun guard() {
                check(SentinelRuntime.alive(epoch)) { "Companion tools were stopped" }
                CapabilityRegistry.requireTool(call.name)
            }
            guard()
            if (spec.approval && !SentinelRuntime.store.reviewActions) {
                val command = checkNotNull(grant) { "Ask the owner for a matching request by voice; no Allow tap is required." }
                check(command.epoch == epoch && command.permits(call.name, args)) {
                    "This action needs a clear matching request. Ask the owner for the missing detail by voice; no Allow tap is required."
                }
                if (call.name == "open_app") {
                    val app = apps().firstOrNull { it.packageName == args.optString("package") } ?: error("Find a visible launchable app first")
                    check(command.permitsApp(app.packageName, app.name)) { "Which app should I open? Say its visible app name." }
                }
                if (call.name == "click_screen") check(command.permitsScreenLabel(ScreenReader.label(args.getInt("ref")))) { "Which labelled item should I tap? Say its visible label." }
            }
            var target: ScreenTarget? = null
            if (call.name in setOf("read_screen", "scroll_screen")) {
                OverlayManager.prepareForPhoneAction(); delay(150); guard()
                target = ScreenReader.target()
            }
            if (spec.approval && SentinelRuntime.store.reviewActions) {
                val description = (target?.let { "Target app: ${it.app}\n" } ?: "") + detail(spec, args)
                check(ActionApprovals.request(spec.label, description)) { "Owner declined or approval timed out; no action performed" }
                currentCoroutineContext().ensureActive(); guard()
                OverlayManager.prepareForPhoneAction(); delay(150); guard()
            } else if (spec.approval) {
                OverlayManager.prepareForPhoneAction(); delay(150); guard()
            }
            val result: String = when (call.name) {
                "device_info" -> JSONObject(deviceInfo()).toString()
                "list_apps" -> {
                    val matches = apps(args.optString("query")); val offset = args.optInt("offset", 0); val page = matches.drop(offset).take(60)
                    JSONObject().put("visible_matches", matches.size).put("returned", page.size).put("has_more", offset + page.size < matches.size)
                        .put("next_offset", if (offset + page.size < matches.size) offset + page.size else JSONObject.NULL)
                        .put("apps", JSONArray().also { a -> page.forEach { a.put(JSONObject().put("name", it.name).put("package", it.packageName)) } }).toString()
                }
                "open_app" -> {
                    val pkg = args.getString("package"); check(apps().any { it.packageName == pkg }) { "Find a launchable app first with list_apps" }
                    val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: error("No launcher activity is available")
                    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); "Launch request sent for $pkg. Verify the target screen before claiming further completion."
                }
                "open_settings" -> {
                    val action = when (args.getString("page")) {
                        "internet" -> if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_INTERNET_CONNECTIVITY else Settings.ACTION_WIRELESS_SETTINGS
                        "wifi" -> Settings.ACTION_WIFI_SETTINGS; "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
                        "display" -> Settings.ACTION_DISPLAY_SETTINGS; "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
                        "storage" -> Settings.ACTION_INTERNAL_STORAGE_SETTINGS; "sound" -> Settings.ACTION_SOUND_SETTINGS
                        "accessibility" -> Settings.ACTION_ACCESSIBILITY_SETTINGS; else -> Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                    }
                    val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (args.getString("page") == "app") intent.data = Uri.parse("package:${context.packageName}")
                    OverlayManager.suspendForSettings()
                    context.startActivity(intent); "Settings page requested. The owner must change its switches; no setting was changed by this tool."
                }
                "set_media_volume" -> {
                    val audio = context.getSystemService(AudioManager::class.java); check(!audio.isVolumeFixed) { "This device has fixed media volume" }
                    val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, (args.getInt("percent") * max / 100.0).toInt(), AudioManager.FLAG_SHOW_UI)
                    "Media volume is now ${audio.getStreamVolume(AudioManager.STREAM_MUSIC)} of $max device steps."
                }
                "set_timer" -> {
                    context.startActivity(Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, args.getInt("seconds"))
                        .putExtra(AlarmClock.EXTRA_MESSAGE, args.optString("label", "ELLIOT timer")).putExtra(AlarmClock.EXTRA_SKIP_UI, !SentinelRuntime.store.reviewActions).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    if (SentinelRuntime.store.reviewActions) "Timer request opened in the clock. The owner must confirm/start it there; it is not verified as running."
                    else "Timer start requested from the clock. The clock decides whether it can start without UI; running state is not independently verified."
                }
                "search_web" -> observeWeb { WebTools.search(args.getString("query")) }
                "read_web_page" -> observeWeb { WebTools.page(args.getString("url")) }
                "open_url" -> {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(WebPolicy.url(args.getString("url")).toString())).addCategory(Intent.CATEGORY_BROWSABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    "Browser launch requested. No page workflow was completed by this tool."
                }
                "read_screen" -> ScreenReader.readForTool(target)
                "click_screen" -> ScreenReader.act(args.getInt("ref"))
                "type_text" -> ScreenReader.act(args.getInt("ref"), args.getString("text"))
                "scroll_screen" -> ScreenReader.scroll(args.getString("direction"), target)
                "navigate" -> ScreenReader.navigate(args.getString("action"))
                "save_memory" -> {
                    check(SentinelRuntime.store.memoryEnabled) { "Memory is disabled" }
                    withContext(Dispatchers.IO) { SentinelRuntime.memory.addNote(args.getString("text")) }; "Owner-approved note saved in encrypted local memory."
                }
                "recall_memory" -> {
                    check(SentinelRuntime.store.memoryEnabled) { "Memory is disabled" }
                    SentinelRuntime.memory.recalledNotes(args.getString("query")).ifBlank { "No matching saved notes were supplied." }
                }
                else -> error("Unknown tool")
            }
            currentCoroutineContext().ensureActive(); guard()
            ToolReceipt.success(call.name, result)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            JSONObject().put("ok", false).put("tool", call.name).put("error", e.message?.take(500) ?: "Action failed").also { result ->
                if (e is CapabilityBlocked) result.put("error_code", "capability_not_ready").put("availability", e.entry.availability.name)
                    .put("recovery", e.entry.recovery.name).put("recovery_label", e.entry.recovery.label)
            }.toString()
        }
    }
    private suspend fun observeWeb(block: suspend () -> String): String {
        return try { block().also { CapabilityRegistry.recordWeb(true) } }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { CapabilityRegistry.recordWeb(false); throw e }
    }
}
