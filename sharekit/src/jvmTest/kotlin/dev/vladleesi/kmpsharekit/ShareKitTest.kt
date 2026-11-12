package dev.vladleesi.kmpsharekit

import java.awt.HeadlessException
import java.io.IOException
import java.net.URI
import java.net.URLDecoder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ShareKitTest {
    @Test
    fun opensAMailDraftWithTheOriginalTextAsItsOnlyField() {
        var opened: URI? = null
        val sharing = DesktopSharing(canMail = { true }, mail = { opened = it })

        assertTrue(sharing.isSupported())
        listOf("Hello", "", "Line 1\r\nLine 2\nПривет 世界 🌍", "a+b &cc=other@example.com?subject=#100% / ").forEach { text ->
            sharing.shareText(text)
            val uri = checkNotNull(opened)
            assertEquals("mailto", uri.scheme)
            assertEquals(null, uri.rawFragment)
            val fields = uri.rawSchemeSpecificPart.removePrefix("?").split('&')
            assertEquals(1, fields.size)
            assertTrue(fields.single().startsWith("body="))
            val body = fields.single().removePrefix("body=")
            assertFalse(body.contains('+'))
            assertEquals(text, URLDecoder.decode(body, "UTF-8"))
        }
        sharing.shareText("a b+c")
        assertEquals("mailto:?body=a%20b%2Bc", opened.toString())
    }

    @Test
    fun unsupportedEnvironmentsNeverLaunchAMailClient() {
        val probes: List<() -> Boolean> =
            listOf(
                { false },
                { throw HeadlessException() },
                { throw UnsupportedOperationException() },
                { throw SecurityException() },
            )
        probes.forEach { probe ->
            var launched = false
            val sharing = DesktopSharing(canMail = probe, mail = { launched = true })
            assertFalse(sharing.isSupported())
            assertFailsWith<UnsupportedOperationException> { sharing.shareText("Hello") }
            assertFalse(launched)
        }
    }

    @Test
    fun checksCapabilityForEachShare() {
        var supported = true
        var launches = 0
        val sharing = DesktopSharing(canMail = { supported }, mail = { launches++ })
        assertTrue(sharing.isSupported())
        supported = false
        assertFailsWith<UnsupportedOperationException> { sharing.shareText("Hello") }
        assertEquals(0, launches)
    }

    @Test
    fun mailClientFailuresRetainTheirCause() {
        listOf(IOException("No mail client"), SecurityException("Denied")).forEach { cause ->
            val sharing = DesktopSharing(canMail = { true }, mail = { throw cause })
            val error = assertFailsWith<IllegalStateException> { sharing.shareText("Hello") }
            assertSame(cause, error.cause)
        }
    }

    @Test
    fun commonEntryPointIsSafeInHeadlessEnvironments() {
        // The JVM test task explicitly runs headless and must not launch a real mail client.
        assertFalse(ShareKit.isSupported())
        assertFailsWith<UnsupportedOperationException> { ShareKit.shareText("Hello") }
    }
}
