package com.eiliot.app.overlay

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow

data class OverlayAppearance(
    val sizeDp: Int = 80, val opacity: Float = 1f, val captionSp: Int = 14,
    val timeoutSeconds: Int = 20, val motion: String = "system", val typing: Boolean = true,
    val haptics: Boolean = true, val captions: Boolean = true
)

/** Presentation preferences only; legacy position is retained for rollback. */
class OverlayLayoutStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("elliot_overlay_layout_v1", Context.MODE_PRIVATE)
    private val legacy = context.applicationContext.getSharedPreferences("eiliot_orb_geometry", Context.MODE_PRIVATE)
    val changes = MutableStateFlow(0)
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> changes.value++ }
    init { prefs.registerOnSharedPreferenceChangeListener(listener) }
    private fun integer(key: String, default: Int) = runCatching { prefs.getInt(key, default) }.getOrDefault(default)
    private fun decimal(key: String, default: Float) = runCatching { prefs.getFloat(key, default) }.getOrDefault(default)
    private fun flag(key: String, default: Boolean) = runCatching { prefs.getBoolean(key, default) }.getOrDefault(default)
    private fun text(key: String, default: String) = runCatching { prefs.getString(key, default).orEmpty() }.getOrDefault(default)
    fun appearance() = OverlayAppearance(
        integer("size", 80).coerceIn(56, 112), decimal("opacity", 1f).let { if (it.isFinite()) it.coerceIn(.6f, 1f) else 1f },
        integer("caption_sp", 14).coerceIn(14, 20), integer("timeout", 20).coerceIn(5, 60),
        text("motion", "system").takeIf { it in setOf("system", "reduced", "expressive") } ?: "system",
        flag("typing", true), flag("haptics", true), flag("captions", true)
    )
    fun save(value: OverlayAppearance) {
        prefs.edit().putInt("schema", 1).putInt("size", value.sizeDp.coerceIn(56, 112))
            .putFloat("opacity", if (value.opacity.isFinite()) value.opacity.coerceIn(.6f, 1f) else 1f)
            .putInt("caption_sp", value.captionSp.coerceIn(14, 20)).putInt("timeout", value.timeoutSeconds.coerceIn(5, 60))
            .putString("motion", value.motion.takeIf { it in setOf("system", "reduced", "expressive") } ?: "system")
            .putBoolean("typing", value.typing).putBoolean("haptics", value.haptics).putBoolean("captions", value.captions).apply()
    }
    fun anchor(profile: String, bounds: OverlayRect, size: Int): OverlayAnchor {
        val key = "anchor_$profile"
        if (!prefs.contains("${key}_edge")) {
            val migrated = if (!flag("legacy_imported", false) && legacy.contains("x") && legacy.contains("y")) {
                OverlayGeometry.clamp(bounds, runCatching { legacy.getInt("x", bounds.right-size) }.getOrDefault(bounds.right-size),
                    runCatching { legacy.getInt("y", bounds.top+bounds.height/2) }.getOrDefault(bounds.top), size, size)?.let { OverlayGeometry.anchor(bounds,it) }
            } else null
            saveAnchor(profile, migrated ?: OverlayAnchor())
            prefs.edit().putBoolean("legacy_imported", true).putInt("schema",1).apply()
        }
        val edge = runCatching { DockEdge.valueOf(text("${key}_edge", "RIGHT")) }.getOrDefault(DockEdge.RIGHT)
        return OverlayAnchor(edge, decimal("${key}_fraction", .5f)).sanitized()
    }
    fun saveAnchor(profile: String, anchor: OverlayAnchor) {
        val a = anchor.sanitized()
        prefs.edit().putString("anchor_${profile}_edge", a.edge.name).putFloat("anchor_${profile}_fraction", a.fraction).apply()
    }
    fun reset() { prefs.edit().clear().putBoolean("legacy_imported", true).putInt("schema", 1).apply() }
    fun reducedMotion(context: Context): Boolean = appearance().motion == "reduced" ||
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
    fun close() { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    companion object {
        fun hapticsEnabled(context: Context) = runCatching {
            context.applicationContext.getSharedPreferences("elliot_overlay_layout_v1",Context.MODE_PRIVATE).getBoolean("haptics",true)
        }.getOrDefault(true)
    }
}
