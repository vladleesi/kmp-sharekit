package dev.vladleesi.kmpsharekit

/** Opens the platform share sheet for plain text. Call from the main thread. */
public expect object ShareKit {
    /**
     * Shares [text] unchanged, including empty strings and Unicode.
     *
     * Android requires a current Activity registered with `setActivity`.
     * iOS requires a visible view controller in the foreground window.
     * Throws [IllegalStateException] when no usable presentation host exists.
     */
    public fun shareText(text: String)
}
