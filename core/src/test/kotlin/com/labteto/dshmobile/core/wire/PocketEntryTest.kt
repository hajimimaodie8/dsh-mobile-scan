package com.labteto.dshmobile.core.wire

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The second kind of code the scanner sees.
 *
 * Everything here is about *not* over-reading a QR. The scanner is pointed at whatever is in front
 * of it, so the parser's job is to accept the one shape an entry has — an origin, with or without a
 * token — and to refuse everything else rather than treating a web page as a host to connect to.
 */
class PocketEntryTest {

    /** What the dsh-pocket panel shows on a LAN: a bare origin, because that proxy asks for no pin. */
    @Test
    fun `a bare pocket origin parses without a token`() {
        val entry = valid("http://192.168.1.6:3081")
        assertEquals("http://192.168.1.6:3081", entry.baseUrl)
        assertEquals("192.168.1.6", entry.host)
        assertEquals(3081, entry.port)
        assertFalse(entry.useTls)
        assertNull(entry.token)
        assertFalse(entry.isLoopback)
    }

    @Test
    fun `a trailing slash is still the same origin`() {
        assertEquals("http://192.168.1.6:3081", valid("http://192.168.1.6:3081/").baseUrl)
    }

    /** A code that does name a token keeps it; the exchange presents it instead of nothing. */
    @Test
    fun `a token on the code is carried through`() {
        val entry = valid("http://192.168.1.6:3081/?token=69297029")
        assertEquals("69297029", entry.token)
    }

    /** The public tunnel is https on the default port, and is reached exactly the same way. */
    @Test
    fun `an https code parses as tls on the default port`() {
        val entry = valid("https://dsh-example.trycloudflare.com")
        assertTrue(entry.useTls)
        assertEquals(443, entry.port)
        assertEquals("dsh-example.trycloudflare.com:443", entry.authority)
    }

    @Test
    fun `a missing port is the scheme's default`() {
        assertEquals(80, valid("http://192.168.1.6").port)
    }

    @Test
    fun `loopback is recognised, so a tunnelled endpoint is not mistaken for one`() {
        assertTrue(valid("http://127.0.0.1:3080").isLoopback)
        assertTrue(valid("http://localhost:3080").isLoopback)
    }

    @Test
    fun `leading and trailing whitespace is tolerated`() {
        assertEquals("192.168.1.6:3081", valid("  http://192.168.1.6:3081\n").authority)
    }

    /** A URL with a path is somebody's page. Connecting to it would turn "wrong code" into a host. */
    @Test
    fun `a url with a path is not an entry`() {
        assertNotAnEntry("http://example.com/some/page")
        assertNotAnEntry("https://example.com/index.html?token=abc")
    }

    @Test
    fun `text that is not a url is not an entry`() {
        assertNotAnEntry("hello world")
        assertNotAnEntry("192.168.1.6:3081")
        assertNotAnEntry("")
        assertNotAnEntry("   ")
    }

    /** The relay payload is handled before this parser ever sees it; the shape is refused anyway. */
    @Test
    fun `a relay payload is not an entry`() {
        assertNotAnEntry("""{"v":1,"kind":"dsh-relay-pair","url":"http://192.168.1.6:3455","code":"12345678","expiresAt":1}""")
    }

    @Test
    fun `a blank token is treated as absent`() {
        assertNull(valid("http://192.168.1.6:3081/?token=").token)
        assertNull(valid("http://192.168.1.6:3081/?token=%20").token)
    }

    private fun valid(text: String): PocketEntry =
        (PocketEntryParser.parse(text) as PocketEntryResult.Valid).entry

    private fun assertNotAnEntry(text: String) {
        assertEquals(PocketEntryResult.NotAnEntry, PocketEntryParser.parse(text))
    }
}
