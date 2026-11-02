package dev.vladleesi.kmpsharekit

/** Native plain-text sharing. Call [shareText] from the main thread or a browser user gesture. */
public expect object ShareKit {
    /**
     * Reports whether the platform supports native text sharing, without opening a share sheet.
     *
     * Returns `true` on Android and iOS. Browsers require a secure context and the Web Share API.
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
     */
    public fun shareText(text: String)
}
