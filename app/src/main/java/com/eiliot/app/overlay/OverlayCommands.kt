package com.eiliot.app.overlay

enum class OverlayLayoutCommand { LEFT, RIGHT, RESET, CAPTIONS_OFF, CAPTIONS_ON }
object OverlayCommands {
    fun parse(raw:String):OverlayLayoutCommand? = when(raw.trim().lowercase().trimEnd('.', '!', '?')) {
        "move your orb left", "move elliot's orb left" -> OverlayLayoutCommand.LEFT
        "move your orb right", "move elliot's orb right" -> OverlayLayoutCommand.RIGHT
        "reset your orb position", "reset elliot's orb position" -> OverlayLayoutCommand.RESET
        "hide your captions", "hide elliot's captions" -> OverlayLayoutCommand.CAPTIONS_OFF
        "show your captions", "show elliot's captions" -> OverlayLayoutCommand.CAPTIONS_ON
        else -> null
    }
}
