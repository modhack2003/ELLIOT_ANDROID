package com.eiliot.app

import com.eiliot.app.learning.FeedParser
import org.junit.Assert.*
import org.junit.Test

class FeedParserTest {
    @Test fun parsesTheCurrentAndroidAtomFormat() {
        val xml = "<feed xmlns=\"http://www.w3.org/2005/Atom\"><entry><title>Android update</title><link rel=\"alternate\" href=\"https://developer.android.com/blog/posts/example\"/><content type=\"html\">&lt;p&gt;Useful information&lt;/p&gt;</content></entry></feed>"
        val item = FeedParser.androidEntries(xml).single()
        assertEquals("Android update", item.title); assertTrue(item.summary.contains("Useful information")); assertFalse(item.summary.contains("<p>"))
    }
    @Test fun rssCompatibilityDoesNotAcceptForeignLinks() {
        val xml = "<rss><channel><item><title>Allowed</title><link>https://android-developers.googleblog.com/post</link><description>Summary</description></item><item><title>Foreign</title><link>https://example.net/</link></item></channel></rss>"
        assertEquals(listOf("Allowed"), FeedParser.androidEntries(xml).map { it.title })
    }
    @Test(expected = IllegalArgumentException::class) fun refusesExternalEntityDeclarations() {
        FeedParser.androidEntries("<!DOCTYPE feed [<!ENTITY x SYSTEM 'file:///private'>]><feed>&x;</feed>")
    }
}
