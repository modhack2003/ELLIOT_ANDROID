package com.eiliot.app.voice

/** Alternative spellings of the same name, encoded by the bundled KWS bpe.model. */
object WakePronunciations {
    val extras=listOf(
        "▁E LI O T @eliot",
        "▁HE Y ▁E LI O T @hey_eliot",
        "▁E LL I O T T @elliott",
        "▁HE Y ▁E LL I O T T @hey_elliott"
    ).joinToString("\n")
}
