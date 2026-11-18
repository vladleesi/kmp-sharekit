package dev.vladleesi.sharekit.sample

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() =
    application {
        Window(onCloseRequest = ::exitApplication, title = "ShareKit sample") {
            ShareSample()
        }
    }
