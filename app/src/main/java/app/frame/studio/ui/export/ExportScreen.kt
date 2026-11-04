package app.frame.studio.ui.export

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.frame.studio.FrameApp
import app.frame.studio.ads.BannerAd
import app.frame.studio.domain.ExportHeight
import app.frame.studio.domain.ExportQuality
import app.frame.studio.domain.Project
import app.frame.studio.export.ExportManager
import app.frame.studio.export.ExportPlan
import app.frame.studio.export.ExportState
import app.frame.studio.export.GallerySaver
import app.frame.studio.timeline.TimelineMath
import app.frame.studio.ui.common.VideoThumbnail
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

    fun update(transform: (Project) -> Project) {
        val p = project ?: return
        scope.launch { app.repository.save(transform(p)) }
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
        val size = ExportPlan.outputSize(project.videoClips, project.exportHeight, project.aspect)
        val durationMs = TimelineMath.totalVideoMs(project.videoClips)
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Poster(app, project, size, shownState)
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(project.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    "${formatDuration(durationMs)} · ${size.width}×${size.height} · ${project.exportFps} fps",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            AnimatedContent(targetState = shownState, contentKey = { it::class }, label = "exportState") { target ->
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    when (val s = target) {
                        ExportState.Idle -> Settings(
                            project = project,
                            heights = app.device.supportedExportHeights,
                            size = size,
                            durationMs = durationMs,
                            saveToGallery = saveToGallery,
                            onSaveToGallery = { saveToGallery = it },
                            onHeight = { h -> update { it.copy(exportHeight = h) } },
                            onFps = { f -> update { it.copy(exportFps = f) } },
                            onQuality = { q -> update { it.copy(exportQuality = q) } },
                            onExport = ::startExport,
                        )
                        is ExportState.Running -> {
                            Text(
                                "Keep Frame open while your video is exported.",
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedButton(onClick = app.exportManager::cancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
                        }
                        is ExportState.Saving -> Text(
                            "Saving to your gallery…",
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        is ExportState.Done -> {
                            Text(
                                buildString {
                                    append(formatBytes(s.record.sizeBytes))
                                    if (s.savedToGallery) append(" · Saved to Movies/Frame")
                                },
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(onClick = { shareExport(context, s.record) }, modifier = Modifier.weight(1f).height(52.dp)) {
                                    Icon(Icons.Filled.Share, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Share")
                                }
                                OutlinedButton(onClick = { playExport(context, s.record) }, modifier = Modifier.weight(1f).height(52.dp)) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Play")
                                }
                            }
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
                            Text(s.message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
                            Button(onClick = { app.exportManager.acknowledge() }, modifier = Modifier.fillMaxWidth()) {
                                Text(if (cancelled) "OK" else "Try again")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Poster(app: FrameApp, project: Project, size: ExportPlan.Size, state: ExportState) {
    val first = project.videoClips.firstOrNull()
    val aspect = size.width.toFloat() / size.height
    Box(Modifier.fillMaxWidth().heightIn(max = 300.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .aspectRatio(aspect, matchHeightConstraintsFirst = aspect < 1f)
                .clip(RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center,
        ) {
            VideoThumbnail(
                probe = app.mediaProbe,
                uri = first?.sourceUri,
                atMs = first?.trimStartMs ?: 0,
                isImage = first?.isImage == true,
                rotationDegrees = first?.rotationDegrees ?: 0,
                maxSize = 640,
                modifier = Modifier.fillMaxSize(),
            )
            when (state) {
                is ExportState.Running, is ExportState.Saving -> {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))
                    val progress = (state as? ExportState.Running)?.progress
                    Box(contentAlignment = Alignment.Center) {
                        if (progress != null) {
                            CircularProgressIndicator(
                                progress = { progress / 100f },
                                modifier = Modifier.size(96.dp),
                                strokeWidth = 6.dp,
                                color = Color.White,
                                trackColor = Color.White.copy(alpha = 0.2f),
                            )
                            Text("$progress%", style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
                        } else {
                            CircularProgressIndicator(modifier = Modifier.size(96.dp), color = Color.White, strokeWidth = 6.dp)
                        }
                    }
                }
                is ExportState.Done -> {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
                    Icon(Icons.Filled.CheckCircle, contentDescription = "Done", tint = Color.White, modifier = Modifier.size(72.dp))
                }
                is ExportState.Failed -> {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
                    Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = Color.White, modifier = Modifier.size(64.dp))
                }
                ExportState.Idle -> Unit
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Settings(
    project: Project,
    heights: List<ExportHeight>,
    size: ExportPlan.Size,
    durationMs: Long,
    saveToGallery: Boolean,
    onSaveToGallery: (Boolean) -> Unit,
    onHeight: (ExportHeight) -> Unit,
    onFps: (Int) -> Unit,
    onQuality: (ExportQuality) -> Unit,
    onExport: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Segmented("Resolution", heights, heights.firstOrNull { it == project.exportHeight } ?: heights.last(), { it.label }, onHeight)
        Segmented("Frame rate", listOf(24, 30, 60), project.exportFps, { "$it" }, onFps)
        Segmented("Quality", ExportQuality.entries, project.exportQuality, { it.label }, onQuality)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Save to gallery", style = MaterialTheme.typography.titleSmall)
                Text("Also copy to Movies/Frame", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = saveToGallery, onCheckedChange = onSaveToGallery)
        }
    }
    val estimate = ExportPlan.estimateBytes(durationMs, size, project.exportFps, project.exportQuality)
    Button(
        onClick = onExport,
        enabled = project.videoClips.isNotEmpty(),
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Text("Export · about ${formatBytes(estimate)}", fontWeight = FontWeight.SemiBold)
    }
    Text(
        "No watermark. Higher settings take longer and need a capable device; Frame falls back automatically if the encoder can't keep up.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> Segmented(label: String, options: List<T>, selected: T, text: (T) -> String, onSelect: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    icon = {},
                ) { Text(text(option), maxLines = 1, style = MaterialTheme.typography.labelMedium) }
            }
        }
    }
}
