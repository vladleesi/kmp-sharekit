@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package dev.vladleesi.kmpsharekit

import kotlin.io.encoding.Base64
import kotlin.js.JsAny
import kotlin.js.js

public actual object ShareKit {
    private var sharing = false

    public actual fun capabilities(): ShareCapabilities =
        ShareCapabilities(if (isSupported()) ShareMethod.NATIVE else ShareMethod.UNAVAILABLE, true)

    public actual fun canShare(request: ShareRequest): Boolean =
        request.failure(capabilities()) == null && canShareInBrowser(browserData(request.text, request.url, request.title), false)

    public actual fun share(
        request: ShareRequest,
        onResult: (ShareResult) -> Unit,
    ) {
        share(request.failure(capabilities()), false, onResult) { browserData(request.text, request.url, request.title) }
    }

    public actual fun canShareFiles(request: ShareFilesRequest): Boolean =
        request.failure(capabilities()) == null &&
            supportsBrowserFiles() &&
            canShareInBrowser(request.browserData(), true)

    public actual fun shareFiles(
        request: ShareFilesRequest,
        onResult: (ShareResult) -> Unit,
    ) {
        share(request.failure(capabilities()), true, onResult) { request.browserData() }
    }

    private fun share(
        invalid: ShareResult.Failed?,
        hasFiles: Boolean,
        onResult: (ShareResult) -> Unit,
        createData: () -> JsAny,
    ) {
        val failure =
            invalid ?: when {
                sharing -> ShareResult.Failed(ShareFailure.BUSY)
                !hasUserActivation() -> ShareResult.Failed(ShareFailure.USER_ACTIVATION_REQUIRED)
                hasFiles && !supportsBrowserFiles() -> ShareResult.Failed(ShareFailure.UNSUPPORTED_CONTENT)
                else -> null
            }
        if (failure != null) {
            onResult(failure)
            return
        }
        val data =
            try {
                createData()
            } catch (error: Exception) {
                onResult(ShareResult.Failed(ShareFailure.PLATFORM_ERROR, error))
                return
            }
        if (!canShareInBrowser(data, hasFiles)) {
            onResult(ShareResult.Failed(ShareFailure.UNSUPPORTED_CONTENT))
            return
        }
        sharing = true
        var finished = false
        val finish: (ShareResult) -> Unit = { result ->
            if (!finished) {
                finished = true
                sharing = false
                onResult(result)
            }
        }
        shareRequestInBrowser(
            data,
            { finish(ShareResult.Completed) },
            { name, message ->
                finish(
                    if (name == "AbortError") {
                        ShareResult.CancelledOrUnavailable
                    } else {
                        ShareResult.Failed(
                            when (name) {
                                "NotAllowedError" -> ShareFailure.PERMISSION_DENIED
                                "InvalidStateError" -> ShareFailure.BUSY
                                "TypeError" -> ShareFailure.INVALID_REQUEST
                                else -> ShareFailure.PLATFORM_ERROR
                            },
                            BrowserShareException(name, message),
                        )
                    },
                )
            },
        )
    }

    /** Checks secure-context and API availability, without requiring or consuming a user gesture. */
    public actual fun isSupported(): Boolean = isSecureContext() && hasWebShare()

    public actual fun shareText(text: String) {
        checkAvailability()
        shareInBrowser(text, {}, { name, message ->
            if (name != "AbortError") logShareError(name, message)
        })
    }

    /**
     * Starts sharing immediately and reports the browser outcome to [onResult] exactly once.
     * Call directly from a click/tap handler; do not defer the call to a coroutine or timer.
     *
     * Preflight failures are reported synchronously; Web Share results arrive asynchronously.
     * Browser failures use [BrowserShareException], including `AbortError` for cancellation
     * or no available share targets. Success means the browser resolved its Promise, which
     * does not guarantee delivery to a recipient. No clipboard fallback is performed.
     */
    public fun shareText(
        text: String,
        onResult: (Result<Unit>) -> Unit,
    ) {
        val failure = runCatching { checkAvailability() }.exceptionOrNull()
        if (failure != null) {
            onResult(Result.failure(failure))
            return
        }
        shareInBrowser(
            text,
            { onResult(Result.success(Unit)) },
            { name, message -> onResult(Result.failure(BrowserShareException(name, message))) },
        )
    }

    private fun checkAvailability() {
        check(isSecureContext()) { "Browser sharing requires a secure context (HTTPS or localhost)." }
        if (!hasWebShare()) {
            throw UnsupportedOperationException("This browser does not support the Web Share API.")
        }
        check(hasUserActivation()) { "Call ShareKit.shareText directly from a click or tap handler." }
    }
}

