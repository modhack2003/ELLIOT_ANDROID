package com.eiliot.app.core

/** Approval is for the app/window that was reviewed, not whatever appears afterward. */
data class ScreenTarget(val app: String, val window: Int) {
    fun requireSame(current: ScreenTarget) {
        check(this == current) { "The foreground app or window changed while approval was pending. Read the target screen and try again." }
    }
}
