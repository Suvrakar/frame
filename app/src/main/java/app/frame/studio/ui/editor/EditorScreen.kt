@file:OptIn(ExperimentalMaterial3Api::class)

package app.frame.studio.ui.editor

import androidx.compose.material3.FilterChip
import androidx.compose.material.icons.filled.LayersClear
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.outlined.Diamond
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.PictureInPicture
import app.frame.studio.export.OverlayGeometry
import app.frame.studio.timeline.Keyframes
import app.frame.studio.timeline.Transform
import app.frame.studio.effects.Filters
import app.frame.studio.domain.CaptionStyle
import app.frame.studio.pro.ProFeature
import app.frame.studio.ui.pro.rememberProGate
import app.frame.studio.ui.pro.ProGate
import android.Manifest
import android.content.pm.PackageManager
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn as AndroidOptIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Gradient
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.util.UnstableApi
import app.frame.studio.FrameApp
import app.frame.studio.data.VoiceRecorder
import app.frame.studio.domain.AudioKind
import app.frame.studio.domain.ColorAdjust
import app.frame.studio.domain.ExportHeight
import app.frame.studio.domain.Project
import app.frame.studio.domain.TextOverlay
import app.frame.studio.export.ExportPlan
import app.frame.studio.ui.common.TextInputDialog
import app.frame.studio.ui.common.formatDuration
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun EditorScreen(
    app: FrameApp,
    projectId: String,
    autoImport: Boolean,
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
    EditorContent(app, vm, current, autoImport, onBack, onExport)
}