/** A Web Share failure with the original browser error [name], such as `AbortError` or `NotAllowedError`. */
public class BrowserShareException(
    public val name: String,
    message: String,
) : IllegalStateException(message)

private fun isSecureContext(): Boolean = js("globalThis.isSecureContext === true")

private fun hasWebShare(): Boolean = js("typeof navigator !== 'undefined' && typeof navigator.share === 'function'")

// Older Web Share implementations may not expose userActivation. Let share() enforce it there.
private fun hasUserActivation(): Boolean = js("!navigator.userActivation || navigator.userActivation.isActive")

private fun shareInBrowser(
    text: String,
    onSuccess: () -> Unit,
    onError: (String, String) -> Unit,
): Unit =
    js(
        """{
        const failure = (error) => onError(
            error && typeof error.name === 'string' ? error.name : 'Error',
            error && typeof error.message === 'string' ? error.message : 'Browser sharing failed.'
        );
        let promise;
        try {
            promise = navigator.share({ text: text });
        } catch (error) {
            failure(error);
            return;
        }
        // Attach both handlers immediately. Do not catch errors thrown by the caller's callback.
        promise.then(() => onSuccess(), failure);
        }""",
    )

private fun logShareError(
    name: String,
    message: String,
): Unit = js("console.error('ShareKit: ' + name + ': ' + message)")

internal actual fun validShareUrl(url: String): Boolean =
    js(
        """{
    try {
        if (/\s/.test(url)) return false;
        const parsed = new URL(url);
        return /^https?:\/\//i.test(url) && (parsed.protocol === 'http:' || parsed.protocol === 'https:') && !!parsed.hostname;
    } catch (_) { return false; }
}""",
    )

private fun browserData(
    text: String?,
    url: String?,
    title: String?,
): JsAny =
    js(
        """{
    const data = {};
    if (text !== null) data.text = text;
    if (url !== null) data.url = url;
    if (title !== null) data.title = title;
    return data;
}""",
    )

private fun ShareFilesRequest.browserData(): JsAny =
    browserData(text, null, title).also { data ->
        files.forEach { file -> addBrowserFile(data, file.name, file.mimeType, Base64.encode(file.bytes)) }
    }

private fun addBrowserFile(
    data: JsAny,
    name: String,
    mimeType: String,
    encoded: String,
): Unit =
    js(
        """{
    const decoded = atob(encoded);
    const bytes = Uint8Array.from(decoded, character => character.charCodeAt(0));
    if (!data.files) data.files = [];
    data.files.push(new File([bytes], name, { type: mimeType }));
}""",
    )

private fun supportsBrowserFiles(): Boolean = js("typeof File === 'function' && typeof navigator.canShare === 'function'")

private fun canShareInBrowser(
    data: JsAny,
    hasFiles: Boolean,
): Boolean =
    js(
        """{
    try {
        if (hasFiles) {
            if (!navigator.canShare({ files: data.files })) return false;
            if (data.text === undefined && data.title === undefined) return true;
        }
        return typeof navigator.canShare !== 'function' || navigator.canShare(data);
    } catch (_) { return false; }
}""",
    )

private fun shareRequestInBrowser(
    data: JsAny,
    onSuccess: () -> Unit,
    onError: (String, String) -> Unit,
): Unit =
    js(
        """{
    const failure = (error) => onError(
        error && typeof error.name === 'string' ? error.name : 'Error',
        error && typeof error.message === 'string' ? error.message : 'Browser sharing failed.'
    );
    let promise;
    try { promise = navigator.share(data); }
    catch (error) { failure(error); return; }
    promise.then(() => onSuccess(), failure);
}""",
    )
