package dev.vladleesi.kmpsharekit

import java.awt.EventQueue

internal fun shareOnWindows(
    request: ShareRequest,
    callback: (ShareResult) -> Unit,
) {
    EventQueue.invokeLater {
        val result =
            try {
                windowsSharingResult(WindowsSharingBridge.present(request.text, request.url, request.title))
            } catch (error: LinkageError) {
                ShareResult.Failed(ShareFailure.UNAVAILABLE, error)
            } catch (error: Exception) {
                ShareResult.Failed(ShareFailure.PLATFORM_ERROR, error)
            }
        callback(result)
    }
}

internal fun windowsSharingResult(status: Int): ShareResult =
    when (status) {
        0 -> ShareResult.Launched
        2 -> ShareResult.Failed(ShareFailure.HOST_UNAVAILABLE)
        3 -> ShareResult.Failed(ShareFailure.BUSY)
        5 -> ShareResult.Failed(ShareFailure.PERMISSION_DENIED)
        6 -> ShareResult.Failed(ShareFailure.UNAVAILABLE)
        else -> ShareResult.Failed(ShareFailure.PLATFORM_ERROR)
    }

internal object WindowsSharingBridge {
    val loaded: Boolean by lazy {
        try {
            System.loadLibrary("sharekit_windows")
            true
        } catch (_: Exception) {
            false
        } catch (_: LinkageError) {
            false
        }
    }

    external fun available(): Boolean

    external fun present(
        text: String?,
        url: String?,
        title: String?,
    ): Int
}
