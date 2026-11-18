package dev.vladleesi.sharekit.consumer.compose

import androidx.compose.runtime.Composable
import dev.vladleesi.kmpsharekit.ShareFile
import dev.vladleesi.kmpsharekit.ShareFilesRequest
import dev.vladleesi.kmpsharekit.ShareRequest
import dev.vladleesi.kmpsharekit.compose.rememberShareState

@Composable
fun rememberShareActions(
    image: ByteArray,
): Pair<() -> Unit, () -> Unit> {
    val state = rememberShareState()
    return { state.share(ShareRequest(text = "Hello")) } to
        { state.shareFiles(ShareFilesRequest(listOf(ShareFile("sample.png", "image/png", image)))) }
}
