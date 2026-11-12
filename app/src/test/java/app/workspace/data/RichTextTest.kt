package app.workspace.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RichTextTest {
    private val bold = listOf(Span(0, 3, "b"))

    @Test
    fun typingAfterARunContinuesIt() {
        assertEquals(listOf(Span(0, 4, "b")), RichText.adjust(bold, "abc", "abcd"))
    }

    @Test
    fun typingBeforeARunDoesNotJoinIt() {
        assertEquals(listOf(Span(1, 4, "b")), RichText.adjust(bold, "abc", "xabc"))
    }

    @Test
    fun deletingInsideShrinksTheRun() {
        assertEquals(listOf(Span(0, 2, "b")), RichText.adjust(bold, "abc", "ac"))
    }

    @Test
    fun deletingEverythingDropsTheRun() {
        assertEquals(emptyList<Span>(), RichText.adjust(bold, "abc", ""))
    }

    @Test
    fun linksDoNotGrowWhenTypingAfterThem() {
        val link = listOf(Span(0, 3, "l:x"))
        assertEquals(link, RichText.adjust(link, "abc", "abcd"))
    }

    @Test
    fun toggleOnThenOff() {
        val on = RichText.toggle(emptyList(), 0, 3, "b")
        assertEquals(listOf(Span(0, 3, "b")), on)
        assertEquals(emptyList<Span>(), RichText.toggle(on, 0, 3, "b"))
    }

    @Test
    fun unboldingTheMiddleSplitsTheRun() {
        assertEquals(listOf(Span(0, 1, "b"), Span(2, 3, "b")), RichText.toggle(bold, 1, 2, "b"))
    }

    @Test
    fun aStretchHasOneColour() {
        val red = RichText.add(emptyList(), 0, 4, "c:red")
        val mixed = RichText.add(red, 1, 3, "c:blue")
        assertEquals(listOf(Span(0, 1, "c:red"), Span(1, 3, "c:blue"), Span(3, 4, "c:red")), mixed)
    }

    @Test
    fun sliceMovesSpansToTheStartOfTheSlice() {
        assertEquals(listOf(Span(0, 2, "b")), RichText.slice(listOf(Span(2, 5, "b")), 2, 4))
    }
}
