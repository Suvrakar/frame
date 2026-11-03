@file:OptIn(ExperimentalMaterial3Api::class)

package app.frame.studio.ui.editor

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn as AndroidOptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.frame.studio.FrameApp
import app.frame.studio.domain.AudioClip
import app.frame.studio.domain.Project
import app.frame.studio.domain.TextOverlay
import app.frame.studio.domain.VideoClip
import app.frame.studio.export.ExportPlan
import app.frame.studio.export.centerCrop
import app.frame.studio.timeline.TimelineMath
import app.frame.studio.timeline.TimelineOps
import app.frame.studio.ui.common.TextInputDialog
import app.frame.studio.ui.common.VideoThumbnail
import app.frame.studio.ui.common.formatDuration
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val DP_PER_SECOND = 48f
private val TRACK_HEIGHT = 52.dp

@Composable
fun EditorScreen(
    app: FrameApp,
    projectId: String,
    onBack: () -> Unit,
    onExport: () -> Unit,
) {
    val vm: EditorViewModel = viewModel(
        key = projectId,
        factory = viewModelFactory { initializer { EditorViewModel(app, projectId) } },
    )
    val project by vm.project.collectAsStateWithLifecycle()
    val current = project
    if (current == null) {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("This project no longer exists.")
                TextButton(onClick = onBack) { Text("Back") }
            }
        }
        return
    }
    EditorContent(app, vm, current, onBack, onExport)
}

@Composable
private fun EditorContent(
    app: FrameApp,
    vm: EditorViewModel,
    project: Project,
    onBack: () -> Unit,
    onExport: () -> Unit,
) {
    val context = LocalContext.current
    val selection by vm.selection.collectAsStateWithLifecycle()
    val missing by vm.missing.collectAsStateWithLifecycle()
    val importing by vm.importing.collectAsStateWithLifecycle()
    val canUndo by vm.canUndo.collectAsStateWithLifecycle()
    val canRedo by vm.canRedo.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    val controller = remember { PreviewController(context.applicationContext) }
    DisposableEffect(controller) { onDispose { controller.release() } }
    LaunchedEffect(project) { controller.setProject(project) }
    LaunchedEffect(controller) {
        while (true) {
            withFrameMillis { }
            controller.tick()
            vm.setPlayhead(controller.positionMs)
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                controller.pause()
                vm.flush()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(controller.error) { controller.error?.let { snackbar.showSnackbar(it) } }

    val pickVideos = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {
        vm.addVideos(it)
    }
    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {
        vm.addAudio(it)
    }
    var showRename by remember { mutableStateOf(false) }
    var showAddText by remember { mutableStateOf(false) }

    BackHandler {
        vm.flush()
        onBack()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { vm.flush(); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Text(
                        project.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { showRename = true },
                    )
                },
                actions = {
                    IconButton(onClick = vm::undo, enabled = canUndo) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                    }
                    IconButton(onClick = vm::redo, enabled = canRedo) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                    }
                    Button(
                        onClick = {
                            controller.pause()
                            vm.flush()
                            onExport()
                        },
                        enabled = project.videoClips.isNotEmpty() && missing.isEmpty(),
                        modifier = Modifier.padding(end = 8.dp),
                    ) { Text("Export") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).navigationBarsPadding()) {
            if (importing) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (missing.isNotEmpty()) {
                MissingBanner(count = missing.size, onRemove = vm::removeMissing)
            }
            Box(Modifier.fillMaxWidth().weight(1f).background(Color.Black)) {
                if (project.videoClips.isEmpty()) {
                    EmptyPreview(onAdd = { pickVideos.launch(arrayOf("video/*")) })
                } else {
                    PreviewArea(controller, project, selection, onSelectText = { vm.select(Selection.Text(it)) })
                }
            }
            Transport(controller)
            AddRow(
                onAddVideo = { pickVideos.launch(arrayOf("video/*")) },
                onAddMusic = { pickAudio.launch(arrayOf("audio/*")) },
                onAddText = { showAddText = true },
                textEnabled = project.videoClips.isNotEmpty(),
            )
            Timeline(app, project, controller, selection, missing, onSelect = vm::select)
            ToolPanel(vm, project, selection, controller)
        }
    }

    if (showRename) {
        TextInputDialog(
            title = "Rename project",
            initial = project.name,
            confirmLabel = "Rename",
            onDismiss = { showRename = false },
            onConfirm = {
                showRename = false
                vm.rename(it)
            },
        )
    }
    if (showAddText) {
        TextInputDialog(
            title = "Add text",
            initial = "",
            label = "Text",
            confirmLabel = "Add",
            singleLine = false,
            onDismiss = { showAddText = false },
            onConfirm = {
                showAddText = false
                vm.addText(it)
            },
        )
    }
}

