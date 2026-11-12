package dev.vladleesi.kmpsharekit

import android.app.Activity
import android.content.Intent
import android.os.Looper
import java.lang.ref.WeakReference
import java.net.URI

public actual object ShareKit {
    private var activityReference: WeakReference<Activity>? = null

    public actual fun isSupported(): Boolean = true

    public actual fun capabilities(): ShareCapabilities = ShareCapabilities(ShareMethod.NATIVE, true)

    public actual fun canShare(request: ShareRequest): Boolean = request.failure(capabilities()) == null

    public actual fun share(
        request: ShareRequest,
        onResult: (ShareResult) -> Unit,
    ) {
        shareFromActivity(activityReference?.get(), request, onResult)
    }

    /** Shares using an explicit Activity without retaining it. */
    public fun share(
        activity: Activity,
        request: ShareRequest,
        onResult: (ShareResult) -> Unit,
    ) {
        shareFromActivity(activity, request, onResult)
    }

    private fun shareFromActivity(
        activity: Activity?,
        request: ShareRequest,
        onResult: (ShareResult) -> Unit,
    ) {
        val failure =
            when {
                Looper.myLooper() != Looper.getMainLooper() -> ShareResult.Failed(ShareFailure.WRONG_THREAD)
                else -> request.failure(capabilities())
            }
        if (failure != null) {
            onResult(failure)
            return
        }
        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            onResult(ShareResult.Failed(ShareFailure.HOST_UNAVAILABLE))
            return
        }
        val result =
            shareAttempt {
                val intent =
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, request.textWithUrl())
                        request.title?.let {
                            putExtra(Intent.EXTRA_SUBJECT, it)
                            putExtra(Intent.EXTRA_TITLE, it)
                        }
                    }
                activity.startActivity(Intent.createChooser(intent, null))
            }
        onResult(result)
    }

    /** Registers the current Activity without retaining it. Call from `onResume`. */
    public fun setActivity(activity: Activity) {
        checkMainThread()
        activityReference = WeakReference(activity)
    }

    /**
     * Compatibility entry point. Resolves [provider] immediately and keeps only a weak reference.
     * Prefer [setActivity] or the explicit Activity overload of [shareText].
     */
    @Deprecated(
        message = "Use setActivity(activity); the provider is resolved immediately.",
        replaceWith = ReplaceWith("ShareKit.setActivity(provider())", "dev.vladleesi.kmpsharekit.ShareKit"),
    )
    public fun setActivityProvider(provider: () -> Activity) {
        setActivity(provider())
    }

    public actual fun shareText(text: String) {
        checkMainThread()
        val activity =
            checkNotNull(activityReference?.get()) {
                "No Activity registered. Call ShareKit.setActivity(activity) from onResume, " +
                    "or use ShareKit.shareText(activity, text)."
            }
        shareText(activity, text)
    }

    /** Shares [text] from [activity] without registering or retaining it. */
    public fun shareText(
        activity: Activity,
        text: String,
    ) {
        checkMainThread()
        check(!activity.isFinishing && !activity.isDestroyed) {
            "Cannot share from a finishing or destroyed Activity."
        }
        val intent =
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
        activity.startActivity(Intent.createChooser(intent, null))
    }

    private fun checkMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "ShareKit must be called from the main thread."
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
