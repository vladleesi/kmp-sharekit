@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package dev.vladleesi.kmpsharekit

import kotlin.js.JsAny
import kotlin.js.toJsReference

internal actual fun ByteArray.browserBytes(): JsAny = toJsReference()