@Composable
private fun MissingBanner(count: Int, onRemove: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.errorContainer) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$count source file${if (count > 1) "s were" else " was"} moved or deleted.",
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onRemove) { Text("Remove clips") }
        }
    }
}

@Composable
private fun EmptyPreview(onAdd: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.VideoLibrary, contentDescription = null, tint = Color.White, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(12.dp))
        Text("Start by adding your videos", color = Color.White)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onAdd) { Text("Add videos") }
    }
}

@AndroidOptIn(UnstableApi::class)
@Composable
private fun PreviewArea(
    controller: PreviewController,
    project: Project,
    selection: Selection,
    onSelectText: (String) -> Unit,
) {
    val outputSize = ExportPlan.outputSize(project.videoClips, project.exportHeight)
    val aspect = outputSize.width.toFloat() / outputSize.height
    val clip = project.videoClips.getOrNull(controller.currentClipIndex)
    val rotation = clip?.rotationDegrees ?: 0
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        BoxWithConstraints(
            Modifier.aspectRatio(aspect, matchHeightConstraintsFirst = aspect < 1f).clip(RoundedCornerShape(2.dp)),
        ) {
            val frameW = constraints.maxWidth.toFloat()
            val frameH = constraints.maxHeight.toFloat()
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        player = controller.videoPlayer
                    }
                },
                onRelease = { it.player = null },
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    rotationZ = rotation.toFloat()
                    if (rotation % 180 != 0 && size.width > 0 && size.height > 0) {
                        val s = min(size.width / size.height, size.height / size.width)
                        scaleX = s
                        scaleY = s
                    }
                },
            )
            // Crop guide (exact for un-rotated or 180° clips; crop is always applied on export).
            if (clip?.crop != null && rotation % 180 == 0 && clip.width > 0 && clip.height > 0) {
                CropGuide(clip, frameW, frameH)
            }
            // Fade preview.
            val dim = 1f - controller.brightness()
            if (dim > 0.001f) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))
            }
            // Text overlays, sized like the export (100px per 1080px of short side).
            val density = LocalDensity.current
            val shortSide = min(frameW, frameH)
            val fontSize = with(density) { (shortSide * 100f / 1080f).toSp() }
            project.textOverlays.forEach { overlay ->
                val visible = controller.positionMs in overlay.startMs until overlay.endMs
                val selected = selection is Selection.Text && selection.id == overlay.id
                if (visible || selected) {
                    OverlayText(overlay, frameW, frameH, fontSize, selected, onClick = { onSelectText(overlay.id) })
                }
            }
        }
    }
}

@Composable
private fun CropGuide(clip: VideoClip, frameW: Float, frameH: Float) {
    val crop = clip.crop ?: return
    val contentAspect = clip.width.toFloat() / clip.height
    val (cw, ch) = if (frameW / frameH > contentAspect) frameH * contentAspect to frameH else frameW to frameW / contentAspect
    val left = (frameW - cw) / 2 + crop.left * cw
    val top = (frameH - ch) / 2 + crop.top * ch
    val w = (crop.right - crop.left) * cw
    val h = (crop.bottom - crop.top) * ch
    val density = LocalDensity.current
    with(density) {
        Box(
            Modifier
                .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                .size(w.toDp(), h.toDp())
                .border(2.dp, Color.White.copy(alpha = 0.85f)),
        )
    }
}

