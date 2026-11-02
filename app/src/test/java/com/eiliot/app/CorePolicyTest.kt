package com.eiliot.app

import com.eiliot.app.core.ContentPolicy
import com.eiliot.app.core.PermissionRequest
import com.eiliot.app.core.ScreenTarget
import com.eiliot.app.data.ContextBudget
import com.eiliot.app.llm.ChatMessage
import com.eiliot.app.ui.BlockKind
import com.eiliot.app.ui.OutputFormat
import org.junit.Assert.*
import org.junit.Test

class CorePolicyTest {
    @Test fun staleMicrophoneCallbacksCannotRestartAfterOffOrErasure() {
        val voice = PermissionRequest(5, needsRunning = true)
        assertTrue(voice.isCurrent(5, running = true, erasing = false))
        assertFalse(voice.isCurrent(6, running = true, erasing = false))
        assertFalse(voice.isCurrent(5, running = false, erasing = false))
        assertFalse(voice.isCurrent(5, running = true, erasing = true))
        val resume = PermissionRequest(5, needsRunning = false)
        assertTrue(resume.isCurrent(5, running = false, erasing = false))
        assertFalse(resume.isCurrent(6, running = false, erasing = false))
        assertFalse(resume.isCurrent(5, running = false, erasing = true))
    }
    @Test fun approvedScreenTargetCannotMoveToAnotherAppOrWindow() {
        val approved = ScreenTarget("org.example.reader", 10)
        approved.requireSame(ScreenTarget("org.example.reader", 10))
        assertThrows(IllegalStateException::class.java) { approved.requireSame(ScreenTarget("org.example.other", 10)) }
        assertThrows(IllegalStateException::class.java) { approved.requireSame(ScreenTarget("org.example.reader", 11)) }
    }
    private val allowed = setOf("com.whatsapp", "com.example.bank")
    @Test fun unknownNotificationAppsAreExcluded() {
        assertFalse(ContentPolicy.notificationAllowed("com.other", "Friend", "Hello", allowed))
        assertFalse(ContentPolicy.notificationAllowed("com.whatsapp", "Friend", "Hello", emptySet()))
    }
    @Test fun ordinaryAllowedMessagePassesLocalPolicy() { assertTrue(ContentPolicy.notificationAllowed("com.whatsapp", "Alex", "Lunch at noon?", allowed)) }
    @Test fun otpContentIsSuppressedBeforeAnyModelDecision() {
        listOf("Your OTP is 129443", "Verification code: 4321", "Your password was reset", "Authentication code sent", "PIN for access", "299031").forEach {
            assertFalse(it, ContentPolicy.notificationAllowed("com.whatsapp", "Message", it, allowed))
        }
    }
    @Test fun sensitiveAppsRemainExcludedEvenWhenAllowlisted() { assertFalse(ContentPolicy.notificationAllowed("com.example.bank", "Update", "Welcome", allowed)) }
    @Test fun screenPolicyExcludesOwnAndProtectedApps() {
        assertFalse(ContentPolicy.screenPackageAllowed("com.eiliot.app", "com.eiliot.app"))
        assertFalse(ContentPolicy.screenPackageAllowed("com.android.systemui", "com.eiliot.app"))
        assertFalse(ContentPolicy.screenPackageAllowed("com.example.passwordmanager", "com.eiliot.app"))
        assertTrue(ContentPolicy.screenPackageAllowed("org.example.reader", "com.eiliot.app"))
    }
    @Test fun wakeMatchingUsesWordBoundaries() {
        listOf("hey elliot", "ELLIOT", "hello elliott").forEach { assertTrue(ContentPolicy.wakeMatch(it)) }
        listOf("heliotrope", "elliotson", "some unrelated speech").forEach { assertFalse(ContentPolicy.wakeMatch(it)) }
    }
    @Test fun speechDoesNotReadRawCodeAndMarkdown() {
        val result = ContentPolicy.speechText("# Result\n```sh\nsecret command\n```\n**Done**")
        assertFalse(result.contains("secret command")); assertFalse(result.contains("**")); assertTrue(result.contains("Code is shown"))
    }
    @Test fun contextKeepsChronologyAndNewestInformationWithinBudget() {
        val h = listOf(ChatMessage("user", "old context"), ChatMessage("assistant", "last answer"), ChatMessage("user", "new query"))
        val selected = ContextBudget.select(h, 16)
        assertTrue(selected.sumOf { it.content.length } <= 16)
        assertEquals("new query", selected.last().content)
        assertEquals("assistant", selected.first().role)
    }
    @Test fun zeroBudgetDoesNotLeakHistory() { assertTrue(ContextBudget.select(listOf(ChatMessage("user", "private")), 0).isEmpty()) }
    @Test fun contextClippingDoesNotSplitSurrogatePairs() {
        val result = ContextBudget.select(listOf(ChatMessage("user", "😀")), 1)
        assertTrue(result.all { it.content.isEmpty() || !it.content.first().isLowSurrogate() })
    }
    @Test fun richOutputKeepsCodeLiteralAndSeparatesHeadingsAndBullets() {
        val b = OutputFormat.blocks("## Findings\n\n- One\n\n```kotlin\nval s = \"<tag>\"\n```\nDone")
        assertEquals(listOf(BlockKind.HEADING, BlockKind.BULLET, BlockKind.CODE, BlockKind.TEXT), b.map { it.kind })
        assertEquals("val s = \"<tag>\"", b[2].text)
    }
    @Test fun incompleteCodeFenceStillPreservesStreamingContent() { assertEquals("println(1)", OutputFormat.blocks("```\nprintln(1)").single().text) }
    @Test fun typewriterNeverSplitsEmoji() {
        val text = "A😀B"; val first = OutputFormat.advance(text, 0, 1); val second = OutputFormat.advance(text, first, 1)
        assertEquals("A", text.take(first)); assertEquals("A😀", text.take(second)); assertEquals(text.length, OutputFormat.advance(text, second, 99))
    }
    @Test fun previewsBoundNewlineHeavyOutputEvenWhenCharacterCountIsSmall() {
        val text = (1..40).joinToString("\n") { "Item $it" }
        val preview = OutputFormat.prefix(text, 1800, 12)
        assertEquals(12, preview.lines().size)
        assertTrue(preview.endsWith("Item 12"))
        assertTrue(preview.length < text.length)
    }
    @Test fun previewClippingPreservesEmojiAndHonorsEmptyBudgets() {
        assertEquals("A", OutputFormat.prefix("A😀B", 2))
        assertEquals("A😀", OutputFormat.prefix("A😀B", 3))
        assertEquals("", OutputFormat.prefix("text", 0))
        assertEquals("", OutputFormat.prefix("text", 10, 0))
    }
    @Test fun previewsKeepAnEntireShortResponse() {
        val text = "## Heading\n\nA short response."
        assertEquals(text, OutputFormat.prefix(text, 1800, 12))
    }
    @Test fun numberedStepsStaySeparateFromSurroundingParagraphs() {
        val blocks = OutputFormat.blocks("Steps\n1. Open Controls\n2) Select a voice\nFinish")
        assertEquals(listOf("Steps", "1. Open Controls", "2) Select a voice", "Finish"), blocks.map { it.text })
    }
}
