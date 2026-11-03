package app.frame.studio.ui.export

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.frame.studio.FrameApp
import app.frame.studio.ads.BannerAd
import app.frame.studio.domain.ExportHeight
import app.frame.studio.domain.Project
import app.frame.studio.export.ExportManager
import app.frame.studio.export.ExportPlan
import app.frame.studio.export.ExportState
import app.frame.studio.export.GallerySaver
import app.frame.studio.timeline.TimelineMath
import app.frame.studio.ui.common.formatBytes
import app.frame.studio.ui.common.formatDuration
import app.frame.studio.ui.common.playExport
import app.frame.studio.ui.common.shareExport
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    app: FrameApp,
    projectId: String,
    onBack: () -> Unit,
    onOpenExports: () -> Unit,
) {
    val context = LocalContext.current
    val projects by app.repository.projects.collectAsStateWithLifecycle()
    val project = projects.firstOrNull { it.id == projectId }
    val state by app.exportManager.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var saveToGallery by rememberSaveable { mutableStateOf(true) }

    val relevant = when (val s = state) {
        is ExportState.Running -> s.projectId == projectId
        is ExportState.Saving -> s.projectId == projectId
        is ExportState.Done -> s.record.projectId == projectId
        is ExportState.Failed -> s.projectId == projectId
        ExportState.Idle -> false
    }
    val shownState = if (relevant) state else ExportState.Idle
    val busy = shownState is ExportState.Running || shownState is ExportState.Saving

    fun leave() {
        if (!busy) app.exportManager.acknowledge()
        onBack()
    }
    BackHandler { leave() }

    fun start(p: Project, gallery: Boolean) = app.exportManager.start(p, gallery)

    val legacyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        project?.let { start(it, saveToGallery && granted) }
    }

    fun startExport() {
        val p = project ?: return
        if (saveToGallery && GallerySaver.needsLegacyPermission() &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            legacyPermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            start(p, saveToGallery)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Export") },
                navigationIcon = {
                    IconButton(onClick = ::leave) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        bottomBar = { BannerAd() },
    ) { padding ->
        if (project == null) {
            Text("Project not found", modifier = Modifier.padding(padding).padding(24.dp))
            return@Scaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(project.name, style = MaterialTheme.typography.headlineSmall)
            val size = ExportPlan.outputSize(project.videoClips, project.exportHeight)
            Text(
                "${project.videoClips.size} clips · ${formatDuration(TimelineMath.totalVideoMs(project.videoClips))} · " +
                    "${size.width}×${size.height} · H.264/AAC MP4",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            when (val s = shownState) {
                ExportState.Idle -> Settings(
                    project = project,
                    saveToGallery = saveToGallery,
                    onSaveToGallery = { saveToGallery = it },
                    onHeight = { h -> scope.launch { app.repository.save(project.copy(exportHeight = h)) } },
                    onExport = ::startExport,
                )
                is ExportState.Running -> Progress(s.progress, onCancel = app.exportManager::cancel)
                is ExportState.Saving -> {
                    Text("Saving to your gallery…")
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                is ExportState.Done -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.size(8.dp))
                        Text("Export complete", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        buildString {
                            append(formatBytes(s.record.sizeBytes))
                            if (s.savedToGallery) append(" · Saved to Movies/Frame")
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = { shareExport(context, s.record) }, modifier = Modifier.fillMaxWidth()) { Text("Share") }
                    OutlinedButton(onClick = { playExport(context, s.record) }, modifier = Modifier.fillMaxWidth()) { Text("Play") }
                    OutlinedButton(
                        onClick = {
                            app.exportManager.acknowledge()
                            onOpenExports()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("All exports") }
                }
                is ExportState.Failed -> {
                    val cancelled = s.message == ExportManager.CANCELLED
                    if (!cancelled) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.size(8.dp))
                            Text("Export failed", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    Text(s.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(
                        onClick = {
                            app.exportManager.acknowledge()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (cancelled) "OK" else "Try again") }
                }
            }
        }
    }
}

@Composable
private fun Settings(
    project: Project,
    saveToGallery: Boolean,
    onSaveToGallery: (Boolean) -> Unit,
    onHeight: (ExportHeight) -> Unit,
    onExport: () -> Unit,
) {
    Text("Resolution", style = MaterialTheme.typography.titleSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = project.exportHeight == ExportHeight.HD_720,
            onClick = { onHeight(ExportHeight.HD_720) },
            label = { Text("720p") },
        )
        FilterChip(
            selected = project.exportHeight == ExportHeight.HD_1080,
            onClick = { onHeight(ExportHeight.HD_1080) },
            label = { Text("1080p") },
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Save to gallery", style = MaterialTheme.typography.titleSmall)
            Text(
                "Also copy the video to Movies/Frame",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = saveToGallery, onCheckedChange = onSaveToGallery)
    }
    Spacer(Modifier.height(8.dp))
    Button(onClick = onExport, modifier = Modifier.fillMaxWidth(), enabled = project.videoClips.isNotEmpty()) {
        Text("Export video")
    }
    Text(
        "No watermark. Keep Frame open while exporting.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Progress(progress: Int?, onCancel: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        if (progress != null) {
            Text("$progress%", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
        } else {
            CircularProgressIndicator()
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Exporting… keep Frame open until it finishes.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onCancel) { Text("Cancel") }
    }
}