@Composable
private fun OverlayText(
    overlay: TextOverlay,
    frameW: Float,
    frameH: Float,
    fontSize: androidx.compose.ui.unit.TextUnit,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Layout(
        content = {
            Text(
                overlay.text,
                color = Color.White,
                fontSize = fontSize,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.6f))
                    .then(if (selected) Modifier.border(1.dp, MaterialTheme.colorScheme.primary) else Modifier)
                    .clickable(onClick = onClick)
                    .padding(horizontal = 4.dp),
            )
        },
        modifier = Modifier.fillMaxSize(),
    ) { measurables, constraints ->
        val placeable = measurables.first().measure(constraints.copy(minWidth = 0, minHeight = 0))
        layout(constraints.maxWidth, constraints.maxHeight) {
            val x = (overlay.x * frameW - placeable.width / 2f).roundToInt()
                .coerceIn(0, max(0, constraints.maxWidth - placeable.width))
            val y = (overlay.y * frameH - placeable.height / 2f).roundToInt()
                .coerceIn(0, max(0, constraints.maxHeight - placeable.height))
            placeable.place(x, y)
        }
    }
}

@Composable
private fun Transport(controller: PreviewController) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = controller::togglePlay, enabled = controller.totalMs > 0) {
            Icon(
                if (controller.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (controller.isPlaying) "Pause" else "Play",
            )
        }
        Text(
            "${formatDuration(controller.positionMs)} / ${formatDuration(controller.totalMs)}",
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun AddRow(onAddVideo: () -> Unit, onAddMusic: () -> Unit, onAddText: () -> Unit, textEnabled: Boolean) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TextButton(onClick = onAddVideo) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text("Video")
        }
        TextButton(onClick = onAddMusic) {
            Icon(Icons.Outlined.MusicNote, contentDescription = null)
            Text("Music")
        }
        TextButton(onClick = onAddText, enabled = textEnabled) {
            Icon(Icons.Outlined.TextFields, contentDescription = null)
            Text("Text")
        }
    }
}

/** Scrubbing without fling keeps the playhead exactly under the finger. */
private object NoFling : FlingBehavior {
    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float = initialVelocity
}

