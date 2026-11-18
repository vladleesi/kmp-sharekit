@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.vladleesi.sharekit.sample

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

fun shareSampleViewController(): UIViewController = ComposeUIViewController { ShareSample() }
