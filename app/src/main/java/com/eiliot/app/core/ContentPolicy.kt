package com.eiliot.app.core

object ContentPolicy {
    private val sensitive = Regex("(?i)(\\botp\\b|one.?time|verification|verify.{0,20}code|\\bpin\\b|password|passcode|2fa|authentication code|bank|account balance|medical|patient)")
    private val sensitivePackages = Regex("(?i)(bank|wallet|authenticator|health|hospital|password|payment)")
    fun notificationAllowed(pkg: String, title: String, text: String, allowed: Set<String>): Boolean =
        pkg in allowed && !sensitivePackages.containsMatchIn(pkg) && !sensitive.containsMatchIn("$title $text") &&
            !Regex("(?<!\\d)\\d{4,8}(?!\\d)").containsMatchIn(text)
    fun screenPackageAllowed(pkg: String, own: String): Boolean =
        pkg.isNotBlank() && pkg != own && pkg != "com.android.systemui" && !sensitivePackages.containsMatchIn(pkg)
    fun wakeMatch(text: String): Boolean = Regex("\\b(?:hey\\s+)?(?:eiliot|elliot|elliott|elio)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)
    fun speechText(text: String): String = text.replace(Regex("(?s)```.*?```"), " Code is shown in the chat. ")
        .replace(Regex("[#*`_]+"), "").take(12000)
}
