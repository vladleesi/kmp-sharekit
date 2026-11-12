@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package dev.vladleesi.kmpsharekit

import kotlin.js.JsAny
import kotlin.js.Promise
import kotlin.js.js
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ShareKitTest {
    @Test
    fun newApiHandlesMissingNavigatorAndUnavailableSharingWithoutLaunching() {
        removeNavigator()
        assertEquals(ShareMethod.UNAVAILABLE, ShareKit.capabilities().method)
        assertFalse(ShareKit.canShare(ShareRequest(text = "Hello")))
        var calls = 0
        ShareKit.share(ShareRequest(text = "Hello")) {
            calls++
            assertEquals(ShareFailure.UNAVAILABLE, (it as ShareResult.Failed).reason)
        }
        assertEquals(1, calls)
        assertEquals(0, clipboardCalls())
    }

    @Test
    fun handlesMissingOrRejectingCanShare() {
        configureCanShare("missing")
        assertTrue(ShareKit.canShare(ShareRequest(text = "Hello")))
        configureCanShare("false")
        assertFalse(ShareKit.canShare(ShareRequest(text = "Hello")))
        ShareKit.share(ShareRequest(text = "Hello")) {
            assertEquals(ShareFailure.UNSUPPORTED_CONTENT, (it as ShareResult.Failed).reason)
        }
        configureCanShare("throw")
        assertFalse(ShareKit.canShare(ShareRequest(text = "Hello")))
        assertEquals(0, shareCalls())
    }

    @Test
    fun synchronousPermissionFailureReleasesThePendingRequest() {
        setOutcome("throw", "NotAllowedError")
        var calls = 0
        repeat(2) {
            ShareKit.share(ShareRequest(text = "Hi")) {
                calls++
                assertEquals(ShareFailure.PERMISSION_DENIED, (it as ShareResult.Failed).reason)
                assertEquals("NotAllowedError", (it.cause as BrowserShareException).name)
            }
        }
        assertEquals(2, calls)
        assertEquals(2, shareCalls())
    }

    @Test
    fun richRequestStartsImmediatelyAndReportsCompletion(): Promise<JsAny?> {
        var result: ShareResult? = null
        val request = ShareRequest("Hello", "https://example.com", "Title")
        assertTrue(ShareKit.canShare(request))
        ShareKit.share(request) { result = it }
        assertEquals(1, shareCalls())
        assertEquals("Hello", sharedText())
        assertTrue(hasRichFields())
        assertEquals(null, result)
        return afterMicrotasks { assertEquals(ShareResult.Completed, result) }
    }

    @Test
    fun newApiRejectsConcurrentRequestsAndRecoversAfterAbort(): Promise<JsAny?> {
        setOutcome("pending", "")
        var first: ShareResult? = null
        ShareKit.share(ShareRequest(text = "First")) { first = it }
        ShareKit.share(ShareRequest(text = "Second")) {
            assertEquals(ShareFailure.BUSY, (it as ShareResult.Failed).reason)
        }
        assertEquals(1, shareCalls())
        resolvePendingShare()
        return afterMicrotasks { assertEquals(ShareResult.Completed, first) }
    }

    @Test
    fun newApiPreservesAbortAmbiguity(): Promise<JsAny?> {
        setOutcome("reject", "AbortError")
        var result: ShareResult? = null
        ShareKit.share(ShareRequest(text = "Hi")) { result = it }
        return afterMicrotasks { assertEquals(ShareResult.CancelledOrUnavailable, result) }
    }

    @Test
    fun canShareDoesNotRequireActivationAndPreflightDoesNotSwallowCallbackErrors() {
        setActivation(false)
        assertTrue(ShareKit.canShare(ShareRequest(text = "Hi")))
        var calls = 0
        assertFailsWith<IllegalArgumentException> {
            ShareKit.share(ShareRequest(text = "Hi")) {
                calls++
                assertEquals(ShareFailure.USER_ACTIVATION_REQUIRED, (it as ShareResult.Failed).reason)
                throw IllegalArgumentException("Caller")
            }
        }
        assertEquals(1, calls)
        assertEquals(0, shareCalls())
    }

    @BeforeTest
    fun setUp() = installBrowser()

    @AfterTest
    fun tearDown() = restoreBrowser()

    @Test
    fun detectsSupportWithoutRequiringAGesture() {
        assertTrue(ShareKit.isSupported())
        setActivation(false)
        assertTrue(ShareKit.isSupported())
        assertEquals(0, shareCalls())
    }

    @Test
    fun rejectsUnsupportedBrowsersWithoutCopying() {
        setSupported(false)
        assertFalse(ShareKit.isSupported())
        assertFailsWith<UnsupportedOperationException> { ShareKit.shareText("Text") }
        var failure: Throwable? = null
        ShareKit.shareText("Text") { failure = it.exceptionOrNull() }
        assertIs<UnsupportedOperationException>(failure)
        assertEquals(0, shareCalls())
        assertEquals(0, clipboardCalls())
    }

    @Test
    fun rejectsInsecureContextsEvenWhenShareIsExposed() {
        setSecure(false)
        assertFalse(ShareKit.isSupported())
        assertFailsWith<IllegalStateException> { ShareKit.shareText("Text") }
        var calls = 0
        ShareKit.shareText("Text") {
            calls++
            assertIs<IllegalStateException>(it.exceptionOrNull())
        }
        assertEquals(1, calls)
        assertEquals(0, shareCalls())
    }

    @Test
    fun rejectsCallsWithoutUserActivation() {
        setActivation(false)
        assertFailsWith<IllegalStateException> { ShareKit.shareText("Text") }
        var failure: Throwable? = null
        ShareKit.shareText("Text") { failure = it.exceptionOrNull() }
        assertIs<IllegalStateException>(failure)
        assertEquals(0, shareCalls())
    }

    @Test
    fun handlesMissingNavigator() {
        removeNavigator()
        assertFalse(ShareKit.isSupported())
        assertFailsWith<UnsupportedOperationException> { ShareKit.shareText("Text") }
    }

    @Test
    fun sharesUnchangedTextImmediatelyAndReportsSuccessAsynchronously(): Promise<JsAny?> {
        val texts = listOf("Hello", "", "Line 1\nLine 2 — Привет 世界 🌍", "  whitespace  ")
        var completed = 0
        texts.forEachIndexed { index, text ->
            ShareKit.shareText(text) {
                assertTrue(it.isSuccess)
                completed++
            }
            assertEquals(index + 1, shareCalls())
            assertEquals(text, sharedText())
            assertTrue(hasOnlyText())
        }
        assertEquals(0, completed)
        return afterMicrotasks {
            assertEquals(texts.size, completed)
            assertEquals(0, clipboardCalls())
        }
    }

    @Test
    fun letsOlderBrowsersEnforceActivationWhenUserActivationIsAbsent(): Promise<JsAny?> {
        removeActivation()
        var completed = false
        ShareKit.shareText("Text") { completed = it.isSuccess }
        return afterMicrotasks { assertTrue(completed) }
    }

    @Test
    fun reportsPromiseRejectionsWithOriginalBrowserErrorNames(): Promise<JsAny?> {
        val names = listOf("NotAllowedError", "InvalidStateError", "TypeError", "DataError")
        val failures = mutableListOf<BrowserShareException>()
        names.forEach { name ->
            setOutcome("reject", name)
            ShareKit.shareText("Text") { failures += assertIs<BrowserShareException>(it.exceptionOrNull()) }
        }
        assertTrue(failures.isEmpty())
        return afterMicrotasks {
            assertEquals(names, failures.map { it.name })
            assertTrue(failures.all { it.message == "Mock share failure" })
            assertEquals(0, clipboardCalls())
        }
    }

    @Test
    fun reportsCancellationToTheCallback(): Promise<JsAny?> {
        setOutcome("reject", "AbortError")
        var calls = 0
        ShareKit.shareText("Text") {
            calls++
            assertEquals("AbortError", assertIs<BrowserShareException>(it.exceptionOrNull()).name)
        }
        return afterMicrotasks {
            assertEquals(1, calls)
            assertEquals(0, loggedErrors())
            assertEquals(0, clipboardCalls())
        }
    }

    @Test
    fun commonEntryPointConsumesCancellationWithoutLoggingOrCopying(): Promise<JsAny?> {
        setOutcome("reject", "AbortError")
        ShareKit.shareText("Text")
        return afterMicrotasks {
            assertEquals(0, loggedErrors())
            assertEquals(0, clipboardCalls())
        }
    }

    @Test
    fun commonEntryPointLogsAsyncErrors(): Promise<JsAny?> {
        setOutcome("reject", "DataError")
        ShareKit.shareText("Text")
        return afterMicrotasks {
            assertEquals(1, loggedErrors())
            assertEquals(0, clipboardCalls())
        }
    }

    @Test
    fun normalizesNonErrorPromiseRejections(): Promise<JsAny?> {
        setOutcome("rejectNull", "")
        var failure: BrowserShareException? = null
        ShareKit.shareText("Text") { failure = assertIs<BrowserShareException>(it.exceptionOrNull()) }
        return afterMicrotasks {
            assertEquals("Error", assertNotNull(failure).name)
            assertEquals("Browser sharing failed.", failure.message)
        }
    }

    @Test
    fun reportsSynchronousBrowserExceptionsOnce() {
        setOutcome("throw", "NotAllowedError")
        var calls = 0
        ShareKit.shareText("Text") {
            calls++
            assertEquals("NotAllowedError", assertIs<BrowserShareException>(it.exceptionOrNull()).name)
        }
        assertEquals(1, calls)
    }

    @Test
    fun doesNotTreatCallerExceptionsAsBrowserFailures() {
        setSupported(false)
        var calls = 0
        assertFailsWith<IllegalArgumentException> {
            ShareKit.shareText("Text") {
                calls++
                throw IllegalArgumentException("Caller failure")
            }
        }
        assertEquals(1, calls)
    }

    @Test
    fun doesNotCatchCallerExceptionsWhenTheBrowserThrowsSynchronously() {
        setOutcome("throw", "DataError")
        var calls = 0
        assertFailsWith<IllegalArgumentException> {
            ShareKit.shareText("Text") {
                calls++
                throw IllegalArgumentException("Caller failure")
            }
        }
        assertEquals(1, calls)
    }

    @Test
    fun waitsForTheNativePromiseAndLeavesConcurrentShareDecisionsToTheBrowser(): Promise<JsAny?> {
        setOutcome("pending", "")
        var firstCompleted = false
        ShareKit.shareText("First") { firstCompleted = it.isSuccess }
        assertFalse(firstCompleted)
        setOutcome("reject", "InvalidStateError")
        var secondFailure: BrowserShareException? = null
        ShareKit.shareText("Second") { secondFailure = assertIs<BrowserShareException>(it.exceptionOrNull()) }
        resolvePendingShare()
        return afterMicrotasks {
            assertTrue(firstCompleted)
            assertEquals("InvalidStateError", assertNotNull(secondFailure).name)
            assertEquals(2, shareCalls())
        }
    }
}

