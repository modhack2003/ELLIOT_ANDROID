package com.eiliot.app.core

/** An Android permission result cannot revive an intention cancelled by Off or erasure. */
data class PermissionRequest(val epoch: Long, val needsRunning: Boolean) {
    fun isCurrent(currentEpoch: Long, running: Boolean, erasing: Boolean): Boolean =
        epoch == currentEpoch && !erasing && (!needsRunning || running)
}
