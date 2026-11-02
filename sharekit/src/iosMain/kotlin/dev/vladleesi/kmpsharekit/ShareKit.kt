@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.vladleesi.kmpsharekit

import platform.Foundation.NSThread
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