@Composable
private fun Timeline(
    app: FrameApp,
    project: Project,
    controller: PreviewController,
    selection: Selection,
    missing: Set<String>,
    onSelect: (Selection) -> Unit,
) {
    val density = LocalDensity.current
    val pxPerMs = with(density) { DP_PER_SECOND.dp.toPx() } / 1000f
    val scroll = rememberScrollState()
    val dragged by scroll.interactionSource.collectIsDraggedAsState()
    val draggedState = rememberUpdatedState(dragged)
    val edgesState = rememberUpdatedState(TimelineMath.videoEdges(project.videoClips))

    // Follow playback / programmatic seeks.
    LaunchedEffect(controller.positionMs, dragged) {
        if (!dragged) scroll.scrollTo((controller.positionMs * pxPerMs).roundToInt())
    }
    // User scrubbing seeks the preview.
    LaunchedEffect(scroll) {
        snapshotFlow { scroll.value }.collect { value ->
            if (draggedState.value) {
                controller.pause()
                val raw = (value / pxPerMs).toLong()
                // Snap to clip edges within ~8dp.
                val thresholdMs = (8f * density.density / pxPerMs).toLong()
                controller.seekTo(TimelineOps.snap(raw, edgesState.value, thresholdMs))
            }
        }
    }

    fun widthOf(ms: Long): Dp = with(density) { max(ms * pxPerMs, 4f).toDp() }

    BoxWithConstraints(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer)) {
        val half = maxWidth / 2
        Column(
            Modifier
                .horizontalScroll(scroll, flingBehavior = NoFling)
                .padding(vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Video track
            Row {
                Spacer(Modifier.width(half))
                project.videoClips.forEach { clip ->
                    val selected = selection is Selection.Video && selection.id == clip.id
                    VideoTile(app, clip, widthOf(clip.outputDurationMs()), selected, clip.sourceUri in missing) {
                        onSelect(if (selected) Selection.None else Selection.Video(clip.id))
                    }
                }
                Spacer(Modifier.width(half))
            }
            // Music track
            Row(Modifier.height(32.dp)) {
                Spacer(Modifier.width(half))
                if (project.audioClips.isEmpty()) {
                    Text(
                        "No music",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                }
                project.audioClips.forEach { clip ->
                    val selected = selection is Selection.Audio && selection.id == clip.id
                    AudioTile(clip, widthOf(clip.outputDurationMs()), selected, clip.sourceUri in missing) {
                        onSelect(if (selected) Selection.None else Selection.Audio(clip.id))
                    }
                }
                Spacer(Modifier.width(half))
            }
            // Text track (lanes for overlapping overlays)
            val lanes = remember(project.textOverlays) { assignLanes(project.textOverlays) }
            val laneCount = (lanes.values.maxOrNull() ?: -1) + 1
            if (laneCount > 0) {
                val totalWidth = widthOf(TimelineMath.totalVideoMs(project.videoClips)) + half * 2
                Box(Modifier.width(totalWidth).height(28.dp * laneCount)) {
                    project.textOverlays.forEach { overlay ->
                        val selected = selection is Selection.Text && selection.id == overlay.id
                        val lane = lanes[overlay.id] ?: 0
                        Box(
                            Modifier
                                .offset(x = half + widthOf(overlay.startMs), y = 28.dp * lane)
                                .width(widthOf(overlay.endMs - overlay.startMs))
                                .height(24.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)) else Modifier)
                                .clickable { onSelect(if (selected) Selection.None else Selection.Text(overlay.id)) }
                                .padding(horizontal = 4.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(
                                overlay.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                }
            }
        }
        // Centered playhead
        Box(
            Modifier
                .align(Alignment.Center)
                .width(2.dp)
                .fillMaxHeight()
                .background(Color.White),
        )
    }
}

private fun assignLanes(overlays: List<TextOverlay>): Map<String, Int> {
    val laneEnds = mutableListOf<Long>()
    val result = mutableMapOf<String, Int>()
    overlays.sortedBy { it.startMs }.forEach { o ->
        val lane = laneEnds.indexOfFirst { it <= o.startMs }.let { if (it < 0) laneEnds.size.also { laneEnds += 0L } else it }
        laneEnds[lane] = o.endMs
        result[o.id] = lane
    }
    return result
}

@Composable
private fun VideoTile(
    app: FrameApp,
    clip: VideoClip,
    width: Dp,
    selected: Boolean,
    isMissing: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier
            .padding(horizontal = 1.dp)
            .width(width)
            .height(TRACK_HEIGHT)
            .clip(shape)
            .then(
                if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape)
                else Modifier,
            )
            .clickable(onClick = onClick),
    ) {
        VideoThumbnail(
            probe = app.mediaProbe,
            uri = if (isMissing) null else clip.sourceUri,
            atMs = clip.trimStartMs,
            rotationDegrees = clip.rotationDegrees,
            modifier = Modifier.fillMaxSize(),
        )
        if (isMissing) Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.error.copy(alpha = 0.5f)))
        Text(
            buildString {
                append(formatDuration(clip.outputDurationMs()))
                if (clip.speed != 1f) append(" · ${clip.speed}x")
            },
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 3.dp),
        )
    }
}

@Composable
private fun AudioTile(clip: AudioClip, width: Dp, selected: Boolean, isMissing: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier
            .padding(horizontal = 1.dp)
            .width(width)
            .fillMaxHeight()
            .clip(shape)
            .background(if (isMissing) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer)
            .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            clip.displayName.ifBlank { "Music" },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}

// ---- Tool panel -------------------------------------------------------------------------

@Composable
private fun ToolPanel(vm: EditorViewModel, project: Project, selection: Selection, controller: PreviewController) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 260.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            when (selection) {
                is Selection.Video -> project.videoClips.firstOrNull { it.id == selection.id }?.let {
                    VideoTools(vm, it, project.videoClips.indexOf(it), project.videoClips.size, controller)
                }
                is Selection.Audio -> project.audioClips.firstOrNull { it.id == selection.id }?.let {
                    AudioTools(vm, it, project.audioClips.indexOf(it), project.audioClips.size)
                }
                is Selection.Text -> project.textOverlays.firstOrNull { it.id == selection.id }?.let {
                    TextTools(vm, it, TimelineMath.totalVideoMs(project.videoClips))
                }
                Selection.None -> ProjectTools(vm, project)
            }
        }
    }
}

