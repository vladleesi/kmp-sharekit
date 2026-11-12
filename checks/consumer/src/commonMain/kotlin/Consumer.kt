package consumer

import dev.vladleesi.kmpsharekit.ShareKit

fun shareFromUserGesture(text: String) {
    if (ShareKit.isSupported()) ShareKit.shareText(text)
}
