package dev.vladleesi.kmpsharekit

/** Text and URL sharing. Call from the UI thread or a browser user gesture. */
public expect object ShareKit {
    /** Reports the sharing method and supported request fields without opening UI. */
    public fun capabilities(): ShareCapabilities

    /** Checks request validity and capability, without requiring a host or user gesture. */
    public fun canShare(request: ShareRequest): Boolean

    /**
     * Starts sharing immediately. Call on the UI thread or directly within a browser user gesture.
     * Preflight errors are delivered synchronously. Android/JVM email report launch synchronously;
     * iOS/browser report the later platform outcome. macOS native reports picker cancellation or
     * handoff on the AWT event thread. The callback receives one terminal result.
     * User callback exceptions propagate on the callback thread; no implicit clipboard or email fallback is performed.
     */
    public fun share(
        request: ShareRequest,
        onResult: (ShareResult) -> Unit,
    )

    /**
     * Reports whether the platform supports text sharing, without opening sharing UI.
     *
     * Returns `true` on Android and iOS. Browsers require a secure context and the Web Share API.
     * JVM desktop checks graphical Desktop MAIL support, preserving legacy [shareText] behavior.
     * Use [capabilities] or [canShare] for request-based native macOS sharing.
     * This does not check presentation-host readiness, user activation, permissions, or share targets,
     * and does not guarantee that [shareText] will succeed. Safe to call from shared Compose UI code.
     */
    public fun isSupported(): Boolean

    /**
     * Passes [text] unchanged, including empty strings and Unicode, to the platform.
     *
     * Android requires a current Activity registered with `setActivity`.
     * iOS requires a visible view controller in the foreground window.
     * Throws [IllegalStateException] when no usable presentation host exists.
     *
     * Browsers require a secure context (normally HTTPS) and a direct user gesture.
     * Throws [UnsupportedOperationException] when Web Share is unavailable, or
     * [IllegalStateException] for an insecure context or missing user activation.
     * Browser sharing is asynchronous: cancellation is ignored and later errors are logged.
     * Use the browser-only callback overload to observe the outcome. Browsers may reject empty text.
     *
     * JVM desktop opens an email draft in the default mail client using a `mailto:` URI.
     * Throws [UnsupportedOperationException] in headless environments or without Desktop MAIL support,
     * or [IllegalStateException] if the mail client cannot be launched. A configured client is required;
     * clients may limit text length or normalize line endings. No email is sent automatically.
     */
    public fun shareText(text: String)
}