@Composable
private fun ActionButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        Icon(icon, contentDescription = null, tint = tint)
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

@Composable
private fun ActionRow(content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) { content() }
}

@Composable
private fun ToolTabs(tabs: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tabs.forEach { tab ->
            FilterChip(selected = tab == selected, onClick = { onSelect(tab) }, label = { Text(tab) })
        }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    enabled: Boolean = true,
) {
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value = value.coerceIn(range), onValueChange = onChange, valueRange = range, enabled = enabled)
    }
}

@Composable
private fun TrimSlider(sourceDurationMs: Long, startMs: Long, endMs: Long, onCommit: (Long, Long) -> Unit) {
    var range by remember(startMs, endMs, sourceDurationMs) { mutableStateOf(startMs.toFloat()..endMs.toFloat()) }
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text("Trim", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            Text(
                "${formatDuration(range.start.toLong())} – ${formatDuration(range.endInclusive.toLong())}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        RangeSlider(
            value = range,
            onValueChange = { new ->
                if (new.endInclusive - new.start >= TimelineOps.MIN_SPLIT_MS) range = new
            },
            valueRange = 0f..sourceDurationMs.toFloat().coerceAtLeast(1f),
            onValueChangeFinished = { onCommit(range.start.toLong(), range.endInclusive.toLong()) },
        )
    }
}

