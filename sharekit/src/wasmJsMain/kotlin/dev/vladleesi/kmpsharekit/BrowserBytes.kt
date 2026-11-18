@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class, kotlin.wasm.unsafe.UnsafeWasmMemoryApi::class)

package dev.vladleesi.kmpsharekit

import kotlin.js.JsAny
import kotlin.js.js
import kotlin.wasm.unsafe.wasmMemory
import kotlin.wasm.unsafe.withScopedMemoryAllocator

internal actual fun ByteArray.browserBytes(): JsAny {
    val bytes = newByteView(size)
    if (isEmpty()) return bytes

    // Copy across the Wasm/JS boundary in bulk, using at most one Wasm page of scratch space.
    withScopedMemoryAllocator { allocator ->
        val capacity = minOf(size, 64 * 1024)
        val scratch = allocator.allocate(capacity)
        var offset = 0
        while (offset < size) {
            val count = minOf(capacity, size - offset)
            for (index in 0 until count) (scratch + index).storeByte(this[offset + index])
            copyChunk(bytes, offset, wasmMemory, scratch.address.toInt(), count)
            offset += count
        }
    }
    return bytes
}

private fun newByteView(size: Int): JsAny = js("new Uint8Array(size)")

private fun copyChunk(
    bytes: JsAny,
    offset: Int,
    memory: JsAny,
    address: Int,
    count: Int,
): Unit = js("{ bytes.set(new Uint8Array(memory.buffer, address, count), offset); }")
