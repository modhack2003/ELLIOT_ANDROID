package com.eiliot.app.overlay

import android.app.KeyguardManager
import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.database.ContentObserver
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowInsets
import android.view.WindowManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.max
import kotlin.math.roundToInt

data class OverlayViewport(val stable: OverlayRect, val usable: OverlayRect, val profile: String,
    val density: Float, val fontScale: Float, val locked: Boolean, val interactive: Boolean, val imeObserved: Boolean)

/** Owns passive layout observation only. A relayout never starts voice or replays a task. */
class OverlayWindowController(private val context: Context, private val changed: () -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private val wm = context.getSystemService(WindowManager::class.java)
    private val observed = linkedMapOf<View, ViewTreeObserver.OnGlobalLayoutListener>()
    private var imeBottom = 0
    private var active = false
    private var last: OverlayViewport? = null
    private val refresh = Runnable { if (active) changed() }
    private val watch = object : Runnable {
        override fun run() {
            if (!active) return
            val next = viewport()
            if (next != last || !android.provider.Settings.canDrawOverlays(context)) { last = next; changed() }
            main.postDelayed(this, 1000)
        }
    }
    private val configuration = object : ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) { imeBottom = 0; request() }
        override fun onLowMemory() = Unit
    }
    private val motion = object:ContentObserver(main) { override fun onChange(selfChange:Boolean) { request() } }
    fun start() {
        if (active) return
        active = true; context.registerComponentCallbacks(configuration)
        context.contentResolver.registerContentObserver(android.provider.Settings.Global.getUriFor(android.provider.Settings.Global.ANIMATOR_DURATION_SCALE),false,motion)
        main.post(watch)
    }
    fun observe(view: View) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val bottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            // Only the focusable input window owns our IME observation. An orb's zero
            // insets must not erase a keyboard measurement delivered to the panel.
            if (view.hasFocus() || insets.isVisible(WindowInsetsCompat.Type.ime())) {
                if (imeBottom != bottom) { imeBottom = bottom; request() }
            }
            insets
        }
        val listener = ViewTreeObserver.OnGlobalLayoutListener { request() }
        observed[view] = listener; view.viewTreeObserver.addOnGlobalLayoutListener(listener)
        ViewCompat.requestApplyInsets(view)
    }
    fun unobserve(view: View) {
        observed.remove(view)?.let { if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnGlobalLayoutListener(it) }
        ViewCompat.setOnApplyWindowInsetsListener(view, null)
    }
    fun clearKeyboard() { imeBottom = 0; request() }
    fun request() { main.removeCallbacks(refresh); if (active) main.postDelayed(refresh, 40) }
    fun viewport(): OverlayViewport {
        val metrics = context.resources.displayMetrics
        var width = metrics.widthPixels; var height = metrics.heightPixels
        var l = 0; var t = 0; var r = 0; var b = 0
        if (Build.VERSION.SDK_INT >= 30) {
            val window = wm.maximumWindowMetrics
            width = window.bounds.width(); height = window.bounds.height()
            val insets = window.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.mandatorySystemGestures() or WindowInsets.Type.systemGestures())
            l = insets.left; t = insets.top; r = insets.right; b = insets.bottom
        } else {
            // Insets attached to a real window are preferred on older Android. A
            // conservative resource fallback is retained before the first dispatch.
            observed.keys.firstNotNullOfOrNull { ViewCompat.getRootWindowInsets(it) }?.let {
                val inset = it.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.systemGestures())
                l = inset.left; t = inset.top; r = inset.right; b = inset.bottom
            } ?: run {
                fun resource(name: String): Int {
                    val id = context.resources.getIdentifier(name, "dimen", "android")
                    return if (id > 0) context.resources.getDimensionPixelSize(id) else 0
                }
                t = resource("status_bar_height"); b = resource("navigation_bar_height")
            }
        }
        val density = metrics.density.takeIf { it > 0 } ?: 1f
        val margin = (8 * density).roundToInt()
        val stable = OverlayGeometry.safeBounds(width,height,l,t,r,b,margin)
        var keyboard = imeBottom.coerceAtLeast(0)
        // Legacy/undelivered IME fallback only from our deliberately focused window.
        observed.keys.firstOrNull { it.hasFocus() }?.let { view ->
            val frame = Rect(); view.getWindowVisibleDisplayFrame(frame)
            if (frame.width() >= stable.width * .75f && frame.bottom > stable.top && frame.bottom <= height) {
                keyboard = max(keyboard, height - frame.bottom)
            }
        }
        val usable = OverlayGeometry.safeBounds(width,height,l,t,r,max(b,keyboard),margin)
        return OverlayViewport(stable,usable,OverlayGeometry.profile(width,height,density),density,
            context.resources.configuration.fontScale,context.getSystemService(KeyguardManager::class.java).isKeyguardLocked,
            context.getSystemService(PowerManager::class.java).isInteractive,keyboard > b)
    }
    fun close() {
        if (active) { context.unregisterComponentCallbacks(configuration);context.contentResolver.unregisterContentObserver(motion) }
        active = false; main.removeCallbacks(refresh); main.removeCallbacks(watch)
        observed.keys.toList().forEach(::unobserve); imeBottom = 0; last = null
    }
}
