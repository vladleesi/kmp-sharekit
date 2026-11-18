package dev.vladleesi.kmpsharekit.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.vladleesi.kmpsharekit.ShareFilesRequest
import dev.vladleesi.kmpsharekit.ShareKit
import dev.vladleesi.kmpsharekit.ShareRequest
import dev.vladleesi.kmpsharekit.ShareResult

/** Remembers sharing state for this composition. Call its share methods from UI actions. */
@Composable
public fun rememberShareState(): ShareState = remember { ShareState() }

/** Observable state for a single share action at a time. */
@Stable
public class ShareState internal constructor() {
    private var state by mutableStateOf(ShareAction())

    /** Whether ShareKit is waiting for the platform to report a result. */
    public val isSharing: Boolean get() = state.pending

    /** The latest result, cleared when another share starts or [clearResult] is called. */
    public val result: ShareResult? get() = state.result

    /** Starts sharing text or a link directly from the UI action. */
    public fun share(request: ShareRequest) {
        launch { ShareKit.share(request, it) }
    }

    /** Starts sharing files directly from the UI action. */
    public fun shareFiles(request: ShareFilesRequest) {
        launch { ShareKit.shareFiles(request, it) }
    }

    /** Clears the displayed result. An active action can still report its outcome. */
    public fun clearResult() {
        if (state.result != null) state = ShareAction()
    }

    internal fun launch(share: ((ShareResult) -> Unit) -> Unit) {
        if (state.pending) return

        val action = ShareAction(pending = true)
        state = action
        try {
            share { result ->
                if (state === action) state = ShareAction(result = result)
            }
        } catch (error: Throwable) {
            if (state === action) state = ShareAction()
            throw error
        }
    }

    private class ShareAction(
        val pending: Boolean = false,
        val result: ShareResult? = null,
    )
}
