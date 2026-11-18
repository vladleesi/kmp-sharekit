package dev.vladleesi.kmpsharekit

import java.io.IOException
import java.io.UncheckedIOException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.Paths

internal class StagedShareFiles(
    files: List<ShareFile>,
) {
    private val root = Paths.get(System.getProperty("java.io.tmpdir")).toAbsolutePath()
    private val batch = Files.createTempDirectory(root, "kmp-sharekit-files-")
    val paths: Array<String> =
        try {
            // The picker only reports handoff. Leave successful batches available for the receiving service.
            pruneExpiredBatches()
            files
                .mapIndexed { index, file ->
                    val directory = Files.createDirectory(batch.resolve(index.toString()))
                    Files.write(directory.resolve(file.name), file.bytes).toString()
                }.toTypedArray()
        } catch (error: Exception) {
            remove()
            throw error
        }

    fun remove() {
        deleteBatch(batch)
    }

    private fun pruneExpiredBatches() {
        val cutoff = System.currentTimeMillis() - 86_400_000L
        try {
            val owner = Files.getOwner(batch, NOFOLLOW_LINKS)
            Files.newDirectoryStream(root, "kmp-sharekit-files-*").use { folders ->
                for (folder in folders) {
                    if (folder != batch &&
                        Files.isDirectory(folder, NOFOLLOW_LINKS) &&
                        Files.getLastModifiedTime(folder, NOFOLLOW_LINKS).toMillis() < cutoff &&
                        Files.getOwner(folder, NOFOLLOW_LINKS) == owner
                    ) {
                        deleteBatch(folder)
                    }
                }
            }
        } catch (_: IOException) {
            // The temporary folder can be cleaned by the OS if an old batch cannot be inspected.
        } catch (_: UnsupportedOperationException) {
            // Some temporary filesystems do not expose owners.
        }
    }

    private fun deleteBatch(directory: Path) {
        try {
            Files.walk(directory).use { entries ->
                entries.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        } catch (_: IOException) {
            // The receiver or the OS may have a handle open.
        } catch (_: UncheckedIOException) {
            // The OS may have removed a temporary file during traversal.
        }
    }
}
