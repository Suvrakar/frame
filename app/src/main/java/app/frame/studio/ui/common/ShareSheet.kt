package app.frame.studio.ui.common

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import app.frame.studio.BuildConfig
import app.frame.studio.domain.ExportRecord
import app.frame.studio.export.ShareOptimizer
import app.frame.studio.export.SharePlan
import app.frame.studio.export.ShareTarget
import app.frame.studio.export.VideoFacts
import app.frame.studio.telemetry.Telemetry
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

private data class Installed(val target: ShareTarget, val pkg: String, val icon: ImageBitmap?)

/**
 * Share to the apps people post to, in the best quality each one keeps (see SharePlan): the
 * file is made upload-ready for that app when needed, then that app opens directly. "More apps"
 * sends the untouched original anywhere (Drive, Gmail, Telegram, Bluetooth…).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSheet(record: ExportRecord, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val optimizer = remember { ShareOptimizer(context.applicationContext) }
    val installed = remember { installedTargets(context) }
    val file = remember(record) { File(record.filePath).takeIf { it.exists() } }
    var facts by remember { mutableStateOf<VideoFacts?>(null) }
    androidx.compose.runtime.LaunchedEffect(file) { facts = file?.let { optimizer.facts(it) } }
    var working by remember { mutableStateOf<Pair<ShareTarget, Int>?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    var note by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = { job?.cancel(); onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding()) {
            Text("Share", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)) {
                Icon(Icons.Filled.HighQuality, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "Each app gets the version it keeps sharpest.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val busy = working
            if (busy != null) {
                Text("Preparing for ${busy.first.label}…", style = MaterialTheme.typography.titleSmall)
                LinearProgressIndicator(progress = { busy.second / 100f }, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp))
                TextButton(onClick = { job?.cancel(); working = null }) { Text("Cancel") }
            } else {
                val tiles = installed.map { it as Any } + "more"
                tiles.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { tile ->
                            if (tile is Installed) {
                                AppTile(tile.target.label, tile.icon, Modifier.weight(1f)) {
                                    if (file == null) {
                                        Toast.makeText(context, "The exported file is no longer available", Toast.LENGTH_SHORT).show()
                                        return@AppTile
                                    }
                                    note = facts?.let { f -> SharePlan.lengthNote(f, tile.target) } ?: tile.target.tip
                                    job = scope.launch {
                                        working = tile.target to 0
                                        val ready = optimizer.prepare(file, tile.target) { p -> working = tile.target to p }
                                        working = null
                                        Telemetry.event("share", "target" to tile.target.name.lowercase(), "optimized" to (ready != file))
                                        tile.target.tip?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                                        send(context, ready, tile.pkg)
                                        onDismiss()
                                    }
                                }
                            } else {
                                AppTile("More apps", null, Modifier.weight(1f)) {
                                    Telemetry.event("share", "target" to "more")
                                    shareExport(context, record)
                                    onDismiss()
                                }
                            }
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                note?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun AppTile(label: String, icon: ImageBitmap?, modifier: Modifier, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier.bounceClick(onClick = onClick)) {
        Box(
            Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.size(44.dp))
            else Icon(Icons.Filled.MoreHoriz, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
    }
}

/** Destinations installed on this phone (first matching package of each), with their own icons. */
private fun installedTargets(context: Context): List<Installed> {
    val pm = context.packageManager
    return ShareTarget.entries.mapNotNull { t ->
        val pkg = t.packages.firstOrNull { p -> runCatching { pm.getApplicationInfo(p, 0) }.isSuccess } ?: return@mapNotNull null
        val icon = runCatching { pm.getApplicationIcon(pkg).toBitmap(128, 128).asImageBitmap() }.getOrNull()
        Installed(t, pkg, icon)
    }
}

/** Opens [pkg]'s own share screen with [file]; falls back to the system chooser. */
private fun send(context: Context, file: File, pkg: String) {
    val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.files", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "video/mp4"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri("", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        setPackage(pkg)
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent.createChooser(intent.setPackage(null), "Share video"))
    }
}
