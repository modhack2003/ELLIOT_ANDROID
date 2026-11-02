package com.eiliot.app.learning

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import com.eiliot.app.core.SentinelRuntime
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import android.util.Xml
import java.util.concurrent.TimeUnit

object LearningScheduler {
    private const val JOB = 2042
    private const val KEV = "https://www.cisa.gov/sites/default/files/feeds/known_exploited_vulnerabilities.json"
    private const val ANDROID = "https://developer.android.com/static/blog/atom.xml"
    private const val KEV_MIRROR = "https://raw.githubusercontent.com/cisagov/kev-data/develop/known_exploited_vulnerabilities.json"
    val status = MutableStateFlow("Learning off · reference material stays on device")
    private var task: Job? = null
    @Volatile private var call: okhttp3.Call? = null
    fun schedule(context: Context) {
        if (!SentinelRuntime.alive() || !SentinelRuntime.store.learningEnabled || !SentinelRuntime.store.memoryEnabled) return
        val info = JobInfo.Builder(JOB, ComponentName(context, LearningJobService::class.java))
            .setPeriodic(6 * 60 * 60 * 1000L).setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
            .setRequiresCharging(true).setPersisted(true).build()
        val result = context.getSystemService(JobScheduler::class.java).schedule(info)
        status.value = if (result == JobScheduler.RESULT_SUCCESS) "Scheduled · charging + unmetered network · timing managed by Android" else "Android could not schedule learning"
    }
    fun runNow(onDone: () -> Unit = {}) {
        if (!SentinelRuntime.alive() || !SentinelRuntime.store.learningEnabled || !SentinelRuntime.store.memoryEnabled) { status.value="Turn on Memory to save web references";onDone(); return }
        if (task?.isCompleted == false) { onDone(); return }
        val token = SentinelRuntime.epoch
        task = SentinelRuntime.scope.launch(Dispatchers.IO) {
            try {
                status.value = "Reading official security and Android feeds…"
                var added = 0; var failures = 0
                try {
                    val data = try { fetch(KEV, 5000000) } catch (e: CancellationException) { throw e } catch (_: Exception) { currentCoroutineContext().ensureActive(); fetch(KEV_MIRROR, 5000000) }
                    val list = JSONObject(data).getJSONArray("vulnerabilities")
                    val rows = (0 until list.length()).map { list.getJSONObject(it) }.sortedByDescending { it.optString("dateAdded") }.take(35)
                    for (row in rows) {
                        ensureActive(); if (!SentinelRuntime.alive(token)) break
                        val cve = row.optString("cveID")
                        val text = "$cve · ${row.optString("vendorProject")} ${row.optString("product")}\n${row.optString("shortDescription")}\nCISA date added: ${row.optString("dateAdded")}. Action: ${row.optString("requiredAction")}".take(5000)
                        if (SentinelRuntime.memory.addNote(text, "https://www.cisa.gov/known-exploited-vulnerabilities-catalog", "knowledge")) added++
                    }
                } catch (e: CancellationException) { throw e } catch (_: Exception) { failures++ }
                try {
                    val entries = FeedParser.androidEntries(fetch(ANDROID, 1500000))
                    check(entries.isNotEmpty()) { "No supported Android feed entries" }
                    for (entry in entries) {
                        ensureActive(); if (!SentinelRuntime.alive(token)) break
                        if (SentinelRuntime.memory.addNote("${entry.title}\n${entry.summary}", entry.url, "knowledge")) added++
                    }
                } catch (e: CancellationException) { throw e } catch (_: Exception) { failures++ }
                if (SentinelRuntime.alive(token)) status.value = "$added new reference entries saved${if (failures > 0) " · $failures feed(s) unavailable" else " · sources attached"}"
            } catch (e: CancellationException) { status.value = "Learning stopped"; throw e }
            finally { call = null; withContext(NonCancellable + Dispatchers.Main) { onDone() } }
        }
    }
    private suspend fun fetch(url: String, limit: Int): String {
        currentCoroutineContext().ensureActive(); check(SentinelRuntime.alive()) { "Stopped" }
        val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).followRedirects(false).build()
        val req = http.newCall(Request.Builder().url(url).header("User-Agent", "ELLIOT/0.2 feed-reader").build()); call = req
        return req.execute().use { r ->
            check(r.isSuccessful) { "Feed unavailable" }
            val input = r.body?.byteStream() ?: error("Empty feed")
            val out = java.io.ByteArrayOutputStream(); val b = ByteArray(32768)
            input.use { while (true) { check(SentinelRuntime.alive()) { "Stopped" }; val n = it.read(b); if (n < 0) break; check(out.size() + n <= limit) { "Feed too large" }; out.write(b, 0, n) } }
            out.toString("UTF-8")
        }
    }
    fun cancelActive() { call?.cancel(); task?.cancel(); status.value = "Learning stopped" }
    suspend fun awaitStopped() { task?.join() }
    fun stop(context: Context) {
        context.getSystemService(JobScheduler::class.java).cancel(JOB); cancelActive()
    }
}

class LearningJobService : JobService() {
    private var active = false
    override fun onStartJob(params: JobParameters): Boolean {
        if (!SentinelRuntime.alive() || !SentinelRuntime.store.learningEnabled) return false
        active = true; LearningScheduler.runNow { if (active) { active = false; jobFinished(params, false) } }; return true
    }
    override fun onStopJob(params: JobParameters): Boolean { active = false; LearningScheduler.cancelActive(); return SentinelRuntime.alive() && SentinelRuntime.store.learningEnabled }
}