// Exercise the real browser bridge on both backends. Restore every changed property after each test.
private fun installBrowser(): Unit =
    js(
        """{
        const nav = globalThis.navigator;
        const state = { calls: 0, copies: 0, logs: 0, mode: 'resolve', name: 'DataError', saved: [] };
        globalThis.__shareKitTest = state;
        const replace = (object, key, value) => {
            state.saved.push([object, key, Object.getOwnPropertyDescriptor(object, key)]);
            Object.defineProperty(object, key, { configurable: true, writable: true, value: value });
        };
        replace(globalThis, 'navigator', nav);
        replace(globalThis, 'isSecureContext', true);
        replace(nav, 'userActivation', { isActive: true });
        replace(nav, 'clipboard', { writeText: () => { state.copies++; return Promise.resolve(); } });
        // Kotlin's runner restores console methods as soon as an async test returns its Promise.
        const testConsole = Object.create(console);
        testConsole.error = () => { state.logs++; };
        replace(globalThis, 'console', testConsole);
        state.share = function(data) {
            if (this !== nav) throw new Error('Incorrect navigator receiver');
            state.calls++;
            state.data = data;
            const error = new DOMException('Mock share failure', state.name);
            if (state.mode === 'throw') throw error;
            if (state.mode === 'reject') return Promise.reject(error);
            if (state.mode === 'rejectNull') return Promise.reject(null);
            if (state.mode === 'pending') return new Promise(resolve => { state.resolve = resolve; });
            return Promise.resolve();
        };
        replace(nav, 'share', state.share);
        replace(nav, 'canShare', () => true);
        }""",
    )

