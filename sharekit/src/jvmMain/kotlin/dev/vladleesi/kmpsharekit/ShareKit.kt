package dev.vladleesi.kmpsharekit

import java.awt.Desktop
import java.io.IOException
import java.net.URI
import java.net.URLEncoder

public actual object ShareKit {
    private val sharing = DesktopSharing()
    private val requests = DesktopRequests(email = sharing)

    public actual fun capabilities(): ShareCapabilities = requests.capabilities()

    /** Checks an explicit desktop method without opening UI. */
    public fun capabilities(method: ShareMethod): ShareCapabilities = requests.capabilities(method)

    public actual fun canShare(request: ShareRequest): Boolean = request.failure(capabilities()) == null

    public actual fun share(
        request: ShareRequest,
        onResult: (ShareResult) -> Unit,
    ) {
        requests.share(request, onResult = onResult)
    }

    /** Uses an explicit desktop method. Native failures never fall back to email automatically. */
    public fun share(
        request: ShareRequest,
        method: ShareMethod,
        onResult: (ShareResult) -> Unit,
    ) {
        requests.share(request, method, onResult)
    }

    /** Checks MAIL support for legacy [shareText]. Use [capabilities] for request-based sharing. */
    public actual fun isSupported(): Boolean = sharing.isSupported()

    /** Opens an email draft containing [text] in the default mail client. Does not send email. */
    public actual fun shareText(text: String) {
        sharing.shareText(text)
    }
}

internal class DesktopSharing(
    private val canMail: () -> Boolean = {
        Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.MAIL)
    },
    private val mail: (URI) -> Unit = { Desktop.getDesktop().mail(it) },
) {
    fun isSupported(): Boolean =
        try {
            canMail()
        } catch (_: UnsupportedOperationException) {
            false
        } catch (_: SecurityException) {
            false
        }

    fun share(
        request: ShareRequest,
        onResult: (ShareResult) -> Unit,
    ) {
        val capabilities = ShareCapabilities(if (isSupported()) ShareMethod.EMAIL else ShareMethod.UNAVAILABLE, true)
        val failure = request.failure(capabilities)
        if (failure != null) {
            onResult(failure)
            return
        }
        val body = URLEncoder.encode(request.textWithUrl(), "UTF-8").replace("+", "%20")
        val subject = request.title?.let { "&subject=" + URLEncoder.encode(it, "UTF-8").replace("+", "%20") } ?: ""
        val result = shareAttempt { mail(URI.create("mailto:?body=$body$subject")) }
        onResult(result)
    }

    fun shareText(text: String) {
        if (!isSupported()) {
            throw UnsupportedOperationException(
                "Desktop text sharing requires a graphical environment with Desktop.Action.MAIL support.",
            )
        }
        // Form encoding escapes header delimiters; mailto requires %20 rather than '+' for spaces.
        val body = URLEncoder.encode(text, "UTF-8").replace("+", "%20")
        val uri = URI.create("mailto:?body=$body")
        try {
            mail(uri)
        } catch (error: IOException) {
            throw IllegalStateException("Unable to open the default mail client for text sharing.", error)
        } catch (error: SecurityException) {
            throw IllegalStateException("Access to the default mail client was denied.", error)
        }
    }
}

internal actual fun validShareUrl(url: String): Boolean =
    try {
        val uri = URI(url)
        uri.scheme?.lowercase() in listOf("http", "https") && !uri.host.isNullOrEmpty()
    } catch (_: Exception) {
        false
    }

// Invoke user code outside platform try/catch blocks so callback exceptions are never reported twice.
internal fun shareAttempt(block: () -> Unit): ShareResult =
    try {
        block()
        ShareResult.Launched
    } catch (error: SecurityException) {
        ShareResult.Failed(ShareFailure.PERMISSION_DENIED, error)
    } catch (error: UnsupportedOperationException) {
        ShareResult.Failed(ShareFailure.UNAVAILABLE, error)
    } catch (error: Exception) {
        ShareResult.Failed(ShareFailure.PLATFORM_ERROR, error)
    }
