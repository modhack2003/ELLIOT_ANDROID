package com.eiliot.app.ui

/** Keep detailed diagnostics at the boundary, not as an unfiltered exception in primary UI. */
object FriendlyStatus {
    fun error(raw:String?):String {
        val text=raw.orEmpty()
        return when {
            text.contains("401")||text.contains("403")||text.contains("authentication",true)->"The AI connection couldn't sign in. Check your key and model in Settings."
            text.contains("429")||text.contains("rate.limit",true)->"Your AI provider is busy. Please try again shortly."
            text.contains("foreground",true)||text.contains("Nano requires",true)->"Keep ELLIOT open to use the on-device model."
            text.contains("key",true)||text.contains("endpoint",true)||text.contains("Configure the cloud",true)->"Connect an AI provider in Settings, or use an offline phone command."
            text.contains("microphone",true)||text.contains("speech",true)->"Voice needs attention. Check microphone access in Settings."
            text.contains("Unlock",true)->"Unlock your phone to continue."
            text.contains("reference",true)||text.contains("screen changed",true)->"The screen changed. Read it again before taking that action."
            text.contains("SSL",true)||text.contains("timeout",true)||text.contains("connect",true)||text.contains("HTTP",true)->"The connection didn't finish. Check your internet and try again."
            else->"That request couldn't finish. Check the relevant setting or try a clearer request."
        }
    }
}
