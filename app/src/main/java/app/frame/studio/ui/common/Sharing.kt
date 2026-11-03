package app.frame.studio.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import app.frame.studio.BuildConfig
import app.frame.studio.domain.ExportRecord
import java.io.File

private fun exportUri(context: Context, record: ExportRecord): Uri? {
    val file = File(record.filePath)
    if (file.exists()) {
        return FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.files", file)
    }
    return record.galleryUri?.let(Uri::parse)?.takeIf { it.scheme == "content" }
}

fun shareExport(context: Context, record: ExportRecord) {
    val uri = exportUri(context, record) ?: run {
        Toast.makeText(context, "The exported file is no longer available", Toast.LENGTH_SHORT).show()
        return
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "video/mp4"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Share video"))
}

fun playExport(context: Context, record: ExportRecord) {
    val uri = exportUri(context, record) ?: run {
        Toast.makeText(context, "The exported file is no longer available", Toast.LENGTH_SHORT).show()
        return
    }
    val view = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "video/mp4")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(view)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No video player installed", Toast.LENGTH_SHORT).show()
    }
}
