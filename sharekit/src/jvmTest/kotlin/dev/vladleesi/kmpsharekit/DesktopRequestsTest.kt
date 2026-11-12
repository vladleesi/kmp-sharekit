package dev.vladleesi.kmpsharekit

import java.awt.EventQueue
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DesktopRequestsTest {
    @Test
    fun macDefaultsToNativeAndEmailRemainsExplicit() {
        var nativeCalls = 0
        var mailCalls = 0
        val request = ShareRequest("Hello 🌍", "https://example.com", "Title")
        val sharing =
            DesktopRequests(
                mac = true,
                email = DesktopSharing(canMail = { true }, mail = { mailCalls++ }),
                nativeAvailable = { true },
                nativeShare = { payload, callback ->
                    assertEquals(request, payload)
                    nativeCalls++
                    callback(ShareResult.Launched)
                },
            )
        assertEquals(ShareMethod.NATIVE, sharing.capabilities().method)
        assertEquals(ShareMethod.EMAIL, sharing.capabilities(ShareMethod.EMAIL).method)
        sharing.share(request) { assertEquals(ShareResult.Launched, it) }
        assertEquals(1, nativeCalls)
        assertEquals(0, mailCalls)
        sharing.share(request, ShareMethod.EMAIL) { assertEquals(ShareResult.Launched, it) }
        assertEquals(1, nativeCalls)
        assertEquals(1, mailCalls)
    }

    @Test
    fun missingBridgeNeverFallsBackToMail() {
        var mailCalls = 0
        val sharing =
            DesktopRequests(
                mac = true,
                email = DesktopSharing(canMail = { true }, mail = { mailCalls++ }),
                nativeAvailable = { false },
                nativeShare = { _, _ -> error("Unavailable bridge must not launch") },
            )
        assertEquals(ShareMethod.UNAVAILABLE, sharing.capabilities().method)
        sharing.share(ShareRequest("Hello")) { assertEquals(ShareResult.Failed(ShareFailure.UNAVAILABLE), it) }
        assertEquals(0, mailCalls)
    }

    @Test
    fun otherDesktopsUseEmailWithoutProbingNativeCode() {
        val sharing =
            DesktopRequests(
                mac = false,
                email = DesktopSharing(canMail = { true }, mail = {}),
                nativeAvailable = { error("Must not load macOS code") },
            )
        assertEquals(ShareMethod.EMAIL, sharing.capabilities().method)
        assertEquals(ShareMethod.UNAVAILABLE, sharing.capabilities(ShareMethod.NATIVE).method)
        sharing.share(ShareRequest("Hello")) { assertEquals(ShareResult.Launched, it) }
    }

    @Test
    fun invalidContentNeverAcquiresTheNativeSession() {
        val sharing = DesktopRequests(mac = true, nativeAvailable = { true }, nativeShare = { _, _ -> error("Invalid") })
        listOf(ShareRequest(), ShareRequest(title = "Only title"), ShareRequest(url = "file:///tmp/a")).forEach { request ->
            sharing.share(request) { assertEquals(ShareResult.Failed(ShareFailure.INVALID_REQUEST), it) }
        }
    }

    @Test
    fun pendingRequestsAreRejectedAndEveryTerminalResultReleasesTheSession() {
        listOf(
            ShareResult.Launched,
            ShareResult.Cancelled,
            ShareResult.Failed(ShareFailure.HOST_UNAVAILABLE),
            ShareResult.Failed(ShareFailure.PLATFORM_ERROR),
        ).forEach { terminal ->
            lateinit var complete: (ShareResult) -> Unit
            var calls = 0
            val sharing = DesktopRequests(mac = true, nativeAvailable = { true }, nativeShare = { _, callback -> complete = callback })
            sharing.share(ShareRequest("First")) {
                calls++
                assertEquals(terminal, it)
            }
            sharing.share(ShareRequest("Second")) { assertEquals(ShareResult.Failed(ShareFailure.BUSY), it) }
            complete(terminal)
            complete(terminal)
            assertEquals(1, calls)
            sharing.share(ShareRequest("Third")) { calls++ }
            complete(ShareResult.Launched)
            assertEquals(2, calls)
        }
    }

    @Test
    fun callbackCanStartAnotherShareAndExceptionsAreNotReportedTwice() {
        val sharing =
            DesktopRequests(mac = true, nativeAvailable = { true }, nativeShare = { _, callback -> callback(ShareResult.Launched) })
        var calls = 0
        sharing.share(ShareRequest("First")) {
            sharing.share(ShareRequest("Second")) {
                assertEquals(ShareResult.Launched, it)
                calls++
            }
        }
        val error = IllegalArgumentException("Application error")
        assertSame(
            error,
            assertFailsWith<IllegalArgumentException> {
                sharing.share(ShareRequest("Third")) {
                    calls++
                    throw error
                }
            },
        )
        assertEquals(2, calls)
    }

    @Test
    fun bridgeLaunchFailuresPreserveCauseAndReleaseBusyState() {
        listOf(IOException("Bridge"), UnsatisfiedLinkError("Missing JNI symbol")).forEach { error ->
            val sharing = DesktopRequests(mac = true, nativeAvailable = { true }, nativeShare = { _, _ -> throw error })
            repeat(2) {
                sharing.share(ShareRequest("Hello")) {
                    assertTrue(it is ShareResult.Failed)
                    assertSame(error, it.cause)
                    assertEquals(if (error is LinkageError) ShareFailure.UNAVAILABLE else ShareFailure.PLATFORM_ERROR, it.reason)
                }
            }
        }
    }

    @Test
    fun nativeAdapterMapsResultsOnTheAwtEventThread() {
        val expected =
            listOf(
                ShareResult.Launched,
                ShareResult.Cancelled,
                ShareResult.Failed(ShareFailure.HOST_UNAVAILABLE),
                ShareResult.Failed(ShareFailure.BUSY),
            )
        expected.forEachIndexed { status, result ->
            val completed = CountDownLatch(1)
            var actual: ShareResult? = null
            var eventThread = false
            MacSharingCallback {
                actual = it
                eventThread = EventQueue.isDispatchThread()
                completed.countDown()
            }.complete(status, null)
            assertTrue(completed.await(5, TimeUnit.SECONDS))
            assertEquals(result, actual)
            assertTrue(eventThread)
        }
    }

    @Test
    fun packagedBridgeLoadsEvenWhenTestsAreHeadless() {
        if (System.getProperty("os.name").startsWith("Mac")) {
            assertTrue(MacSharingBridge.loaded, "Native library must load from java.library.path")
        }
        assertFalse(ShareKit.isSupported())
    }
}
