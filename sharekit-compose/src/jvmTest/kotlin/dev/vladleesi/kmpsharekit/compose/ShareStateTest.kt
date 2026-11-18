package dev.vladleesi.kmpsharekit.compose

import dev.vladleesi.kmpsharekit.ShareFailure
import dev.vladleesi.kmpsharekit.ShareFilesRequest
import dev.vladleesi.kmpsharekit.ShareRequest
import dev.vladleesi.kmpsharekit.ShareResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class ShareStateTest {
    @Test
    fun managesPendingActionsAndIgnoresLateResults() {
        val state = ShareState()
        lateinit var first: (ShareResult) -> Unit
        state.launch { first = it }
        assertTrue(state.isSharing)
        assertNull(state.result)
        state.launch { fail("The pending share should own the state.") }
        state.clearResult()
        first(ShareResult.Cancelled)
        assertFalse(state.isSharing)
        assertEquals(ShareResult.Cancelled, state.result)

        lateinit var second: (ShareResult) -> Unit
        state.launch { second = it }
        first(ShareResult.Launched)
        assertTrue(state.isSharing)
        assertNull(state.result)
        second(ShareResult.Completed)
        assertEquals(ShareResult.Completed, state.result)
        state.clearResult()
        assertNull(state.result)
    }

    @Test
    fun surfacesSynchronousResultsFromShareKit() {
        val state = ShareState()
        state.share(ShareRequest())
        assertFalse(state.isSharing)
        assertEquals(ShareResult.Failed(ShareFailure.INVALID_REQUEST), state.result)
        state.shareFiles(ShareFilesRequest(emptyList()))
        assertFalse(state.isSharing)
        assertEquals(ShareResult.Failed(ShareFailure.INVALID_REQUEST), state.result)
    }

    @Test
    fun releasesPendingStateWhenLaunchThrows() {
        val state = ShareState()
        assertFailsWith<IllegalStateException> {
            state.launch { error("Launch failed.") }
        }
        assertFalse(state.isSharing)
        state.launch { it(ShareResult.Launched) }
        assertEquals(ShareResult.Launched, state.result)
    }
}
