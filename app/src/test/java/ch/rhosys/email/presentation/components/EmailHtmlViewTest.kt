package ch.rhosys.email.presentation.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailHtmlViewTest {
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
    fun `csp and viewport are inserted after the doctype`() {
        val doc = prepareEmailDocument("<!DOCTYPE html>\n<html><body>Hi</body></html>")
        assertTrue(doc.startsWith("<!DOCTYPE html><meta http-equiv=\"Content-Security-Policy\" content=\"$EMAIL_CSP\">"))
        assertTrue(doc.contains("name=\"viewport\""))
        assertTrue(doc.endsWith("\n<html><body>Hi</body></html>"))
    }

    @Test
    fun `csp is prepended when there is no doctype`() {
        val doc = prepareEmailDocument("<div>Hi</div>")
        assertTrue(doc.startsWith("<meta http-equiv=\"Content-Security-Policy\""))
        assertTrue(doc.endsWith("<div>Hi</div>"))
    }

    @Test
    fun `csp only allows images and inline styles`() {
        assertEquals(
            "default-src 'none'; img-src https: data:; style-src 'unsafe-inline'; form-action 'none'; base-uri 'none'",
            EMAIL_CSP,
        )
    }
}
