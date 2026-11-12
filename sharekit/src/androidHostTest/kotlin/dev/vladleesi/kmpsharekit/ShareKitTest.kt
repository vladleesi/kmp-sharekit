package dev.vladleesi.kmpsharekit

import android.app.Activity
import android.content.Intent
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 37])
class ShareKitTest {
    @Test
    fun richRequestUsesAndroidMetadataAndReportsLaunch() {
        Robolectric.buildActivity(Activity::class.java).setup().use { controller ->
            var result: ShareResult? = null
            ShareKit.share(controller.get(), ShareRequest("Hello", "https://example.com", "Title")) { result = it }
            assertEquals(ShareResult.Launched, result)
            val chooser = assertNotNull(shadowOf(controller.get()).nextStartedActivity)

            @Suppress("DEPRECATION")
            val send = assertNotNull(chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT))
            assertEquals("Hello\nhttps://example.com", send.getStringExtra(Intent.EXTRA_TEXT))
            assertEquals("Title", send.getStringExtra(Intent.EXTRA_SUBJECT))
            assertEquals("Title", send.getStringExtra(Intent.EXTRA_TITLE))
        }
    }

    @Test
    fun newApiReportsDestroyedHostAndDoesNotCatchCallerExceptions() {
        Robolectric.buildActivity(Activity::class.java).setup().use { controller ->
            controller.get().finish()
            var calls = 0
            assertFailsWith<IllegalArgumentException> {
                ShareKit.share(controller.get(), ShareRequest(text = "Hi")) {
                    calls++
                    assertEquals(ShareFailure.HOST_UNAVAILABLE, (it as ShareResult.Failed).reason)
                    throw IllegalArgumentException("Caller")
                }
            }
            assertEquals(1, calls)
        }
    }

    @Test
    fun sharesPlainTextInAChooserWithoutChangingThePayload() {
        Robolectric.buildActivity(Activity::class.java).setup().use { controller ->
            val activity = controller.get()
            listOf("Hello", "", "Line 1\nLine 2 — Привет 世界").forEach { text ->
                ShareKit.shareText(activity, text)
                assertShareIntent(activity, text)
            }
        }
    }

    @Test
    fun commonEntryPointUsesTheLatestRegisteredActivity() {
        Robolectric.buildActivity(Activity::class.java).setup().use { first ->
            Robolectric.buildActivity(Activity::class.java).setup().use { second ->
                ShareKit.setActivity(first.get())
                ShareKit.setActivity(second.get())
                ShareKit.shareText("Current host")
                assertShareIntent(second.get(), "Current host")
                assertNull(shadowOf(first.get()).nextStartedActivity)
            }
        }
    }

    @Test
    @Suppress("DEPRECATION")
    fun legacyProviderIsResolvedOnceAtRegistration() {
        Robolectric.buildActivity(Activity::class.java).setup().use { controller ->
            var calls = 0
            ShareKit.setActivityProvider {
                calls++
                controller.get()
            }
            ShareKit.shareText("One")
            ShareKit.shareText("Two")
            assertEquals(1, calls)
        }
    }

    @Test
    fun rejectsFinishingActivities() {
        Robolectric.buildActivity(Activity::class.java).setup().use { controller ->
            controller.get().finish()
            assertFailsWith<IllegalStateException> { ShareKit.shareText(controller.get(), "Text") }
            assertNull(shadowOf(controller.get()).nextStartedActivity)
        }
    }

    @Test
    fun rejectsDestroyedRegisteredActivities() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        ShareKit.setActivity(controller.get())
        controller.pause().stop().destroy()
        assertFailsWith<IllegalStateException> { ShareKit.shareText("Text") }
    }

    @Test
    fun rejectsBackgroundThreadCalls() {
        Robolectric.buildActivity(Activity::class.java).setup().use { controller ->
            var failure: Throwable? = null
            val thread =
                Thread {
                    failure = runCatching { ShareKit.shareText(controller.get(), "Text") }.exceptionOrNull()
                }
            thread.start()
            thread.join()
            assertTrue(failure is IllegalStateException)
            assertEquals("ShareKit must be called from the main thread.", failure?.message)
            assertNull(shadowOf(controller.get()).nextStartedActivity)
        }
    }

    @Suppress("DEPRECATION")
    private fun assertShareIntent(
        activity: Activity,
        text: String,
    ) {
        val chooser = assertNotNull(shadowOf(activity).nextStartedActivity)
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val send = assertNotNull(chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT))
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("text/plain", send.type)
        assertEquals(text, send.getStringExtra(Intent.EXTRA_TEXT))
    }
}
