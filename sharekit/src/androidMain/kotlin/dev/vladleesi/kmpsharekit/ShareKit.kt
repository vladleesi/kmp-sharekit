package dev.vladleesi.kmpsharekit

import android.app.Activity
import android.content.Intent
import android.os.Looper
import java.lang.ref.WeakReference

public actual object ShareKit {
    private var activityReference: WeakReference<Activity>? = null

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
