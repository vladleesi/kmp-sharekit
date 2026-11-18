package dev.vladleesi.kmpsharekit

/** An in-memory file, such as an encoded image. ShareKit takes a copy of [bytes]. */
public class ShareFile(
    public val name: String,
    public val mimeType: String,
    bytes: ByteArray,
) {
    init {
        require(
            name.isNotBlank() && name.first() != ' ' && name.last() !in ". " &&
                name.none { it.isISOControl() || it in "/\\:\"<>|?*" } &&
                !reservedFileName.matches(name.substringBefore('.').trimEnd()),
        ) { "Use a portable file name without directories." }
        require(mimeTypePattern.matches(mimeType)) { "Use a concrete MIME type such as image/png." }
    }

    internal val bytes: ByteArray = bytes.copyOf()
}

/** One or more files, with optional [text] and a [title] where supported. */
public class ShareFilesRequest(
    files: List<ShareFile>,
    public val text: String? = null,
    public val title: String? = null,
) {
    internal val files: List<ShareFile> = files.toList()
}

private val reservedFileName = Regex("CON|PRN|AUX|NUL|COM[0-9¹²³]|LPT[0-9¹²³]", RegexOption.IGNORE_CASE)
private val mimeTypePattern = Regex("[a-zA-Z0-9!#$&^_.+%-]+/[a-zA-Z0-9!#$&^_.+%-]+")

internal fun ShareFilesRequest.failure(capabilities: ShareCapabilities): ShareResult.Failed? =
    when {
        files.isEmpty() -> ShareResult.Failed(ShareFailure.INVALID_REQUEST)
        title != null && !capabilities.supportsTitle -> ShareResult.Failed(ShareFailure.UNSUPPORTED_CONTENT)
        capabilities.method == ShareMethod.UNAVAILABLE -> ShareResult.Failed(ShareFailure.UNAVAILABLE)
        capabilities.method != ShareMethod.NATIVE -> ShareResult.Failed(ShareFailure.UNSUPPORTED_CONTENT)
        else -> null
    }