@Composable
private fun EditorContent(
    app: FrameApp,
    vm: EditorViewModel,
    project: Project,
    autoImport: Boolean,
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
    var tool by remember { mutableStateOf<Tool?>(null) }
    var dpPerSecond by rememberSaveable { mutableFloatStateOf(48f) }

    // ---- Preview ----------------------------------------------------------------------------
    val controller = remember { PreviewController(context.applicationContext, app.device.previewHeight) }
    DisposableEffect(controller) {
        onDispose {
            controller.release()
            // Filmstrip decoders are only needed while the editor is open.
            app.applicationScope.launch { app.mediaProbe.releaseDecoders() }
        }
    }
    // Rebuild shortly after edits settle so slider drags stay smooth.
    val compareOriginal by vm.compareOriginal.collectAsStateWithLifecycle()
    LaunchedEffect(project, compareOriginal, selection) {
        // Before/after: preview the selected clip without its filter and grade.
        val shown = if (compareOriginal && selection is Selection.Video) {
            val id = (selection as Selection.Video).id
            project.copy(videoClips = project.videoClips.map { if (it.id == id) it.copy(filterId = "none", adjust = ColorAdjust()) else it })
        } else {
            project
        }
        if (controller.hasPlayer && !compareOriginal) kotlinx.coroutines.delay(app.device.previewRebuildDelayMs)
        controller.setProject(shown)
    }
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
    val proGate = rememberProGate(app.pro)
    var captionStyle by rememberSaveable { mutableStateOf(CaptionStyle.KARAOKE) }
    val captionMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.autoCaptions(captionStyle)
    }
    LaunchedEffect(vm) { vm.captionsNeedPermission.collect { captionMic.launch(Manifest.permission.RECORD_AUDIO) } }
    LaunchedEffect(controller.error) { controller.error?.let { snackbar.showSnackbar(it) } }

    // ---- Pickers ----------------------------------------------------------------------------
    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) {
        vm.addMedia(it)
    }
    val pickOverlay = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) {
        vm.addOverlays(it)
    }
    fun launchMediaPicker() =
        pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))

    var pendingAudioKind by remember { mutableStateOf(AudioKind.MUSIC) }
    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {
        vm.addAudio(it, pendingAudioKind)
        tool = null
    }
    var autoImportDone by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(autoImport) {
        if (autoImport && !autoImportDone && project.videoClips.isEmpty()) {
            autoImportDone = true
            launchMediaPicker()
        }
    }

    // ---- Voiceover --------------------------------------------------------------------------
    val recorder = remember { VoiceRecorder(context.applicationContext) }
    var recording by remember { mutableStateOf(false) }
    var recordStartMs by remember { mutableLongStateOf(0L) }
    var recordElapsed by remember { mutableLongStateOf(0L) }
    fun startRecording() {
        recordStartMs = controller.positionMs
        if (recorder.start()) {
            recording = true
            controller.setMuted(true)
            controller.play()
        } else {
            android.widget.Toast.makeText(context, "Microphone is unavailable", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    fun stopRecording() {
        if (!recording) return
        recording = false
        controller.pause()
        controller.setMuted(false)
        recorder.stop()?.let { vm.addVoiceover(it, recordStartMs) }
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording()
    }
    LaunchedEffect(recording) {
        while (recording) {
            recordElapsed = recorder.elapsedMs()
            withFrameMillis { }
        }
    }
    DisposableEffect(recorder) { onDispose { recorder.cancel() } }

    var showRename by remember { mutableStateOf(false) }
    var showAddText by remember { mutableStateOf(false) }

    BackHandler {
        when {
            recording -> stopRecording()
            tool != null -> tool = null
            selection != Selection.None -> vm.select(Selection.None)
            else -> {
                vm.flush()
                onBack()
            }
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            // ---- Top bar ----
            Row(
                Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { vm.flush(); onBack() }) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
                Text(
                    project.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).clickable { showRename = true }.padding(horizontal = 4.dp),
                )
                TextButton(onClick = { controller.pause(); vm.flush(); onExport() }, enabled = project.videoClips.isNotEmpty()) {
                    Text(project.exportHeight.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(
                    onClick = {
                        controller.pause()
                        vm.flush()
                        onExport()
                    },
                    enabled = project.videoClips.isNotEmpty() && missing.isEmpty(),
                    modifier = Modifier.padding(end = 8.dp),
                ) { Text("Export", fontWeight = FontWeight.SemiBold) }
            }
            if (importing) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (missing.isNotEmpty()) MissingBanner(count = missing.size, onRemove = vm::removeMissing)

            // ---- Preview ----
            Box(Modifier.fillMaxWidth().weight(1f).background(Color.Black)) {
                if (project.videoClips.isEmpty()) {
                    EmptyPreview(onAdd = ::launchMediaPicker)
                } else {
                    PreviewArea(controller, project, selection, onTransform = vm::transformSelected)
                }
            }

            // ---- Transport ----
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TimeLabel(controller, Modifier.weight(1f).padding(start = 8.dp))
                FilledTonalIconButton(onClick = controller::togglePlay, enabled = controller.totalMs > 0 && !recording) {
                    Icon(
                        if (controller.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (controller.isPlaying) "Pause" else "Play",
                    )
                }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = vm::undo, enabled = canUndo) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                    }
                    IconButton(onClick = vm::redo, enabled = canRedo) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                    }
                }
            }

            // ---- Timeline ----
            Timeline(
                app = app,
                project = project,
                controller = controller,
                selection = selection,
                missing = missing,
                dpPerSecond = dpPerSecond,
                onZoom = { dpPerSecond = (dpPerSecond * it).coerceIn(6f, 320f) },
                onSelect = {
                    tool = null
                    vm.select(it)
                },
                onTrimVideo = vm::trimVideo,
                onImageDuration = vm::setImageDuration,
                onPlaceAudio = vm::placeAudio,
                onTextRange = vm::setTextRange,
                onEffectRange = vm::setEffectRange,
                onPlaceOverlay = vm::placeOverlay,
                onTransition = { id ->
                    vm.select(Selection.Video(id))
                    tool = Tool.TRANSITION
                },
                onAddMedia = ::launchMediaPicker,
                compact = tool != null,
                modifier = Modifier.height(if (tool != null) COMPACT_TIMELINE_HEIGHT else timelineHeight(project)),
            )

            // ---- Bottom: tool panel or toolbar ----
            AnimatedContent(
                targetState = tool to selection,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "bottom",
            ) { (activeTool, sel) ->
                if (activeTool != null) {
                    ActivePanel(
                        app = app,
                        controller = controller,
                        proGate = proGate,
                        captionStyle = captionStyle,
                        onCaptionStyle = { captionStyle = it },
                        vm = vm,
                        project = project,
                        tool = activeTool,
                        selection = sel,
                        recording = recording,
                        recordElapsed = recordElapsed,
                        onClose = {
                            if (recording) stopRecording()
                            tool = null
                        },
                        onOpenTool = { tool = it },
                        onPickAudio = { kind ->
                            pendingAudioKind = kind
                            pickAudio.launch(arrayOf("audio/*"))
                        },
                        onToggleRecord = {
                            when {
                                recording -> stopRecording()
                                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                                    PackageManager.PERMISSION_GRANTED -> startRecording()
                                else -> micPermission.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                    )
                } else {
                    Toolbar(
                        project = project,
                        selection = sel,
                        vm = vm,
                        onTool = { tool = it },
                        onAddMedia = ::launchMediaPicker,
                        onAddText = { showAddText = true },
                        onAddOverlay = { pickOverlay.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
                        keyed = keyedAt(project, sel, controller.positionMs),
                    )
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 140.dp))
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

/** While a tool panel is open only the ruler and video track are shown, so the preview stays large. */
private val COMPACT_TIMELINE_HEIGHT = (22 + 56 + 10).dp

/** Press and hold to see the clip without its filter and grade. */
@Composable
private fun CompareButton(onCompare: (Boolean) -> Unit) {
    val current = androidx.compose.runtime.rememberUpdatedState(onCompare)
    DisposableEffect(Unit) { onDispose { current.value(false) } }
    Text(
        "Before",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(50))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitPointerEvent()
                        if (down.changes.any { it.pressed }) {
                            current.value(true)
                            do {
                                val event = awaitPointerEvent()
                            } while (event.changes.any { it.pressed })
                            current.value(false)
                        }
                    }
                }
            }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** Isolated so only this label recomposes on every frame during playback. */
@Composable
private fun TimeLabel(controller: PreviewController, modifier: Modifier) {
    Text(
        "${formatDuration(controller.positionMs)} / ${formatDuration(controller.totalMs)}",
        style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

private fun timelineHeight(project: Project) = run {
    val audioLanes = app.frame.studio.timeline.AudioLayout.lanes(project.audioClips).values.maxOrNull()?.plus(1) ?: 0
    val textLanes = (if (project.textOverlays.isEmpty()) 0 else 1) + (if (project.effects.isEmpty()) 0 else 1) +
        (if (project.overlays.isEmpty()) 0 else 1)
    (22 + 56 + 12 + (audioLanes + textLanes).coerceAtMost(3) * 36 + 8).dp
}

@Composable
private fun MissingBanner(count: Int, onRemove: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.errorContainer) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
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
        Box(
            Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable(onClick = onAdd),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text("Add videos and photos", style = MaterialTheme.typography.titleMedium, color = Color.White)
        Text("They'll appear on the timeline below", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
    }
}

@AndroidOptIn(UnstableApi::class)
@Composable
private fun PreviewArea(
    controller: PreviewController,
    project: Project,
    selection: Selection,
    onTransform: (Transform) -> Unit,
) {
    val outputSize = ExportPlan.outputSize(project.videoClips, ExportHeight.HD_720, project.aspect)
    val aspect = outputSize.width.toFloat() / outputSize.height
    Box(Modifier.fillMaxSize().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        BoxWithConstraints(
            Modifier
                .aspectRatio(aspect, matchHeightConstraintsFirst = aspect < 1f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = controller::togglePlay,
                ),
        ) {
            val frameW = constraints.maxWidth.toFloat()
            val frameH = constraints.maxHeight.toFloat()
            AndroidView(
                factory = { ctx ->
                    // A plain SurfaceView: frames are composited by the GPU, and the player feeding it
                    // runs on its own thread (see PreviewController).
                    SurfaceView(ctx).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        holder.addCallback(object : SurfaceHolder.Callback {
                            override fun surfaceCreated(holder: SurfaceHolder) = Unit
                            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                                controller.attachSurface(holder.surface, width, height)
                            }
                            override fun surfaceDestroyed(holder: SurfaceHolder) {
                                controller.detachSurface()
                            }
                        })
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            AnimatedVisibility(
                visible = !controller.isPlaying,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center),
            ) {
                Box(
                    Modifier.size(52.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                }
            }
            // Selected text, sticker or overlay: move, pinch and rotate it on the preview. Animated
            // objects get a keyframe at the playhead (the outline turns gold on a keyframe).
            val playhead = controller.positionMs
            val transform = transformOf(project, selection, playhead)
            val box = boxOf(project, selection, outputSize.width, outputSize.height)
            if (transform != null && box != null) {
                TransformFrame(
                    transform = transform,
                    boxFraction = box,
                    frameW = frameW,
                    frameH = frameH,
                    keyed = keyedAt(project, selection, playhead),
                    onCommit = onTransform,
                    onTap = controller::togglePlay,
                )
            }
        }
    }
}

/** The selected object's placement at [playheadMs], or null when it isn't on screen then. */
private fun transformOf(project: Project, selection: Selection, playheadMs: Long): Transform? = when (selection) {
    is Selection.Overlay -> project.overlays.firstOrNull { it.id == selection.id }
        ?.takeIf { playheadMs >= it.startMs && playheadMs < it.endMs() }
        ?.let { o -> Keyframes.at(Keyframes.base(o), o.keyframes, playheadMs - o.startMs) }
    is Selection.Text -> project.textOverlays.firstOrNull { it.id == selection.id }
        ?.takeIf { playheadMs >= it.startMs - 50 && playheadMs < it.endMs }
        ?.let { t -> Keyframes.at(Transform(t.x, t.y, 1f, t.rotation, t.opacity), t.keyframes, (playheadMs - t.startMs).coerceAtLeast(0)) }
    else -> null
}

/** The object's size at scale 1, in 0..1 frame units. */
private fun boxOf(project: Project, selection: Selection, canvasW: Int, canvasH: Int): Pair<Float, Float>? = when (selection) {
    is Selection.Overlay -> project.overlays.firstOrNull { it.id == selection.id }?.let { o ->
        OverlayGeometry.boxFraction(o.width, o.height, canvasW, canvasH, 1f)
    }
    is Selection.Text -> project.textOverlays.firstOrNull { it.id == selection.id }?.let { t ->
        // Approximate the rendered text box: 100px glyphs per 1080px of short side.
        val fontPx = minOf(canvasW, canvasH) * 100f / 1080f * t.scale * (if (t.isSticker) 2f else 1f)
        val longest = t.text.lines().maxOfOrNull { it.length } ?: 1
        val w = (fontPx * 0.58f * (longest + if (t.background) 2 else 0)).coerceIn(fontPx, canvasW.toFloat())
        val h = (fontPx * 1.25f * t.text.lines().size.coerceAtLeast(1)).coerceAtMost(canvasH.toFloat())
        (w / canvasW) to (h / canvasH)
    }
    else -> null
}

private fun keyedAt(project: Project, selection: Selection, playheadMs: Long): Boolean = when (selection) {
    is Selection.Overlay -> project.overlays.firstOrNull { it.id == selection.id }?.let { Keyframes.nearest(it.keyframes, playheadMs - it.startMs) != null } == true
    is Selection.Text -> project.textOverlays.firstOrNull { it.id == selection.id }?.let { Keyframes.nearest(it.keyframes, playheadMs - it.startMs) != null } == true
    else -> false
}

@Composable
private fun TextDragFrame(
    overlay: TextOverlay,
    frameW: Float,
    frameH: Float,
    onMove: (id: String, x: Float, y: Float) -> Unit,
) {
    val density = LocalDensity.current
    var dragOffset by remember(overlay.id, overlay.x, overlay.y) { mutableStateOf(Offset.Zero) }
    // Approximate the rendered text box: 100px glyphs per 1080px of short side.
    val fontPx = minOf(frameW, frameH) * 100f / 1080f * overlay.scale * (if (overlay.isSticker) 2f else 1f)
    val longestLine = overlay.text.lines().maxOfOrNull { it.length } ?: 1
    val boxW = (fontPx * 0.58f * (longestLine + if (overlay.background) 2 else 0)).coerceIn(fontPx, frameW)
    val boxH = (fontPx * 1.25f * overlay.text.lines().size.coerceAtLeast(1)).coerceAtMost(frameH)
    val cx = overlay.x * frameW + dragOffset.x
    val cy = overlay.y * frameH + dragOffset.y
    val latest = androidx.compose.runtime.rememberUpdatedState(Triple(overlay, frameW, frameH))
    with(density) {
        Box(
            Modifier
                .offset { IntOffset((cx - boxW / 2).roundToInt(), (cy - boxH / 2).roundToInt()) }
                .size(boxW.toDp(), boxH.toDp())
                .border(1.5.dp, Color.White, RoundedCornerShape(4.dp))
                .pointerInput(overlay.id) {
                    detectDragGestures(
                        onDragEnd = {
                            val (o, w, h) = latest.value
                            onMove(o.id, (o.x * w + dragOffset.x) / w, (o.y * h + dragOffset.y) / h)
                        },
                    ) { change, amount ->
                        change.consume()
                        dragOffset += amount
                    }
                },
        )
    }
}

// ---- Toolbars -------------------------------------------------------------------------------

@Composable
private fun ToolbarItem(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    Column(
        Modifier
            .width(68.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1)
    }
}

@Composable
private fun ToolbarRow(onBack: (() -> Unit)?, content: @Composable () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().height(76.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                Box(
                    Modifier
                        .padding(start = 4.dp)
                        .size(width = 40.dp, height = 56.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier.size(20.dp))
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp)) { content() }
        }
    }
}

@Composable
private fun Toolbar(
    project: Project,
    selection: Selection,
    vm: EditorViewModel,
    onTool: (Tool) -> Unit,
    onAddMedia: () -> Unit,
    onAddText: () -> Unit,
    onAddOverlay: () -> Unit,
    keyed: Boolean,
) {
    val hasClips = project.videoClips.isNotEmpty()
    when (selection) {
        Selection.None -> ToolbarRow(onBack = null) {
            ToolbarItem(Icons.Filled.ContentCut, "Edit", enabled = hasClips) { vm.selectClipAtPlayhead() }
            ToolbarItem(Icons.Filled.MusicNote, "Audio", enabled = hasClips) { onTool(Tool.AUDIO) }
            ToolbarItem(Icons.Filled.TextFields, "Text", enabled = hasClips) { onAddText() }
            ToolbarItem(Icons.Filled.PictureInPicture, "Overlay", enabled = hasClips) { onAddOverlay() }
            ToolbarItem(Icons.Filled.ClosedCaption, "Captions", enabled = hasClips) { onTool(Tool.CAPTIONS) }
            ToolbarItem(Icons.Filled.AcUnit, "Effects", enabled = hasClips) { onTool(Tool.EFFECTS) }
            ToolbarItem(Icons.Filled.EmojiEmotions, "Stickers", enabled = hasClips) { onTool(Tool.STICKERS) }
            ToolbarItem(Icons.Filled.AutoAwesome, "Filters", enabled = hasClips) {
                if (vm.selectClipAtPlayhead() != null) onTool(Tool.FILTERS)
            }
            ToolbarItem(Icons.Filled.Tune, "Grade", enabled = hasClips) {
                if (vm.selectClipAtPlayhead() != null) onTool(Tool.ADJUST)
            }
            ToolbarItem(Icons.Filled.AspectRatio, "Canvas", enabled = hasClips) { onTool(Tool.CANVAS) }
            ToolbarItem(Icons.Filled.AddPhotoAlternate, "Media") { onAddMedia() }
        }
        is Selection.Video -> {
            val index = project.videoClips.indexOfFirst { it.id == selection.id }
            val clip = project.videoClips.getOrNull(index) ?: return
            ToolbarRow(onBack = { vm.select(Selection.None) }) {
                ToolbarItem(Icons.Filled.ContentCut, "Split") { vm.splitSelected() }
                if (clip.isImage) {
                    ToolbarItem(Icons.Filled.Timer, "Duration") { onTool(Tool.DURATION) }
                } else {
                    ToolbarItem(Icons.Filled.Speed, "Speed") { onTool(Tool.SPEED) }
                    ToolbarItem(Icons.AutoMirrored.Filled.VolumeUp, "Volume") { onTool(Tool.VOLUME) }
                }
                ToolbarItem(Icons.Filled.AutoAwesome, "Filters") { onTool(Tool.FILTERS) }
                ToolbarItem(Icons.Filled.Tune, "Grade") { onTool(Tool.ADJUST) }
                ToolbarItem(Icons.Filled.Gradient, "Fade") { onTool(Tool.FADE) }
                ToolbarItem(Icons.Filled.Crop, "Crop") { onTool(Tool.CROP) }
                ToolbarItem(Icons.Filled.Rotate90DegreesCw, "Rotate") { vm.rotate(clip.id) }
                ToolbarItem(Icons.Filled.Flip, "Mirror") { vm.flip(clip.id) }
                if (index < project.videoClips.lastIndex) {
                    ToolbarItem(Icons.Filled.SwapHoriz, "Transition") { onTool(Tool.TRANSITION) }
                }
                ToolbarItem(Icons.Filled.ContentCopy, "Duplicate") { vm.duplicateVideo(clip.id) }
                ToolbarItem(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Move left", enabled = index > 0) { vm.moveVideo(clip.id, -1) }
                ToolbarItem(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Move right", enabled = index < project.videoClips.lastIndex) { vm.moveVideo(clip.id, 1) }
                ToolbarItem(Icons.Filled.Delete, "Delete") { vm.deleteVideo(clip.id) }
            }
        }
        is Selection.Audio -> ToolbarRow(onBack = { vm.select(Selection.None) }) {
            ToolbarItem(Icons.Filled.Equalizer, "Beat sync") { onTool(Tool.BEAT_SYNC) }
            ToolbarItem(Icons.Filled.ContentCut, "Split") { vm.splitSelected() }
            ToolbarItem(Icons.AutoMirrored.Filled.VolumeUp, "Volume") { onTool(Tool.VOLUME) }
            ToolbarItem(Icons.Filled.Gradient, "Fade") { onTool(Tool.FADE) }
            ToolbarItem(Icons.Filled.ContentCopy, "Duplicate") { vm.duplicateAudio(selection.id) }
            ToolbarItem(Icons.Filled.Delete, "Delete") { vm.deleteAudio(selection.id) }
        }
        is Selection.Effect -> ToolbarRow(onBack = { vm.select(Selection.None) }) {
            ToolbarItem(Icons.Filled.AcUnit, "Edit") { onTool(Tool.EFFECT_EDIT) }
            ToolbarItem(Icons.Filled.ContentCut, "Split") { vm.splitSelected() }
            ToolbarItem(Icons.Filled.ContentCopy, "Duplicate") { vm.duplicateEffect(selection.id) }
            ToolbarItem(Icons.Filled.Delete, "Delete") { vm.deleteEffect(selection.id) }
        }
        is Selection.Overlay -> {
            val o = project.overlays.firstOrNull { it.id == selection.id } ?: return
            ToolbarRow(onBack = { vm.select(Selection.None) }) {
                ToolbarItem(if (keyed) Icons.Filled.Diamond else Icons.Outlined.Diamond, if (keyed) "Remove key" else "Keyframe") { vm.toggleKeyframe() }
                ToolbarItem(Icons.Filled.Opacity, "Opacity") { onTool(Tool.OPACITY) }
                if (!o.isImage) ToolbarItem(Icons.AutoMirrored.Filled.VolumeUp, "Volume") { onTool(Tool.VOLUME) }
                ToolbarItem(Icons.Filled.AutoAwesome, "Filter") { onTool(Tool.OVERLAY_FILTER) }
                ToolbarItem(Icons.Filled.ContentCut, "Split") { vm.splitSelected() }
                ToolbarItem(Icons.Filled.ContentCopy, "Duplicate") { vm.duplicateOverlay(o.id) }
                if (o.keyframes.isNotEmpty()) ToolbarItem(Icons.Filled.LayersClear, "Clear keys") { vm.clearKeyframes() }
                ToolbarItem(Icons.Filled.Delete, "Delete") { vm.deleteOverlay(o.id) }
            }
        }
        is Selection.Text -> ToolbarRow(onBack = { vm.select(Selection.None) }) {
            ToolbarItem(Icons.Filled.Edit, "Edit") { onTool(Tool.TEXT_EDIT) }
            ToolbarItem(if (keyed) Icons.Filled.Diamond else Icons.Outlined.Diamond, if (keyed) "Remove key" else "Keyframe") { vm.toggleKeyframe() }
            ToolbarItem(Icons.Filled.Palette, "Style") { onTool(Tool.TEXT_STYLE) }
            ToolbarItem(Icons.Filled.ContentCut, "Split") { vm.splitSelected() }
            ToolbarItem(Icons.Filled.ContentCopy, "Duplicate") { vm.duplicateText(selection.id) }
            ToolbarItem(Icons.Filled.Delete, "Delete") { vm.deleteText(selection.id) }
        }
    }
}

@Composable
private fun ActivePanel(
    app: FrameApp,
    controller: PreviewController,
    proGate: ProGate,
    captionStyle: CaptionStyle,
    onCaptionStyle: (CaptionStyle) -> Unit,
    vm: EditorViewModel,
    project: Project,
    tool: Tool,
    selection: Selection,
    recording: Boolean,
    recordElapsed: Long,
    onClose: () -> Unit,
    onOpenTool: (Tool) -> Unit,
    onPickAudio: (AudioKind) -> Unit,
    onToggleRecord: () -> Unit,
) {
    val clip = (selection as? Selection.Video)?.let { s -> project.videoClips.firstOrNull { it.id == s.id } }
    val audio = (selection as? Selection.Audio)?.let { s -> project.audioClips.firstOrNull { it.id == s.id } }
    val text = (selection as? Selection.Text)?.let { s -> project.textOverlays.firstOrNull { it.id == s.id } }
    val effect = (selection as? Selection.Effect)?.let { s -> project.effects.firstOrNull { it.id == s.id } }
    val overlayClip = (selection as? Selection.Overlay)?.let { s -> project.overlays.firstOrNull { it.id == s.id } }

    val action: (@Composable () -> Unit)? = when {
        clip != null && (tool == Tool.FILTERS || tool == Tool.ADJUST) -> {
            {
                CompareButton(onCompare = vm::setCompareOriginal)
                TextButton(onClick = { vm.applyLookToAll(clip.id) }) { Text("Apply to all") }
            }
        }
        else -> null
    }
    ToolPanel(title = tool.title, onDone = onClose, action = action) {
        when (tool) {
            Tool.SPEED -> clip?.let { c -> SpeedPanel(c) { vm.setSpeed(c.id, it) } }
            Tool.DURATION -> clip?.let { c -> DurationPanel(c) { vm.setImageDuration(c.id, it) } }
            Tool.VOLUME -> when {
                overlayClip != null -> VolumePanel(overlayClip.volume) { vm.setOverlayVolume(overlayClip.id, it) }
                clip != null -> VolumePanel(clip.volume) { vm.setVideoVolume(clip.id, it) }
                audio != null -> VolumePanel(audio.volume) { vm.setAudioVolume(audio.id, it) }
            }
            Tool.FADE -> when {
                clip != null -> FadePanel(clip.fadeInMs, clip.fadeOutMs, clip.outputDurationMs(), { vm.setVideoFadeIn(clip.id, it) }, { vm.setVideoFadeOut(clip.id, it) })
                audio != null -> FadePanel(audio.fadeInMs, audio.fadeOutMs, audio.outputDurationMs(), { vm.setAudioFadeIn(audio.id, it) }, { vm.setAudioFadeOut(audio.id, it) })
            }
            Tool.FILTERS -> clip?.let { c ->
                val isPro by app.pro.isPro.collectAsStateWithLifecycle()
                var browsing by remember { mutableStateOf(false) }
                // Premium looks open the Pro sheet; an ad unlocks them for the rest of this session.
                val pick: (String) -> Unit = { id ->
                    if (Filters.isPro(id)) proGate.require(ProFeature.PREMIUM_FILTERS) { vm.setFilter(c.id, id) } else vm.setFilter(c.id, id)
                }
                FiltersPanel(app, c, isPro, pick, { vm.setFilterIntensity(c.id, it) }, onBrowse = { browsing = true })
                if (browsing) {
                    FilterBrowser(app, c, pick, { vm.setFilterIntensity(c.id, it) }, onClose = { browsing = false })
                }
            }
            Tool.ADJUST -> clip?.let { c -> AdjustPanel(c.adjust) { vm.setAdjust(c.id, it) } }
            Tool.CROP -> clip?.let { c -> CropPanel(c) { vm.setCrop(c.id, it) } }
            Tool.TRANSITION -> clip?.let { c ->
                TransitionPanel(c, { type, ms -> vm.setTransition(c.id, type, ms) }, { vm.applyTransitionToAll(c.id) })
            }
            Tool.CANVAS -> CanvasPanel(project, vm::setAspect, vm::applySocialPreset)
            Tool.AUDIO -> AddAudioPanel(
                onMusic = { onPickAudio(AudioKind.MUSIC) },
                onSound = { onPickAudio(AudioKind.SOUND) },
                onVoice = { onOpenTool(Tool.VOICE) },
            )
            Tool.VOICE -> VoicePanel(recording, recordElapsed, onToggleRecord)
            Tool.STICKERS -> StickersPanel { vm.addText(it, sticker = true) }
            Tool.TEXT_EDIT -> text?.let { o -> TextEditPanel(o) { vm.setText(o.id, it) } }
            Tool.TEXT_STYLE -> text?.let { o -> TextStylePanel(o) { transform -> vm.styleText(o.id, transform) } }
            Tool.BEAT_SYNC -> audio?.let { a ->
                val detecting by vm.detectingBeats.collectAsStateWithLifecycle()
                BeatSyncPanel(
                    clip = a,
                    detecting = detecting == a.id,
                    onDetect = { vm.detectBeats(a.id) },
                    onSync = { every ->
                        vm.syncToBeats(a.id, every)
                        onClose()
                    },
                )
            }
            Tool.OPACITY -> overlayClip?.let { o ->
                val at = controller.positionMs - o.startMs
                val current = Keyframes.at(Keyframes.base(o), o.keyframes, at.coerceIn(0, o.durationMs())).opacity
                ValueSlider("Opacity", "${(current * 100).roundToInt()}%", current, 0f..1f) { vm.setOverlayOpacity(o.id, it) }
                if (o.keyframes.isNotEmpty()) {
                    Text(
                        "Animated: this sets the opacity keyframe at the playhead.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            Tool.OVERLAY_FILTER -> overlayClip?.let { o ->
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Filters.all.filter { !it.pro || app.pro.isPro.value }.take(40).forEach { f ->
                        FilterChip(selected = o.filterId == f.id, onClick = { vm.setOverlayFilter(o.id, f.id) }, label = { Text(f.name) })
                    }
                }
            }
            Tool.CAPTIONS -> {
                val progress by vm.captioning.collectAsStateWithLifecycle()
                val captions = project.textOverlays.filter { it.isCaption }
                CaptionsPanel(
                    hasCaptions = captions.isNotEmpty(),
                    style = captions.firstOrNull()?.captionStyle ?: captionStyle,
                    autoSupported = vm.autoCaptionsSupported,
                    progress = progress,
                    onStyle = { s ->
                        onCaptionStyle(s)
                        vm.setCaptionStyle(s)
                    },
                    onAuto = {
                        proGate.require(ProFeature.ANIMATED_CAPTIONS) {
                            app.pro.consumePass(ProFeature.ANIMATED_CAPTIONS)
                            vm.autoCaptions(captions.firstOrNull()?.captionStyle ?: captionStyle)
                        }
                    },
                    onCancel = vm::cancelCaptions,
                    onTyped = { text ->
                        proGate.require(ProFeature.ANIMATED_CAPTIONS) {
                            app.pro.consumePass(ProFeature.ANIMATED_CAPTIONS)
                            vm.typedCaptions(text, captions.firstOrNull()?.captionStyle ?: captionStyle)
                        }
                    },
                    onClear = vm::clearCaptions,
                )
            }
            Tool.EFFECTS -> EffectsPanel(current = null) { type ->
                vm.addEffect(type)
                onClose()
            }
            Tool.EFFECT_EDIT -> effect?.let { e ->
                EffectEditPanel(e, { vm.setEffectType(e.id, it) }, { vm.setEffectIntensity(e.id, it) })
            }
        }
    }
}
