package consumer

import dev.vladleesi.kmpsharekit.ShareKit
import dev.vladleesi.kmpsharekit.ShareMethod
import dev.vladleesi.kmpsharekit.ShareRequest
import dev.vladleesi.kmpsharekit.ShareResult

fun emailFromDesktop(text: String, onResult: (ShareResult) -> Unit) {
    if (ShareKit.capabilities(ShareMethod.EMAIL).method == ShareMethod.EMAIL) {
        ShareKit.share(ShareRequest(text = text), ShareMethod.EMAIL, onResult)
    }
}
