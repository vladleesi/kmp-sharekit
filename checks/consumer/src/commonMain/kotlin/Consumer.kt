package consumer

import dev.vladleesi.kmpsharekit.ShareKit

fun shareFromUserGesture(text: String) {
    if (ShareKit.isSupported()) ShareKit.shareText(text)
}

fun shareRequestFromUserGesture(text: String, onResult: (dev.vladleesi.kmpsharekit.ShareResult) -> Unit) {
    val request = dev.vladleesi.kmpsharekit.ShareRequest(text = text, url = "https://example.com")
    val capabilities = ShareKit.capabilities()
    if (capabilities.method != dev.vladleesi.kmpsharekit.ShareMethod.UNAVAILABLE && ShareKit.canShare(request)) {
        ShareKit.share(request, onResult)
    }
}
