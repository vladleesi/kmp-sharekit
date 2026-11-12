package dev.vladleesi.kmpsharekit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ShareRequestTest {
    private val supported = ShareCapabilities(ShareMethod.NATIVE, true)

    @Test
    fun rejectsEmptyAndInvalidContentWithoutOpeningUi() {
        listOf(
            ShareRequest(),
            ShareRequest(title = "Title"),
            ShareRequest(text = "  "),
            ShareRequest(url = "relative/path"),
            ShareRequest(url = "file:///private.txt"),
            ShareRequest(url = "https://"),
            ShareRequest(url = "https://exa mple.com"),
        ).forEach { assertEquals(ShareFailure.INVALID_REQUEST, it.failure(supported)?.reason) }
    }

    @Test
    fun acceptsTextAndAbsoluteLinks() {
        listOf(
            ShareRequest(text = "Привет 🌍"),
            ShareRequest(url = "https://example.com/path?q=1#part"),
            ShareRequest(text = "Hello", url = "http://example.com", title = "Title"),
        ).forEach { assertNull(it.failure(supported)) }
    }

    @Test
    fun distinguishesUnsupportedTitleFromUnavailableSharing() {
        assertEquals(
            ShareFailure.UNSUPPORTED_CONTENT,
            ShareRequest(text = "Hello", title = "Title").failure(ShareCapabilities(ShareMethod.NATIVE, false))?.reason,
        )
        assertEquals(
            ShareFailure.UNAVAILABLE,
            ShareRequest(text = "Hello").failure(ShareCapabilities(ShareMethod.UNAVAILABLE, true))?.reason,
        )
    }
}
