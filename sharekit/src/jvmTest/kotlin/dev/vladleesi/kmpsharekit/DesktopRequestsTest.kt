package dev.vladleesi.kmpsharekit

import java.awt.EventQueue
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DesktopRequestsTest {
    @Test
    fun emailOnlyDesktopReportsFileAttachmentsAsUnsupported() {
        val sharing =
            DesktopRequests(
                mac = false,
                windows = false,
                email = DesktopSharing(canMail = { true }, mail = { error("Must not open email for files") }),
                nativeAvailable = { error("Must not load a native bridge for files") },
            )
        val request = ShareFilesRequest(listOf(ShareFile("report.pdf", "application/pdf", byteArrayOf(1))))
        assertFalse(sharing.canShareFiles(request))
        sharing.shareFiles(request) { assertEquals(ShareResult.Failed(ShareFailure.UNSUPPORTED_CONTENT), it) }
    }

    @Test
    fun stagedFilesPreserveBytesWithDuplicateNamesAndCanBeRemoved() {
        val first = byteArrayOf(0, -1)
        val staged =
            StagedShareFiles(listOf(ShareFile("photo 🌍.png", "image/png", first), ShareFile("photo 🌍.png", "image/png", byteArrayOf())))
        val files = staged.paths.map(::File)
        try {
            assertEquals(listOf("photo 🌍.png", "photo 🌍.png"), files.map { it.name })
            assertEquals(2, files.distinct().size)
            assertContentEquals(first, files.first().readBytes())
            assertEquals(0L, files.last().length())
        } finally {
            staged.remove()
        }
        assertFalse(files.any { it.exists() })
    }

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
                windows = false,
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
        if (System.getProperty("os.name").startsWith("Windows")) {
            assertTrue(WindowsSharingBridge.loaded, "Windows DLL must load from java.library.path")
        }
        assertFalse(ShareKit.isSupported())
    }

    @Test
    fun windowsDefaultsToNativeAndNeverFallsBackToEmail() {
        listOf(true, false).forEach { available ->
            var nativeCalls = 0
            var emailCalls = 0
            val request = ShareRequest("Hello 🌍", "https://example.com", "Title")
            val sharing =
                DesktopRequests(
                    mac = false,
                    windows = true,
                    email = DesktopSharing(canMail = { true }, mail = { emailCalls++ }),
                    nativeAvailable = { available },
                    nativeShare = { payload, callback ->
                        assertEquals(request, payload)
                        nativeCalls++
                        callback(ShareResult.Launched)
                    },
                )
            assertEquals(if (available) ShareMethod.NATIVE else ShareMethod.UNAVAILABLE, sharing.capabilities().method)
            assertTrue(sharing.capabilities().supportsTitle)
            sharing.share(request) {
                assertEquals(if (available) ShareResult.Launched else ShareResult.Failed(ShareFailure.UNAVAILABLE), it)
            }
            assertEquals(if (available) 1 else 0, nativeCalls)
            assertEquals(0, emailCalls)
            sharing.share(request, ShareMethod.EMAIL) { assertEquals(ShareResult.Launched, it) }
            assertEquals(1, emailCalls)
        }
    }

    @Test
    fun windowsBridgeMapsLaunchAndFailuresWithoutInventingCancellation() {
        mapOf(
            0 to ShareResult.Launched,
            2 to ShareResult.Failed(ShareFailure.HOST_UNAVAILABLE),
            3 to ShareResult.Failed(ShareFailure.BUSY),
            5 to ShareResult.Failed(ShareFailure.PERMISSION_DENIED),
            6 to ShareResult.Failed(ShareFailure.UNAVAILABLE),
            99 to ShareResult.Failed(ShareFailure.PLATFORM_ERROR),
        ).forEach { (status, expected) -> assertEquals(expected, windowsSharingResult(status)) }
    }
}
