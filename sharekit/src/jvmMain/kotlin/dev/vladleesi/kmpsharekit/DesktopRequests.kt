package dev.vladleesi.kmpsharekit

import java.awt.EventQueue
import java.awt.GraphicsEnvironment
import java.util.concurrent.atomic.AtomicBoolean

internal class DesktopRequests(
    private val email: DesktopSharing = DesktopSharing(),
    private val mac: Boolean = System.getProperty("os.name").startsWith("Mac"),
    private val nativeAvailable: () -> Boolean = { !GraphicsEnvironment.isHeadless() && MacSharingBridge.loaded },
    private val nativeShare: (ShareRequest, (ShareResult) -> Unit) -> Unit = { request, callback ->
        MacSharingBridge.present(request.text, request.url, request.title, MacSharingCallback(callback))
    },
) {
    private val pending = AtomicBoolean()

    fun capabilities(method: ShareMethod = defaultMethod()): ShareCapabilities {
        val supported =
            when (method) {
                ShareMethod.NATIVE -> mac && nativeAvailable()
                ShareMethod.EMAIL -> email.isSupported()
                ShareMethod.UNAVAILABLE -> false
            }
        return ShareCapabilities(if (supported) method else ShareMethod.UNAVAILABLE, true)
    }

    fun share(
        request: ShareRequest,
        method: ShareMethod = defaultMethod(),
        onResult: (ShareResult) -> Unit,
    ) {
        val failure = request.failure(capabilities(method))
        if (failure != null) {
            onResult(failure)
            return
        }
        if (method == ShareMethod.EMAIL) {
            email.share(request, onResult)
            return
        }
        if (!pending.compareAndSet(false, true)) {
            onResult(ShareResult.Failed(ShareFailure.BUSY))
            return
        }
        val finished = AtomicBoolean()
        val complete: (ShareResult) -> Unit = { result ->
            if (finished.compareAndSet(false, true)) {
                pending.set(false)
                onResult(result)
            }
        }
        try {
            nativeShare(request, complete)
        } catch (error: Exception) {
            // A synchronous user callback must propagate, rather than become a second result.
            if (finished.get()) throw error
            complete(ShareResult.Failed(ShareFailure.PLATFORM_ERROR, error))
        } catch (error: LinkageError) {
            if (finished.get()) throw error
            complete(ShareResult.Failed(ShareFailure.UNAVAILABLE, error))
        }
    }

    private fun defaultMethod(): ShareMethod = if (mac) ShareMethod.NATIVE else ShareMethod.EMAIL
}

/** JNI calls this adapter; application code always runs later on the AWT event thread. */
internal class MacSharingCallback(
    private val callback: (ShareResult) -> Unit,
) {
    @Suppress("unused") // Called by JNI; keep this name and signature when shrinking desktop applications.
    fun complete(
        status: Int,
        message: String?,
    ) {
        val result =
            when (status) {
                0 -> ShareResult.Launched
                1 -> ShareResult.Cancelled
                2 -> ShareResult.Failed(ShareFailure.HOST_UNAVAILABLE)
                3 -> ShareResult.Failed(ShareFailure.BUSY)
                else -> ShareResult.Failed(ShareFailure.PLATFORM_ERROR, IllegalStateException(message ?: "macOS sharing failed"))
            }
        EventQueue.invokeLater { callback(result) }
    }
}

internal object MacSharingBridge {
    val loaded: Boolean by lazy {
        try {
            System.loadLibrary("sharekit")
            available()
        } catch (_: Exception) {
            false
        } catch (_: LinkageError) {
            false
        }
    }

    private external fun available(): Boolean

    external fun present(
        text: String?,
        url: String?,
        title: String?,
        callback: MacSharingCallback,
    )
}
