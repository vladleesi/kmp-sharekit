@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.vladleesi.kmpsharekit

import platform.Foundation.NSThread
import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UINavigationController
import platform.UIKit.UISceneActivationStateForegroundActive
import platform.UIKit.UITabBarController
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UIKit.popoverPresentationController

public actual object ShareKit {
    private var sharing = false

    public actual fun capabilities(): ShareCapabilities = ShareCapabilities(ShareMethod.NATIVE, false)

    public actual fun canShare(request: ShareRequest): Boolean = request.failure(capabilities()) == null

    public actual fun share(
        request: ShareRequest,
        onResult: (ShareResult) -> Unit,
    ) {
        if (!NSThread.isMainThread) {
            onResult(ShareResult.Failed(ShareFailure.WRONG_THREAD))
            return
        }
        shareFromController(request, foregroundRootViewController(), onResult)
    }

    /** Shares from the specified window's controller. */
    public fun share(
        request: ShareRequest,
        presentingViewController: UIViewController,
        onResult: (ShareResult) -> Unit,
    ) {
        shareFromController(request, presentingViewController, onResult)
    }

    private fun shareFromController(
        request: ShareRequest,
        controller: UIViewController?,
        onResult: (ShareResult) -> Unit,
    ) {
        if (!NSThread.isMainThread) {
            onResult(ShareResult.Failed(ShareFailure.WRONG_THREAD))
            return
        }
        request.failure(capabilities())?.let {
            onResult(it)
            return
        }
        if (sharing) {
            onResult(ShareResult.Failed(ShareFailure.BUSY))
            return
        }
        val presenter = controller?.let(::visibleViewController)
        if (presenter == null || presenter.view.window == null || presenter.isBeingDismissed()) {
            onResult(ShareResult.Failed(ShareFailure.HOST_UNAVAILABLE))
            return
        }
        if (presenter is UIActivityViewController || presenter.isBeingPresented()) {
            onResult(ShareResult.Failed(ShareFailure.BUSY))
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
        val failure =
            try {
                val sheet = requestActivityViewController(request, presenter, finish)
                presenter.presentViewController(sheet, animated = true, completion = null)
                null
            } catch (error: Exception) {
                ShareResult.Failed(ShareFailure.PLATFORM_ERROR, error)
            }
        if (failure != null) finish(failure)
    }

    public actual fun isSupported(): Boolean = true

    public actual fun shareText(text: String) {
        checkMainThread()
        val presenter =
            checkNotNull(foregroundRootViewController()) {
                "No foreground view controller available. Use ShareKit.shareText(text, presentingViewController)."
            }
        shareText(text, presenter)
    }

    /** Shares from a specific controller, for example in apps with multiple windows. */
    public fun shareText(
        text: String,
        presentingViewController: UIViewController,
    ) {
        checkMainThread()
        val presenter = visibleViewController(presentingViewController)
        check(presenter.view.window != null && !presenter.isBeingDismissed()) {
            "The presenting view controller must be visible in a window."
        }
        presenter.presentViewController(
            viewControllerToPresent = activityViewController(text, presenter),
            animated = true,
            completion = null,
        )
    }

    private fun checkMainThread() {
        check(NSThread.isMainThread) { "ShareKit must be called from the main thread." }
    }
}

internal fun visibleViewController(root: UIViewController): UIViewController {
    val next =
        root.presentedViewController?.takeUnless { it.isBeingDismissed() }
            ?: (root as? UINavigationController)?.visibleViewController
            ?: (root as? UITabBarController)?.selectedViewController
    return if (next != null) visibleViewController(next) else root
}

internal fun activityViewController(
    text: String,
    presenter: UIViewController,
): UIActivityViewController =
    UIActivityViewController(listOf(text), null).apply {
        // iPad requires a popover anchor. A centered sheet works for the text-only API.
        popoverPresentationController?.apply {
            sourceView = presenter.view
            sourceRect = presenter.view.bounds
            permittedArrowDirections = 0uL
        }
    }

@Suppress("DEPRECATION") // Apps without a scene manifest still use UIApplication.windows.
private fun foregroundRootViewController(): UIViewController? {
    val application = UIApplication.sharedApplication
    val scenes = application.connectedScenes.filterIsInstance<UIWindowScene>()
    val windows =
        if (scenes.isNotEmpty()) {
            scenes
                .filter { it.activationState == UISceneActivationStateForegroundActive }
                .flatMap { it.windows.filterIsInstance<UIWindow>() }
        } else {
            application.windows.filterIsInstance<UIWindow>()
        }
    return windows.firstOrNull { it.isKeyWindow() }?.rootViewController
}

internal actual fun validShareUrl(url: String): Boolean {
    if (url.any { it.isWhitespace() }) return false
    val parsed = NSURL.URLWithString(url) ?: return false
    return parsed.scheme?.lowercase() in listOf("http", "https") && !parsed.host.isNullOrEmpty()
}

internal fun requestActivityViewController(
    request: ShareRequest,
    presenter: UIViewController,
    onResult: (ShareResult) -> Unit,
): UIActivityViewController =
    UIActivityViewController(
        listOfNotNull(request.text, request.url?.let { NSURL.URLWithString(it) }),
        null,
    ).apply {
        popoverPresentationController?.apply {
            sourceView = presenter.view
            sourceRect = presenter.view.bounds
            permittedArrowDirections = 0uL
        }
        var completed = false
        completionWithItemsHandler = { _, success, _, error ->
            if (!completed) {
                completed = true
                onResult(
                    when {
                        error != null -> ShareResult.Failed(ShareFailure.PLATFORM_ERROR, IllegalStateException(error.localizedDescription))
                        success -> ShareResult.Completed
                        else -> ShareResult.Cancelled
                    },
                )
            }
        }
    }
