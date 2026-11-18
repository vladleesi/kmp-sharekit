package consumer

import dev.vladleesi.kmpsharekit.ShareKit
import dev.vladleesi.kmpsharekit.ShareFile
import dev.vladleesi.kmpsharekit.ShareFilesRequest
import dev.vladleesi.kmpsharekit.ShareResult

fun shareFromUserGesture(text: String) {
    if (ShareKit.isSupported()) ShareKit.shareText(text)
}

fun shareImageFromUserGesture(bytes: ByteArray, onResult: (ShareResult) -> Unit) {
    val request = ShareFilesRequest(listOf(ShareFile("photo.png", "image/png", bytes)))
    if (ShareKit.canShareFiles(request)) ShareKit.shareFiles(request, onResult)
}

fun shareRequestFromUserGesture(text: String, onResult: (dev.vladleesi.kmpsharekit.ShareResult) -> Unit) {
    val request = dev.vladleesi.kmpsharekit.ShareRequest(text = text, url = "https://example.com")
    val capabilities = ShareKit.capabilities()
    if (capabilities.method != dev.vladleesi.kmpsharekit.ShareMethod.UNAVAILABLE && ShareKit.canShare(request)) {
        ShareKit.share(request, onResult)
    }
}