private fun restoreBrowser(): Unit =
    js(
        """{
        for (const [object, key, descriptor] of globalThis.__shareKitTest.saved.reverse()) {
            if (descriptor) Object.defineProperty(object, key, descriptor);
            else delete object[key];
        }
        delete globalThis.__shareKitTest;
        }""",
    )

private fun setSupported(supported: Boolean): Unit = js("{ navigator.share = supported ? globalThis.__shareKitTest.share : undefined; }")

private fun setSecure(secure: Boolean): Unit = js("{ globalThis.isSecureContext = secure; }")

private fun setActivation(active: Boolean): Unit = js("{ navigator.userActivation.isActive = active; }")

private fun removeActivation(): Unit = js("{ navigator.userActivation = undefined; }")

private fun removeNavigator(): Unit = js("{ globalThis.navigator = undefined; }")

private fun setOutcome(
    mode: String,
    name: String,
): Unit = js("{ globalThis.__shareKitTest.mode = mode; globalThis.__shareKitTest.name = name; }")

private fun resolvePendingShare(): Unit = js("globalThis.__shareKitTest.resolve()")

private fun shareCalls(): Int = js("globalThis.__shareKitTest.calls")

private fun clipboardCalls(): Int = js("globalThis.__shareKitTest.copies")

private fun loggedErrors(): Int = js("globalThis.__shareKitTest.logs")

private fun sharedText(): String = js("globalThis.__shareKitTest.data.text")

private fun hasOnlyText(): Boolean = js("Object.keys(globalThis.__shareKitTest.data).join() === 'text'")

private fun afterMicrotasks(assertions: () -> Unit): Promise<JsAny?> = js("Promise.resolve().then(() => { assertions(); return null; })")

private fun hasRichFields(): Boolean =
    js("globalThis.__shareKitTest.data.url === 'https://example.com' && globalThis.__shareKitTest.data.title === 'Title'")

private fun configureCanShare(mode: String): Unit =
    js(
        """{
    navigator.canShare = mode === 'missing' ? undefined : () => {
        if (mode === 'throw') throw new Error('Unavailable');
        return false;
    };
}""",
    )
