package com.mroldl001.m3l

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {
    @Test
    fun tableWidthExcludesAllSupportedFormulaDelimiters() {
        listOf("\$x+y\$", "\$\$x+y\$\$", "\\[x+y\\]", "\\(x+y\\)").forEach { formula ->
            assertEquals("Formula: $formula", "Before  after", stripLatexForTableWidth("Before $formula after"))
        }
    }

    @Test
    fun fencedCodePreservesMathAndQuoteCharacters() {
        val code = "> literal\nval price = \"\$5\""
        assertEquals(
            listOf(ContentSegment(ContentType.CODE_BLOCK, code, "kotlin")),
            splitContent("```kotlin\n$code\n```")
        )
    }

    @Test
    fun unfinishedFenceCanRenderDuringStreaming() {
        assertEquals(
            listOf(ContentSegment(ContentType.CODE_BLOCK, "println(1)", "kotlin")),
            splitContent("```kotlin\nprintln(1)", allowUnclosedCodeFence = true)
        )
    }

    @Test
    fun tableKeepsInlineMathInsideItsCells() {
        val table = "| Value |\n| --- |\n| \$x^2\$ |"
        assertEquals(listOf(ContentSegment(ContentType.TABLE, table)), splitContent(table))
    }

    @Test
    fun parsesDisplayAndInlineMath() {
        assertEquals(
            listOf(ContentSegment(ContentType.LATEX_BLOCK, "x^2")),
            splitContent("\$\$\nx^2\n\$\$")
        )
        assertEquals(
            listOf(ContentSegment(ContentType.LATEX_INLINE, "Value: \\(x^2\\)")),
            splitContent("Value: \\(x^2\\)")
        )
    }

    @Test
    fun streamingHidesIncompleteMathAndTableThenReleasesCompletedText() {
        assertEquals("Value: ", stabilizeStreamingMarkdown("Value: \$x"))
        val complete = "Value: \$x\$"
        assertEquals(complete, stabilizeStreamingMarkdown(complete))
        assertEquals("", stabilizeStreamingMarkdown("| A |\n| --- |\n| x"))
        val finishedTable = "| A |\n| --- |\n| x |\n\nDone"
        assertEquals(finishedTable, stabilizeStreamingMarkdown(finishedTable))
    }

    @Test
    fun emptyTextAndHtmlQuoteRemainSupported() {
        assertTrue(splitContent("").isEmpty())
        val segments = splitContent(normalizeHtmlToMarkdown("<blockquote><p>Hello</p></blockquote>"))
        assertEquals(ContentType.QUOTE, segments.single().type)
        assertEquals("Hello", segments.single().content.trim())
    }
}
