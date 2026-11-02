package com.eiliot.app.overlay

import kotlin.math.roundToInt

data class OverlayRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = (right - left).coerceAtLeast(0)
    val height get() = (bottom - top).coerceAtLeast(0)
    fun contains(other: OverlayRect) = other.left >= left && other.top >= top && other.right <= right && other.bottom <= bottom
    fun overlaps(other: OverlayRect) = left < other.right && right > other.left && top < other.bottom && bottom > other.top
}
enum class DockEdge { LEFT, RIGHT }
data class OverlayAnchor(val edge: DockEdge = DockEdge.RIGHT, val fraction: Float = 0.5f) {
    fun sanitized() = copy(fraction = if (fraction.isFinite()) fraction.coerceIn(0f, 1f) else 0.5f)
}
enum class OverlayPanelMode { FULL, COMPACT, INPUT }
data class OverlayPlacement(val rect: OverlayRect, val mode: OverlayPanelMode)

/** Pixel calculations only. No Android state, window allocation, focus changes or task execution. */
object OverlayGeometry {
    fun safeBounds(width: Int, height: Int, left: Int = 0, top: Int = 0, right: Int = 0, bottom: Int = 0, margin: Int = 0): OverlayRect {
        val w = width.coerceAtLeast(0); val h = height.coerceAtLeast(0); val m = margin.coerceAtLeast(0)
        val l = (left.coerceAtLeast(0).toLong() + m).coerceAtMost(w.toLong()).toInt()
        val t = (top.coerceAtLeast(0).toLong() + m).coerceAtMost(h.toLong()).toInt()
        val r = (w.toLong() - right.coerceAtLeast(0) - m).coerceIn(l.toLong(), w.toLong()).toInt()
        val b = (h.toLong() - bottom.coerceAtLeast(0) - m).coerceIn(t.toLong(), h.toLong()).toInt()
        return OverlayRect(l, t, r, b)
    }
    fun clamp(bounds: OverlayRect, x: Int, y: Int, width: Int, height: Int): OverlayRect? {
        if (width <= 0 || height <= 0 || width > bounds.width || height > bounds.height) return null
        val px = x.coerceIn(bounds.left, bounds.right - width); val py = y.coerceIn(bounds.top, bounds.bottom - height)
        return OverlayRect(px, py, px + width, py + height)
    }
    fun orb(bounds: OverlayRect, size: Int, anchor: OverlayAnchor): OverlayRect? {
        val a = anchor.sanitized()
        val y = bounds.top + ((bounds.height - size).coerceAtLeast(0) * a.fraction).roundToInt()
        return clamp(bounds, if (a.edge == DockEdge.LEFT) bounds.left else bounds.right - size, y, size, size)
    }
    fun anchor(bounds: OverlayRect, rect: OverlayRect): OverlayAnchor {
        val edge = if (rect.left + rect.width / 2 < bounds.left + bounds.width / 2) DockEdge.LEFT else DockEdge.RIGHT
        val span = bounds.height - rect.height
        return OverlayAnchor(edge, if (span > 0) (rect.top - bounds.top).toFloat() / span else 0.5f).sanitized()
    }
    fun panel(bounds: OverlayRect, orb: OverlayRect, density: Float, fontScale: Float, typing: Boolean = false): OverlayPlacement? {
        val d = density.takeIf { it.isFinite() && it > 0 } ?: 1f
        val scale = fontScale.takeIf { it.isFinite() }?.coerceIn(1f, 3f) ?: 1f
        fun dp(v: Float) = (v * d).roundToInt()
        val width = minOf(dp(344f), bounds.width)
        if (width < dp(216f)) return null
        val gap=dp(8f)
        val sideRoom=maxOf(orb.left-bounds.left-gap,bounds.right-orb.right-gap)
        val available=maxOf(orb.top-bounds.top-gap,bounds.bottom-orb.bottom-gap,if(sideRoom>=width)bounds.height else 0)
        val budget = minOf(dp(360f), (bounds.height * 0.45f).toInt(),available)
        val fullMin = dp(280f + 48f * (scale - 1f))
        val compactMin = dp(116f + 20f * (scale - 1f))
        val mode = if (budget >= fullMin) OverlayPanelMode.FULL else if (budget >= compactMin) {
            if (typing) OverlayPanelMode.INPUT else OverlayPanelMode.COMPACT
        } else return null
        val height = if (mode == OverlayPanelMode.FULL) budget else compactMin
        val rect = adjacent(bounds, orb, width, height, dp(8f)) ?: return null
        return OverlayPlacement(rect, mode)
    }
    fun adjacent(bounds: OverlayRect, orb: OverlayRect, width: Int, height: Int, gap: Int): OverlayRect? {
        if (width <= 0 || height <= 0 || width > bounds.width || height > bounds.height) return null
        val candidates = listOf(
            clamp(bounds, orb.right - width, orb.bottom + gap, width, height),
            clamp(bounds, orb.right - width, orb.top - height - gap, width, height),
            clamp(bounds, orb.left - width - gap, orb.top, width, height),
            clamp(bounds, orb.right + gap, orb.top, width, height)
        )
        return candidates.filterNotNull().firstOrNull { !it.overlaps(orb) }
    }
    fun profile(width: Int, height: Int, density: Float): String {
        val d = density.takeIf { it > 0 && it.isFinite() } ?: 1f
        return (if (height >= width) "portrait" else "landscape") + if (width / d >= 600) "_wide" else "_compact"
    }
}

/** Gesture state is separate from actions; cancellation or a drag never becomes a click. */
class OrbGesture(private val slop: Float, private val longPressMs: Long) {
    enum class Result { TAP, TALK, DRAG, CANCEL }
    private var x = 0f; private var y = 0f; private var downAt = 0L
    private var active = false; private var dragged = false
    fun down(x: Float, y: Float, time: Long) { this.x = x; this.y = y; downAt = time; active = true; dragged = false }
    fun move(x: Float, y: Float): Boolean {
        if (!active) return false
        val dx=x-this.x;val dy=y-this.y
        if (dx*dx+dy*dy > slop*slop) dragged = true
        return dragged
    }
    fun end(time: Long): Result {
        if (!active) return Result.CANCEL
        active = false
        return if (dragged) Result.DRAG else if (time - downAt >= longPressMs) Result.TALK else Result.TAP
    }
    fun cancel() { active = false; dragged = false }
}
