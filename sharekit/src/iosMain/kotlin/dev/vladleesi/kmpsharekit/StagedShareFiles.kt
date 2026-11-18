@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package dev.vladleesi.kmpsharekit

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.create

internal class StagedShareFiles(
    files: List<ShareFile>,
) {
    private val manager = NSFileManager.defaultManager
    private val batch = NSTemporaryDirectory().trimEnd('/') + "/sharekit-" + NSUUID().UUIDString
    val urls: List<NSURL> =
        try {
            files.mapIndexed { index, file ->
                val directory = "$batch/$index"
                check(manager.createDirectoryAtPath(directory, true, null, null)) {
                    "Could not create a directory for the shared file."
                }
                val data =
                    if (file.bytes.isEmpty()) {
                        NSData()
                    } else {
                        file.bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = file.bytes.size.toULong()) }
                    }
                val path = "$directory/${file.name}"
                check(manager.createFileAtPath(path, data, null)) { "Could not write a shared file." }
                NSURL.fileURLWithPath(path)
            }
        } catch (error: Exception) {
            remove()
            throw error
        }

    fun remove() {
        manager.removeItemAtPath(batch, null)
    }
}
