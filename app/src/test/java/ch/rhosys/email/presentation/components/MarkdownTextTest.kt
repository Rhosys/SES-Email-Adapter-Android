package ch.rhosys.email.presentation.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTextTest {
    @Test
    fun `table based email is detected as html`() {
        assertTrue(looksLikeHtmlDocument("<table>\n    <tr>\n        <td>07:30</td>\n    </tr>\n</table>"))
        assertTrue(looksLikeHtmlDocument("<HTML><BODY>Hi</BODY></HTML>"))
    }

    @Test
    fun `markdown is not detected as html`() {
        assertFalse(looksLikeHtmlDocument("# Hello\n\n**bold** and a [link](https://x.y)"))
        assertFalse(looksLikeHtmlDocument("line one<br>line two"))
    }

    @Test
    fun `style script head and comments are stripped`() {
        val html = "<head><title>T</title><style>.a{color:red}</style></head>" +
            "<!-- hidden --><body>Shown<script>alert(1)</script></body>"
        assertEquals("<body>Shown</body>", stripNonContentHtml(html))
    }
}