@Composable
private fun VideoTools(vm: EditorViewModel, clip: VideoClip, index: Int, count: Int, controller: PreviewController) {
    ActionRow {
        ActionButton(Icons.Filled.ContentCut, "Split") { vm.splitSelected() }
        ActionButton(Icons.Filled.Rotate90DegreesCw, "Rotate") { vm.rotate(clip.id) }
        ActionButton(Icons.Filled.ContentCopy, "Duplicate") { vm.duplicateVideo(clip.id) }
        ActionButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Move", enabled = index > 0) { vm.moveVideo(clip.id, -1) }
        ActionButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Move", enabled = index < count - 1) { vm.moveVideo(clip.id, 1) }
        ActionButton(Icons.Filled.Delete, "Delete") { vm.deleteVideo(clip.id) }
    }
    var tab by remember { mutableStateOf("Trim") }
    ToolTabs(listOf("Trim", "Speed", "Volume", "Fade", "Crop"), tab) { tab = it }
    Spacer(Modifier.height(4.dp))
    when (tab) {
        "Trim" -> TrimSlider(clip.sourceDurationMs, clip.trimStartMs, clip.trimEndMs) { s, e -> vm.trimVideo(clip.id, s, e) }
        "Speed" -> Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { s ->
                FilterChip(selected = clip.speed == s, onClick = { vm.setSpeed(clip.id, s) }, label = { Text("${s}x") })
            }
        }
        "Volume" -> LabeledSlider("Clip volume", "${(clip.volume * 100).roundToInt()}%", clip.volume, 0f..1f, { vm.setVideoVolume(clip.id, it) })
        "Fade" -> {
            val maxFade = (clip.outputDurationMs() / 2).coerceAtMost(5_000).toFloat().coerceAtLeast(1f)
            LabeledSlider("Fade in", formatDuration(clip.fadeInMs), clip.fadeInMs.toFloat(), 0f..maxFade, { vm.setVideoFadeIn(clip.id, it.toLong()) })
            LabeledSlider("Fade out", formatDuration(clip.fadeOutMs), clip.fadeOutMs.toFloat(), 0f..maxFade, { vm.setVideoFadeOut(clip.id, it.toLong()) })
        }
        "Crop" -> {
            val options = listOf<Pair<String, Float?>>("Original" to null, "1:1" to 1f, "4:5" to 0.8f, "9:16" to 9f / 16f, "16:9" to 16f / 9f)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { (label, ratio) ->
                    val selected = if (ratio == null) clip.crop == null else clip.crop == centerCrop(clip.width, clip.height, ratio)
                    FilterChip(selected = selected, onClick = { vm.setCrop(clip.id, ratio) }, label = { Text(label) })
                }
            }
            if (clip.width <= 0 || clip.height <= 0) {
                Text("Crop isn't available for this clip.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun AudioTools(vm: EditorViewModel, clip: AudioClip, index: Int, count: Int) {
    Text(clip.displayName.ifBlank { "Music" }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    ActionRow {
        ActionButton(Icons.Filled.ContentCut, "Split") { vm.splitSelected() }
        ActionButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Move", enabled = index > 0) { vm.moveAudio(clip.id, -1) }
        ActionButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Move", enabled = index < count - 1) { vm.moveAudio(clip.id, 1) }
        ActionButton(Icons.Filled.Delete, "Delete") { vm.deleteAudio(clip.id) }
    }
    TrimSlider(clip.sourceDurationMs, clip.trimStartMs, clip.trimEndMs) { s, e -> vm.trimAudio(clip.id, s, e) }
    LabeledSlider("Volume", "${(clip.volume * 100).roundToInt()}%", clip.volume, 0f..1f, { vm.setAudioVolume(clip.id, it) })
    val maxFade = (clip.outputDurationMs() / 2).coerceAtMost(10_000).toFloat().coerceAtLeast(1f)
    LabeledSlider("Fade in", formatDuration(clip.fadeInMs), clip.fadeInMs.toFloat(), 0f..maxFade, { vm.setAudioFadeIn(clip.id, it.toLong()) })
    LabeledSlider("Fade out", formatDuration(clip.fadeOutMs), clip.fadeOutMs.toFloat(), 0f..maxFade, { vm.setAudioFadeOut(clip.id, it.toLong()) })
    Text(
        "Music is trimmed automatically to the length of the video.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun TextTools(vm: EditorViewModel, overlay: TextOverlay, totalMs: Long) {
    var text by remember(overlay.id) { mutableStateOf(overlay.text) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = {
                if (it.length <= 200) {
                    text = it
                    if (it.isNotBlank()) vm.setText(overlay.id, it)
                }
            },
            label = { Text("Text") },
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { vm.deleteText(overlay.id) }) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete text")
        }
    }
    var range by remember(overlay.id, overlay.startMs, overlay.endMs) {
        mutableStateOf(overlay.startMs.toFloat()..overlay.endMs.toFloat())
    }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text("Shown", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
        Text(
            "${formatDuration(range.start.toLong())} – ${formatDuration(range.endInclusive.toLong())}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    RangeSlider(
        value = range,
        onValueChange = { if (it.endInclusive - it.start >= 100f) range = it },
        valueRange = 0f..totalMs.toFloat().coerceAtLeast(1f),
        onValueChangeFinished = { vm.setTextRange(overlay.id, range.start.toLong(), range.endInclusive.toLong()) },
    )
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Top" to 0.12f, "Middle" to 0.5f, "Bottom" to 0.85f).forEach { (label, y) ->
            FilterChip(
                selected = kotlin.math.abs(overlay.y - y) < 0.01f && kotlin.math.abs(overlay.x - 0.5f) < 0.01f,
                onClick = { vm.setTextPosition(overlay.id, 0.5f, y) },
                label = { Text(label) },
            )
        }
    }
    LabeledSlider("Horizontal", "${(overlay.x * 100).roundToInt()}%", overlay.x, 0.05f..0.95f, { vm.setTextPosition(overlay.id, it, overlay.y) })
    LabeledSlider("Vertical", "${(overlay.y * 100).roundToInt()}%", overlay.y, 0.05f..0.95f, { vm.setTextPosition(overlay.id, overlay.x, it) })
}

@Composable
private fun ProjectTools(vm: EditorViewModel, project: Project) {
    if (project.videoClips.isEmpty()) {
        Text(
            "Add videos to get started. Tap a clip on the timeline to edit it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Text("Tap a clip, music or text on the timeline to edit it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
    LabeledSlider(
        label = "Fade between clips",
        valueText = if (project.crossfadeMs == 0L) "Off" else formatDuration(project.crossfadeMs),
        value = project.crossfadeMs.toFloat(),
        range = 0f..2_000f,
        onChange = { vm.setTransition((it / 100).roundToInt() * 100L) },
        enabled = project.videoClips.size > 1,
    )
}
