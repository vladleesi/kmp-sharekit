package dev.vladleesi.kmpsharekit

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.util.UUID

// Declared in the library manifest and instantiated by Android.
internal class ShareFileProvider : FileProvider() {
    override fun getType(uri: Uri): String? = uri.getQueryParameter("mime") ?: super.getType(uri)
}

internal fun shareFilesFromActivity(
    context: Context,
    request: ShareFilesRequest,
) {
    val root = File(context.cacheDir, "sharekit-files")
    val cutoff = System.currentTimeMillis() - 86_400_000L
    // Recipients can read the files after the chooser opens. Prune old batches on the next share.
    root.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.deleteRecursively() }
    val batch = File(root, UUID.randomUUID().toString())
    if (!batch.mkdirs()) throw IOException("Could not create the share cache directory.")
    try {
        val authority = "${context.packageName}.sharekit.files"
        val streams =
            request.files.mapIndexed { index, file ->
                val directory = File(batch, index.toString())
                if (!directory.mkdir()) throw IOException("Could not create a directory for the shared file.")
                val staged = File(directory, file.name)
                staged.writeBytes(file.bytes)
                FileProvider
                    .getUriForFile(context, authority, staged)
                    .buildUpon()
                    .appendQueryParameter("mime", file.mimeType.lowercase())
                    .build()
            }
        val types = request.files.map { it.mimeType.lowercase() }.distinct()
        val clipTypes = types.toTypedArray()
        val intent =
            Intent(if (streams.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
                type =
                    when {
                        types.size == 1 -> types.single()
                        types.distinctBy { it.substringBefore('/') }.size == 1 -> types.first().substringBefore('/') + "/*"
                        else -> "*/*"
                    }
                if (streams.size == 1) {
                    putExtra(Intent.EXTRA_STREAM, streams.single())
                } else {
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(streams))
                }
                clipData =
                    ClipData(null, clipTypes, ClipData.Item(streams.first())).apply {
                        streams.drop(1).forEach { addItem(ClipData.Item(it)) }
                    }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                request.text?.let { putExtra(Intent.EXTRA_TEXT, it) }
                request.title?.let {
                    putExtra(Intent.EXTRA_SUBJECT, it)
                    putExtra(Intent.EXTRA_TITLE, it)
                }
            }
        context.startActivity(Intent.createChooser(intent, null))
    } catch (error: Exception) {
        batch.deleteRecursively()
        throw error
    }
}
