package com.eiliot.app.voice

/** Alternative spellings of the same name, encoded by the bundled KWS bpe.model. */
object WakePronunciations {
    val extras=listOf(
        "▁E LI O T @eliot",
        "▁HE Y ▁E LI O T @hey_eliot",
        "▁E LL I O T T @elliott",
        "▁HE Y ▁E LL I O T T @hey_elliott"
    ).joinToString("\n")

    // The saved comparison clip yielded these name phones. Both Hey prefixes
    // are candidates for a fresh check, since that clip starts with Elliot.
    val phonemeExtras=listOf(
        "y ī l iè @elliot_accent",
        "HH EY1 y ī l iè @hey_elliot_accent",
        "h ēi y ī l iè @hey_elliot_accent"
    ).joinToString("\n")

    fun canLearn(phones:List<String>):Boolean =
        phones.size in 5..18 && phones.first() in setOf("HH","h") &&
            phones.all {it.length in 1..12 && it.all(Char::isLetterOrDigit)}
}
