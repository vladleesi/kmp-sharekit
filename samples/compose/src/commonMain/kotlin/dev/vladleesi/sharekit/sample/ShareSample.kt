package dev.vladleesi.sharekit.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.vladleesi.kmpsharekit.ShareFailure
import dev.vladleesi.kmpsharekit.ShareFile
import dev.vladleesi.kmpsharekit.ShareFilesRequest
import dev.vladleesi.kmpsharekit.ShareKit
import dev.vladleesi.kmpsharekit.ShareRequest
import dev.vladleesi.kmpsharekit.ShareResult
import dev.vladleesi.kmpsharekit.compose.rememberShareState
import kotlin.io.encoding.Base64

@Composable
@Suppress("ktlint:standard:function-naming")
fun ShareSample() {
    val sharing = rememberShareState()
    var text by remember { mutableStateOf("Hello from ShareKit") }
    var link by remember { mutableStateOf("https://github.com/vladleesi/kmp-sharekit") }
    val request = ShareRequest(text = text.ifBlank { null }, url = link.ifBlank { null })
    val files = remember { sampleFiles() }

    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
                Column(
                    modifier =
                        Modifier
                            .widthIn(max = 560.dp)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text("ShareKit", style = MaterialTheme.typography.h4)
                    OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), label = { Text("Text") })
                    OutlinedTextField(link, { link = it }, Modifier.fillMaxWidth(), label = { Text("HTTP(S) link") }, singleLine = true)
                    Button(onClick = { sharing.share(request) }, enabled = !sharing.isSharing) {
                        Text("Share text and link")
                    }
                    Text("Sample attachments", style = MaterialTheme.typography.h6)
                    Text("sample.png and notes.txt")
                    Button(onClick = { sharing.shareFiles(files) }, enabled = !sharing.isSharing) {
                        Text("Share files")
                    }
                    val status =
                        if (sharing.isSharing) "Waiting for the share result…" else sharing.result?.message()
                    if (status != null) {
                        Text(status, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    } else if (!ShareKit.canShareFiles(files)) {
                        Text("File sharing is unavailable for this sample on this device or browser.")
                    }
                }
            }
        }
    }
}

private fun sampleFiles(): ShareFilesRequest =
    ShareFilesRequest(
        listOf(
            ShareFile(
                "sample.png",
                "image/png",
                Base64.decode(
                    "iVBORw0KGgoAAAANSUhEUgAAACAAAAAgCAIAAAD8GO2jAAAAQklEQVR4nGN4++U/ViQT1oIVkaqeYdSCUQuGgAXUMgiX" +
                        "+lELRi0YChZQyyBc6kctGLVgKFhALYNwqR+1YNSCIWABAJHcrGr0sva+AAAAAElFTkSuQmCC",
                ),
            ),
            ShareFile("notes.txt", "text/plain", "Shared from the ShareKit sample.\n".encodeToByteArray()),
        ),
    )

private fun ShareResult.message(): String =
    when (this) {
        ShareResult.Launched -> {
            "The share interface opened or a service was selected."
        }

        ShareResult.Completed -> {
            "The platform completed the share action."
        }

        ShareResult.Cancelled -> {
            "Sharing was cancelled."
        }

        ShareResult.CancelledOrUnavailable -> {
            "Sharing was cancelled or no share target was available."
        }

        is ShareResult.Failed -> {
            when (reason) {
                ShareFailure.UNAVAILABLE -> "Sharing is unavailable on this device or browser."
                ShareFailure.INVALID_REQUEST -> "Enter some text, a valid HTTP(S) link, or both."
                ShareFailure.UNSUPPORTED_CONTENT -> "This device or browser cannot share this content."
                ShareFailure.HOST_UNAVAILABLE -> "Bring this app to the foreground and try again."
                ShareFailure.USER_ACTIVATION_REQUIRED -> "Press the share button to start sharing."
                ShareFailure.PERMISSION_DENIED -> "The platform blocked sharing."
                ShareFailure.BUSY -> "Another share is still open."
                ShareFailure.WRONG_THREAD, ShareFailure.PLATFORM_ERROR -> "Sharing failed. Try again."
            }
        }
    }
