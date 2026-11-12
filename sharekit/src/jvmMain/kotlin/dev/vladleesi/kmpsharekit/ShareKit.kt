package dev.vladleesi.kmpsharekit

import java.awt.Desktop
import java.io.IOException
import java.net.URI
import java.net.URLEncoder

public actual object ShareKit {
    private val sharing = DesktopSharing()

    /** Checks desktop MAIL support. A configured mail client is still required to share. */
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
