package dev.vladleesi.kmpsharekit

/** Text and/or an absolute HTTP(S) URL. [title] is optional presentation metadata. */
public data class ShareRequest(
    public val text: String? = null,
    public val url: String? = null,
    public val title: String? = null,
)

/** The UI used by the current platform. */
public enum class ShareMethod { UNAVAILABLE, NATIVE, EMAIL }

/** Capability only; does not guarantee a ready host or installed share targets. */
public data class ShareCapabilities(
    public val method: ShareMethod,
    public val supportsTitle: Boolean,
)

/** Portable failure categories. [ShareResult.Failed.cause] preserves platform details when available. */
public enum class ShareFailure {
    UNAVAILABLE,
    INVALID_REQUEST,
    UNSUPPORTED_CONTENT,
    HOST_UNAVAILABLE,
    WRONG_THREAD,
    USER_ACTIVATION_REQUIRED,
    PERMISSION_DENIED,
    BUSY,
    PLATFORM_ERROR,
}

/** One terminal observation per request. No outcome guarantees delivery to a recipient. */
public sealed interface ShareResult {
    /** Android chooser/desktop mail composer launched, or macOS service selected; later outcome is unknown. */
    public data object Launched : ShareResult

    /** iOS activity completed or the browser share promise resolved; delivery is not confirmed. */
    public data object Completed : ShareResult

    /** The platform explicitly reported cancellation. */
    public data object Cancelled : ShareResult

    /** Web Share AbortError cannot distinguish cancellation from missing targets. */
    public data object CancelledOrUnavailable : ShareResult

    /** A preflight or platform failure. */
    public data class Failed(
        public val reason: ShareFailure,
        public val cause: Throwable? = null,
    ) : ShareResult
}

internal fun ShareRequest.failure(capabilities: ShareCapabilities): ShareResult.Failed? =
    when {
        text.isNullOrBlank() && url == null -> ShareResult.Failed(ShareFailure.INVALID_REQUEST)
        url != null && !validShareUrl(url) -> ShareResult.Failed(ShareFailure.INVALID_REQUEST)
        title != null && !capabilities.supportsTitle -> ShareResult.Failed(ShareFailure.UNSUPPORTED_CONTENT)
        capabilities.method == ShareMethod.UNAVAILABLE -> ShareResult.Failed(ShareFailure.UNAVAILABLE)
        else -> null
    }

internal expect fun validShareUrl(url: String): Boolean

internal fun ShareRequest.textWithUrl(): String = listOfNotNull(text, url).joinToString("\n")
