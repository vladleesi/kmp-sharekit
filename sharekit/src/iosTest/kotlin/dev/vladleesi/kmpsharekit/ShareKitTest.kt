@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.vladleesi.kmpsharekit

import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UINavigationController
import platform.UIKit.UITabBarController
import platform.UIKit.UIView
import platform.UIKit.UIViewController
import platform.UIKit.popoverPresentationController
import platform.darwin.DISPATCH_TIME_FOREVER
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.darwin.dispatch_semaphore_create
import platform.darwin.dispatch_semaphore_signal
import platform.darwin.dispatch_semaphore_wait
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class ShareKitTest {
    @Test
    fun requestCompletionIsReportedOnlyOnce() {
        val results = mutableListOf<ShareResult>()
        val sheet = requestActivityViewController(ShareRequest(url = "https://example.com"), UIViewController()) { results += it }
        val completion = assertNotNull(sheet.completionWithItemsHandler)
        completion(null, true, null, null)
        completion(null, false, null, null)
        assertEquals(listOf<ShareResult>(ShareResult.Completed), results)
    }

    @Test
    fun requestCancellationIsObservable() {
        var result: ShareResult? = null
        val sheet = requestActivityViewController(ShareRequest(text = "Hi"), UIViewController()) { result = it }
        assertNotNull(sheet.completionWithItemsHandler)(null, false, null, null)
        assertEquals(ShareResult.Cancelled, result)
    }

    @Test
    fun rejectsUnsupportedTitleAndMissingHost() {
        var result: ShareResult? = null
        ShareKit.share(ShareRequest(text = "Hi", title = "Title"), UIViewController()) { result = it }
        assertEquals(ShareFailure.UNSUPPORTED_CONTENT, (result as ShareResult.Failed).reason)
        ShareKit.share(ShareRequest(text = "Hi"), UIViewController()) { result = it }
        assertEquals(ShareFailure.HOST_UNAVAILABLE, (result as ShareResult.Failed).reason)
    }

    @Test
    fun findsTheVisibleControllerInsideNavigationAndTabs() {
        val first = UIViewController()
        val selected = UIViewController()
        val tabs =
            UITabBarController().apply {
                viewControllers = listOf(first, UINavigationController(selected))
                selectedIndex = 1uL
            }
        assertEquals(selected, visibleViewController(tabs))
    }

    @Test
    fun returnsTheRootWithoutContainers() {
        val root = UIViewController()
        assertEquals(root, visibleViewController(root))
    }

    @Test
    fun anchorsTheShareSheetForIPad() {
        val presenter = UIViewController()
        presenter.view = UIView(frame = CGRectMake(0.0, 0.0, 320.0, 640.0))
        val sheet = activityViewController("Text", presenter)
        val popover = assertNotNull(sheet.popoverPresentationController)
        assertEquals(presenter.view, popover.sourceView)
        assertEquals(0uL, popover.permittedArrowDirections)
        assertEquals(320.0, popover.sourceRect.useContents { size.width })
        assertEquals(640.0, popover.sourceRect.useContents { size.height })
    }

    @Test
    fun rejectsAControllerThatIsNotInAWindow() {
        assertFailsWith<IllegalStateException> { ShareKit.shareText("Text", UIViewController()) }
    }

    @Test
    fun rejectsBackgroundThreadCallsBeforeAccessingUIKit() {
        var failure: Throwable? = null
        val semaphore = dispatch_semaphore_create(0)
        dispatch_async(dispatch_get_global_queue(0, 0u)) {
            failure = runCatching { ShareKit.shareText("Text") }.exceptionOrNull()
            dispatch_semaphore_signal(semaphore)
        }
        dispatch_semaphore_wait(semaphore, DISPATCH_TIME_FOREVER)
        assertEquals("ShareKit must be called from the main thread.", failure?.message)
    }
}
