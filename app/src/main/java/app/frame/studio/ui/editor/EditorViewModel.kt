package app.frame.studio.ui.editor

import app.frame.studio.telemetry.AppEvents
import app.frame.studio.export.CompositionFactory
import app.frame.studio.media.ImportMessages
import app.frame.studio.media.VideoVerdict
import app.frame.studio.timeline.Transform
import app.frame.studio.timeline.OverlayLayout
import app.frame.studio.timeline.Keyframes
import app.frame.studio.domain.OverlayClip
import app.frame.studio.domain.CaptionStyle
import app.frame.studio.captions.CaptionPlanner
import app.frame.studio.captions.AutoCaptioner
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.frame.studio.FrameApp
import app.frame.studio.domain.AudioClip
import app.frame.studio.domain.AudioKind
import app.frame.studio.domain.CanvasAspect
import app.frame.studio.domain.ColorAdjust
import app.frame.studio.domain.Project
import app.frame.studio.domain.SceneEffect
import app.frame.studio.domain.SceneEffectType
import app.frame.studio.domain.TextOverlay
import app.frame.studio.effects.SceneEffects
import app.frame.studio.domain.TransitionType
import app.frame.studio.domain.VideoClip
import app.frame.studio.export.SocialPreset
import app.frame.studio.export.SocialPresets
import app.frame.studio.export.centerCrop
import app.frame.studio.audio.AudioPcmReader
import app.frame.studio.audio.BeatDetector
import app.frame.studio.timeline.AudioLayout
import app.frame.studio.timeline.BeatSync
import kotlin.math.roundToInt
import app.frame.studio.timeline.TimelineMath
import app.frame.studio.timeline.TimelineOps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

sealed interface Selection {
    data object None : Selection
    data class Video(val id: String) : Selection
    data class Audio(val id: String) : Selection
    data class Text(val id: String) : Selection
    data class Effect(val id: String) : Selection
    data class Overlay(val id: String) : Selection
}

class EditorViewModel(private val app: FrameApp, private val projectId: String) : ViewModel() {

    private val repository = app.repository
    private val probe = app.mediaProbe

    private val _project = MutableStateFlow(
        repository.get(projectId)?.let { it.copy(audioClips = AudioLayout.normalize(it.audioClips)) },
    )
    val project: StateFlow<Project?> = _project.asStateFlow()

    private val _selection = MutableStateFlow<Selection>(Selection.None)
    val selection: StateFlow<Selection> = _selection.asStateFlow()

    private val _missing = MutableStateFlow<Set<String>>(emptySet())
    /** Source URIs that can no longer be opened (deleted or permission revoked). */
    val missing: StateFlow<Set<String>> = _missing.asStateFlow()

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private val messageChannel = Channel<String>(Channel.BUFFERED)
    val messages = messageChannel.receiveAsFlow()

    /** While true, the preview shows the selected clip without its filter and grade. */
    private val _compareOriginal = MutableStateFlow(false)
    val compareOriginal: StateFlow<Boolean> = _compareOriginal.asStateFlow()

    fun setCompareOriginal(on: Boolean) {
        _compareOriginal.value = on
    }

    /** Output-timeline playhead, updated by the preview player. */
    private val _playheadMs = MutableStateFlow(0L)
    val playheadMs: StateFlow<Long> = _playheadMs.asStateFlow()

    private val undo = ArrayDeque<Project>()
    private val redo = ArrayDeque<Project>()
    private var lastCoalesceKey: String? = null
    private var saveJob: Job? = null

    init {
        checkSources()
        // Export settings are edited on the export screen; keep them so later saves don't revert them.
        viewModelScope.launch {
            repository.projects.collect { list ->
                val stored = list.firstOrNull { it.id == projectId } ?: return@collect
                val current = _project.value ?: return@collect
                if (stored.exportHeight != current.exportHeight ||
                    stored.exportFps != current.exportFps ||
                    stored.exportQuality != current.exportQuality ||
                    stored.aspect != current.aspect
                ) {
                    _project.value = current.copy(
                        exportHeight = stored.exportHeight,
                        exportFps = stored.exportFps,
                        exportQuality = stored.exportQuality,
                        aspect = stored.aspect,
                    )
                }
            }
        }
    }

    fun setPlayhead(ms: Long) {
        _playheadMs.value = ms.coerceIn(0, totalMs())
    }

    fun totalMs(): Long = _project.value?.let { TimelineMath.totalVideoMs(it.videoClips) } ?: 0

    fun select(selection: Selection) {
        _selection.value = selection
        lastCoalesceKey = null
    }

    /** Selects the video clip under the playhead (CapCut's "Edit" entry point). */
    fun selectClipAtPlayhead(): VideoClip? {
        val p = _project.value ?: return null
        val pos = TimelineMath.locateVideo(p.videoClips, _playheadMs.value)
            ?: if (p.videoClips.isNotEmpty()) TimelineMath.Position(p.videoClips.lastIndex, 0) else return null
        val clip = p.videoClips[pos.index]
        select(Selection.Video(clip.id))
        return clip
    }

    // ---- Editing core -------------------------------------------------------------------

    /**
     * Applies [transform] and records an undo step. Consecutive edits with the same
     * [coalesceKey] (for example, dragging one slider) collapse into a single undo step.
     */
    private fun edit(coalesceKey: String? = null, transform: (Project) -> Project) {
        val current = _project.value ?: return
        val next = transform(current)
        if (next == current) return
        if (coalesceKey == null || coalesceKey != lastCoalesceKey) {
            undo.addLast(current)
            if (undo.size > MAX_UNDO) undo.removeFirst()
            redo.clear()
        }
        lastCoalesceKey = coalesceKey
        _project.value = next
        afterChange()
    }

    private fun afterChange() {
        _canUndo.value = undo.isNotEmpty()
        _canRedo.value = redo.isNotEmpty()
        _playheadMs.value = _playheadMs.value.coerceIn(0, totalMs())
        val sel = _selection.value
        val p = _project.value
        if (p != null) {
            val exists = when (sel) {
                is Selection.Video -> p.videoClips.any { it.id == sel.id }
                is Selection.Audio -> p.audioClips.any { it.id == sel.id }
                is Selection.Text -> p.textOverlays.any { it.id == sel.id }
                is Selection.Effect -> p.effects.any { it.id == sel.id }
                is Selection.Overlay -> p.overlays.any { it.id == sel.id }
                Selection.None -> true
            }
            if (!exists) _selection.value = Selection.None
        }
        scheduleSave()
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(400)
            val p = _project.value ?: return@launch
            val saved = repository.save(p)
            // Tell the user once per failure streak; edits stay in memory and the next save retries.
            if (!saved && !saveFailureShown) messageChannel.send("Couldn't save. Your phone may be out of storage.")
            saveFailureShown = !saved
        }
    }

    private var saveFailureShown = false

    fun undo() {
        val current = _project.value ?: return
        val previous = undo.removeLastOrNull() ?: return
        redo.addLast(current)
        lastCoalesceKey = null
        _project.value = previous
        afterChange()
    }

    fun redo() {
        val current = _project.value ?: return
        val next = redo.removeLastOrNull() ?: return
        undo.addLast(current)
        lastCoalesceKey = null
        _project.value = next
        afterChange()
    }

    fun rename(name: String) = edit { it.copy(name = name.ifBlank { it.name }) }

    fun flush() {
        saveJob?.cancel()
        val p = _project.value ?: return
        // Application-scoped so leaving the screen never loses the last edit.
        app.applicationScope.launch { repository.save(p) }
    }

    override fun onCleared() {
        flush()
        super.onCleared()
    }

    // ---- Import -------------------------------------------------------------------------

    /** Adds videos and photos after the selected clip (or at the end). */
    fun addMedia(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _importing.value = true
            val clips = mutableListOf<VideoClip>()
            var failed = 0
            val refused = mutableListOf<String>()
            val muted = mutableListOf<String?>()
            for (uri in uris) {
                probe.persistPermission(uri)
                val refusedBefore = refused.size
                val clip = if (probe.isImage(uri)) {
                    // Oversized photos are downscaled once so editing and export stay light.
                    val name = probe.probeImage(uri)?.displayName.orEmpty()
                    app.imageNormalizer.normalize(uri)?.let { image ->
                        VideoClip(
                            id = newId(),
                            sourceUri = image.uri.toString(),
                            sourceDurationMs = VideoClip.MAX_IMAGE_MS,
                            trimEndMs = VideoClip.DEFAULT_IMAGE_MS,
                            width = image.width,
                            height = image.height,
                            displayName = name,
                            isImage = true,
                        )
                    }
                } else {
                    probe.probeVideo(uri)?.takeIf { it.durationMs >= 100 }?.let { info ->
                        val verdict = info.verdict
                        if (verdict is VideoVerdict.Unsupported) {
                            refused += verdict.message
                            return@let null
                        }
                        if (!info.audioSupported) muted += info.audioCodec
                        VideoClip(
                            id = newId(),
                            sourceUri = uri.toString(),
                            sourceDurationMs = info.durationMs,
                            trimEndMs = info.durationMs,
                            width = info.width,
                            height = info.height,
                            displayName = info.displayName,
                            volume = if (info.audioSupported) 1f else 0f,
                            needsProxy = verdict is VideoVerdict.Heavy,
                        )
                    }
                }
                if (clip == null) {
                    // Refused files already have a reason; only count the unreadable ones here.
                    if (refused.size == refusedBefore) failed++
                } else clips += clip
            }
            _importing.value = false
            if (failed > 0) messageChannel.send("$failed item${if (failed > 1) "s" else ""} couldn't be opened")
            if (refused.isNotEmpty()) AppEvents.importRefused("video", refused.size)
            ImportMessages.summary(refused)?.let { messageChannel.send(it) }
            if (muted.isNotEmpty()) messageChannel.send(ImportMessages.mutedAudio(muted.first()))
            if (clips.isEmpty()) return@launch
            edit { p ->
                val sel = _selection.value
                val insertAt = if (sel is Selection.Video) {
                    p.videoClips.indexOfFirst { it.id == sel.id }.let { if (it < 0) p.videoClips.size else it + 1 }
                } else {
                    p.videoClips.size
                }
                p.copy(videoClips = p.videoClips.toMutableList().apply { addAll(insertAt, clips) })
            }
            _selection.value = Selection.None
        }
    }

    fun addAudio(uris: List<Uri>, kind: AudioKind) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _importing.value = true
            val clips = mutableListOf<AudioClip>()
            var failed = 0
            val refused = mutableListOf<String>()
            var cursor = audioInsertPosition()
            for (uri in uris) {
                probe.persistPermission(uri)
                val info = probe.probeAudio(uri)
                if (info == null || info.durationMs < 100) {
                    failed++
                    continue
                }
                if (!info.supported) {
                    refused += "This phone can't play the sound in ${info.displayName.ifBlank { "this file" }}."
                    continue
                }
                clips += AudioClip(
                    id = newId(),
                    sourceUri = uri.toString(),
                    sourceDurationMs = info.durationMs,
                    trimEndMs = info.durationMs,
                    displayName = info.displayName,
                    startMs = cursor,
                    kind = kind,
                )
                cursor += info.durationMs
            }
            _importing.value = false
            if (failed > 0) messageChannel.send("$failed file${if (failed > 1) "s" else ""} couldn't be opened as audio")
            if (refused.isNotEmpty()) AppEvents.importRefused("audio", refused.size)
            ImportMessages.summary(refused)?.let { messageChannel.send(it) }
            if (clips.isEmpty()) return@launch
            edit { p -> p.copy(audioClips = p.audioClips + clips) }
            _selection.value = Selection.Audio(clips.first().id)
        }
    }

    /** Audio starts at the playhead, or at 0 when the playhead is at the end. */
    private fun audioInsertPosition(): Long {
        val total = totalMs()
        val playhead = _playheadMs.value
        return if (playhead >= total - 100) 0 else playhead
    }

    fun addVoiceover(file: File, startMs: Long) {
        viewModelScope.launch {
            val info = probe.probeAudio(Uri.fromFile(file))
            if (info == null || info.durationMs < 200) {
                file.delete()
                messageChannel.send("Recording was too short")
                return@launch
            }
            val clip = AudioClip(
                id = newId(),
                sourceUri = Uri.fromFile(file).toString(),
                sourceDurationMs = info.durationMs,
                trimEndMs = info.durationMs,
                displayName = "Voiceover",
                startMs = startMs,
                kind = AudioKind.VOICE,
            )
            edit { p -> p.copy(audioClips = p.audioClips + clip) }
            _selection.value = Selection.Audio(clip.id)
        }
    }

    /**
     * Adds text (or a sticker) at the playhead. Returns where the preview should park so the new
     * item is fully visible (just past its fade-in), or null if nothing was added.
     */
    fun addText(text: String, sticker: Boolean = false): Long? {
        val total = totalMs()
        if (total <= 0) return null
        val start = _playheadMs.value.coerceIn(0, (total - 500).coerceAtLeast(0))
        val overlay = TextOverlay(
            id = newId(),
            text = text,
            startMs = start,
            endMs = (start + 3_000).coerceAtMost(total.coerceAtLeast(start + 500)),
            y = if (sticker) 0.4f else 0.8f,
            background = !sticker,
            isSticker = sticker,
            bold = !sticker,
        )
        edit { p -> p.copy(textOverlays = p.textOverlays + overlay) }
        _selection.value = Selection.Text(overlay.id)
        // Fading items are invisible on their first frame; show them at full strength instead.
        return if (overlay.animate) minOf(start + REVEAL_AFTER_ADD_MS, overlay.endMs - 1) else start
    }

    private fun checkSources() {
        viewModelScope.launch {
            val p = _project.value ?: return@launch
            val uris = (p.videoClips.map { it.sourceUri } + p.audioClips.map { it.sourceUri } + p.overlays.map { it.sourceUri }).toSet()
            val missing = withContext(Dispatchers.IO) {
                uris.filterNot { probe.isReadable(Uri.parse(it)) }.toSet()
            }
            _missing.value = missing
            if (missing.isNotEmpty()) {
                messageChannel.send("${missing.size} source file${if (missing.size > 1) "s are" else " is"} no longer available")
            }
        }
    }

    fun removeMissing() {
        val gone = _missing.value
        edit { p ->
            p.copy(
                videoClips = p.videoClips.filterNot { it.sourceUri in gone },
                audioClips = p.audioClips.filterNot { it.sourceUri in gone },
            )
        }
        _missing.value = emptySet()
    }

    // ---- Video clip edits ----------------------------------------------------------------

    private fun editVideo(id: String, key: String? = null, transform: (VideoClip) -> VideoClip) =
        edit(key?.let { "$it:$id" }) { p ->
            p.copy(videoClips = p.videoClips.map { if (it.id == id) transform(it) else it })
        }

    private fun clampFades(clip: VideoClip): VideoClip {
        val half = clip.outputDurationMs() / 2
        return clip.copy(fadeInMs = clip.fadeInMs.coerceAtMost(half), fadeOutMs = clip.fadeOutMs.coerceAtMost(half))
    }

    fun trimVideo(id: String, startMs: Long, endMs: Long) =
        editVideo(id) { clamped -> clampFades(TimelineOps.trim(clamped, startMs, endMs)) }

    fun setImageDuration(id: String, ms: Long) = editVideo(id, "imageDuration") { clip ->
        clampFades(clip.copy(trimStartMs = 0, trimEndMs = ms.coerceIn(500, VideoClip.MAX_IMAGE_MS)))
    }

    fun setSpeed(id: String, speed: Float) = editVideo(id, "speed") {
        clampFades(it.copy(speed = speed.coerceIn(VideoClip.MIN_SPEED, VideoClip.MAX_SPEED)))
    }

    fun setVideoVolume(id: String, volume: Float) = editVideo(id, "volume") { it.copy(volume = volume.coerceIn(0f, 2f)) }
    fun setVideoFadeIn(id: String, ms: Long) = editVideo(id, "fadeIn") { it.copy(fadeInMs = ms.coerceIn(0, it.outputDurationMs() / 2)) }
    fun setVideoFadeOut(id: String, ms: Long) = editVideo(id, "fadeOut") { it.copy(fadeOutMs = ms.coerceIn(0, it.outputDurationMs() / 2)) }
    fun rotate(id: String) = editVideo(id) { TimelineOps.rotate(it) }
    fun flip(id: String) = editVideo(id) { it.copy(flipH = !it.flipH) }
    fun setCrop(id: String, ratio: Float?) = editVideo(id) { clip ->
        clip.copy(crop = ratio?.let { centerCrop(clip.width, clip.height, it) })
    }

    fun setFilter(id: String, filterId: String) = editVideo(id) { it.copy(filterId = filterId, filterIntensity = if (it.filterId == filterId) it.filterIntensity else 1f) }
    fun setFilterIntensity(id: String, value: Float) = editVideo(id, "filterIntensity") { it.copy(filterIntensity = value.coerceIn(0f, 1f)) }
    fun setAdjust(id: String, adjust: ColorAdjust) = editVideo(id, "adjust") { it.copy(adjust = adjust) }

    fun applyLookToAll(id: String) = edit { p ->
        val source = p.videoClips.firstOrNull { it.id == id } ?: return@edit p
        p.copy(
            videoClips = p.videoClips.map {
                it.copy(filterId = source.filterId, filterIntensity = source.filterIntensity, adjust = source.adjust)
            },
        )
    }

    fun setTransition(id: String, type: TransitionType, ms: Long) = editVideo(id, "transition") {
        it.copy(transition = type, transitionMs = ms.coerceIn(100, 2_000))
    }

    fun applyTransitionToAll(id: String) = edit { p ->
        val source = p.videoClips.firstOrNull { it.id == id } ?: return@edit p
        p.copy(
            crossfadeMs = 0,
            videoClips = p.videoClips.map { it.copy(transition = source.transition, transitionMs = source.transitionMs) },
        )
    }

    fun splitSelected() {
        val p = _project.value ?: return
        val playhead = _playheadMs.value
        when (val sel = _selection.value) {
            is Selection.Audio -> {
                val clip = p.audioClips.firstOrNull { it.id == sel.id } ?: return
                val offset = playhead - clip.startMs
                if (offset < TimelineOps.MIN_SPLIT_MS || clip.outputDurationMs() - offset < TimelineOps.MIN_SPLIT_MS) {
                    messageChannel.trySend("Move the playhead over the selected audio to split it")
                    return
                }
                val at = clip.trimStartMs + offset
                val left = clip.copy(id = newId(), trimEndMs = at)
                val right = clip.copy(id = newId(), trimStartMs = at, startMs = clip.startMs + offset, fadeInMs = 0)
                edit { cur ->
                    cur.copy(audioClips = cur.audioClips.flatMap { if (it.id == clip.id) listOf(left.copy(fadeOutMs = 0), right) else listOf(it) })
                }
                _selection.value = Selection.Audio(right.id)
            }
            is Selection.Overlay -> {
                val o = p.overlays.firstOrNull { it.id == sel.id } ?: return
                val offset = playhead - o.startMs
                if (offset < TimelineOps.MIN_SPLIT_MS || o.durationMs() - offset < TimelineOps.MIN_SPLIT_MS) {
                    messageChannel.trySend("Move the playhead over the selected overlay to split it")
                    return
                }
                val left = o.copy(trimEndMs = o.trimStartMs + offset, keyframes = Keyframes.retime(o.keyframes, 0, offset))
                val right = o.copy(
                    id = newId(), trimStartMs = o.trimStartMs + offset, startMs = playhead,
                    keyframes = Keyframes.retime(o.keyframes, offset, o.durationMs() - offset),
                )
                edit { cur -> cur.copy(overlays = cur.overlays.flatMap { if (it.id == o.id) listOf(left, right) else listOf(it) }) }
                _selection.value = Selection.Overlay(right.id)
            }
            is Selection.Effect -> {
                val effect = p.effects.firstOrNull { it.id == sel.id } ?: return
                if (playhead - effect.startMs < 100 || effect.endMs - playhead < 100) {
                    messageChannel.trySend("Move the playhead over the selected effect to split it")
                    return
                }
                val right = effect.copy(id = newId(), startMs = playhead)
                edit { cur ->
                    cur.copy(effects = cur.effects.flatMap { if (it.id == effect.id) listOf(it.copy(endMs = playhead), right) else listOf(it) })
                }
                _selection.value = Selection.Effect(right.id)
            }
            is Selection.Text -> {
                val overlay = p.textOverlays.firstOrNull { it.id == sel.id } ?: return
                if (playhead - overlay.startMs < 100 || overlay.endMs - playhead < 100) {
                    messageChannel.trySend("Move the playhead over the selected text to split it")
                    return
                }
                val (left, right) = CaptionPlanner.split(overlay, playhead, newId())
                edit { cur ->
                    cur.copy(textOverlays = cur.textOverlays.flatMap { if (it.id == overlay.id) listOf(left, right) else listOf(it) })
                }
                _selection.value = Selection.Text(right.id)
            }
            else -> {
                val pos = TimelineMath.locateVideo(p.videoClips, playhead) ?: return
                val clip = p.videoClips[pos.index]
                val before = p.videoClips.size
                edit { it.copy(videoClips = TimelineOps.splitVideo(it.videoClips, clip.id, pos.offsetInClipMs)) }
                val after = _project.value?.videoClips ?: return
                if (after.size == before) {
                    messageChannel.trySend("Too close to the edge of the clip to split")
                } else {
                    // Fresh ids keep repeated splits unique.
                    val a = after[pos.index].copy(id = newId(), fadeOutMs = 0, transition = TransitionType.NONE)
                    val b = after[pos.index + 1].copy(id = newId(), fadeInMs = 0)
                    _project.value = _project.value?.copy(
                        videoClips = after.toMutableList().apply {
                            set(pos.index, a)
                            set(pos.index + 1, b)
                        },
                    )
                    _selection.value = Selection.Video(b.id)
                    scheduleSave()
                }
            }
        }
    }

    fun duplicateVideo(id: String) = edit { p ->
        val index = p.videoClips.indexOfFirst { it.id == id }
        if (index < 0) p else p.copy(
            videoClips = p.videoClips.toMutableList().apply { add(index + 1, this[index].copy(id = newId())) },
        )
    }

    fun moveVideo(id: String, delta: Int) = edit { p ->
        val index = p.videoClips.indexOfFirst { it.id == id }
        p.copy(videoClips = TimelineOps.reorder(p.videoClips, index, index + delta))
    }

    fun deleteVideo(id: String) = edit { p -> p.copy(videoClips = TimelineOps.delete(p.videoClips, id)) }

    // ---- Audio clip edits ----------------------------------------------------------------

    private fun editAudio(id: String, key: String? = null, transform: (AudioClip) -> AudioClip) =
        edit(key?.let { "$it:$id" }) { p ->
            p.copy(audioClips = p.audioClips.map { if (it.id == id) transform(it) else it })
        }

    /** Moves and/or trims an audio clip; used by the timeline drag handles. */
    fun placeAudio(id: String, startMs: Long, trimStartMs: Long, trimEndMs: Long) = editAudio(id) { clip ->
        val ts = trimStartMs.coerceIn(0, clip.sourceDurationMs - TimelineOps.MIN_SPLIT_MS)
        val te = trimEndMs.coerceIn(ts + TimelineOps.MIN_SPLIT_MS, clip.sourceDurationMs)
        val trimmed = clip.copy(startMs = startMs.coerceAtLeast(0), trimStartMs = ts, trimEndMs = te)
        val half = trimmed.outputDurationMs() / 2
        trimmed.copy(fadeInMs = trimmed.fadeInMs.coerceAtMost(half), fadeOutMs = trimmed.fadeOutMs.coerceAtMost(half))
    }

    fun setAudioVolume(id: String, volume: Float) = editAudio(id, "volume") { it.copy(volume = volume.coerceIn(0f, 2f)) }
    fun setAudioFadeIn(id: String, ms: Long) = editAudio(id, "fadeIn") { it.copy(fadeInMs = ms.coerceIn(0, it.outputDurationMs() / 2)) }
    fun setAudioFadeOut(id: String, ms: Long) = editAudio(id, "fadeOut") { it.copy(fadeOutMs = ms.coerceIn(0, it.outputDurationMs() / 2)) }

    fun duplicateAudio(id: String) = edit { p ->
        val clip = p.audioClips.firstOrNull { it.id == id } ?: return@edit p
        p.copy(audioClips = p.audioClips + clip.copy(id = newId(), startMs = clip.endMs()))
    }

    fun deleteAudio(id: String) = edit { p -> p.copy(audioClips = p.audioClips.filter { it.id != id }) }

    // ---- Text overlays -----------------------------------------------------------------

    private fun editText(id: String, key: String? = null, transform: (TextOverlay) -> TextOverlay) =
        edit(key?.let { "$it:$id" }) { p ->
            p.copy(textOverlays = p.textOverlays.map { if (it.id == id) transform(it) else it })
        }

    fun setText(id: String, text: String) = editText(id, "text") { CaptionPlanner.retext(it, text) }

    fun setTextRange(id: String, startMs: Long, endMs: Long) = editText(id) {
        val total = totalMs()
        val start = startMs.coerceIn(0, (total - 100).coerceAtLeast(0))
        CaptionPlanner.retime(it, start, endMs.coerceIn(start + 100, total.coerceAtLeast(start + 100)))
    }

    fun setTextPosition(id: String, x: Float, y: Float) =
        editText(id, "position") { it.copy(x = x.coerceIn(0.03f, 0.97f), y = y.coerceIn(0.03f, 0.97f)) }

    fun styleText(id: String, transform: (TextOverlay) -> TextOverlay) = editText(id, "style", transform)

    fun duplicateText(id: String) = edit { p ->
        val o = p.textOverlays.firstOrNull { it.id == id } ?: return@edit p
        p.copy(textOverlays = p.textOverlays + o.copy(id = newId(), y = (o.y + 0.08f).coerceAtMost(0.95f)))
    }

    fun deleteText(id: String) = edit { p -> p.copy(textOverlays = p.textOverlays.filter { it.id != id }) }

    // ---- Overlays (picture-in-picture) ----------------------------------------------------

    /** Adds photos/videos above the main video at the playhead, each on a free lane. */
    fun addOverlays(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _importing.value = true
            var added: String? = null
            var blocked = 0
            val refused = mutableListOf<String>()
            val muted = mutableListOf<String?>()
            for (uri in uris) {
                probe.persistPermission(uri)
                val total = totalMs()
                val start = _playheadMs.value.coerceIn(0, (total - 300).coerceAtLeast(0))
                val clip: OverlayClip = (if (probe.isImage(uri)) {
                    val name = probe.probeImage(uri)?.displayName.orEmpty()
                    app.imageNormalizer.normalize(uri)?.let { img ->
                        OverlayClip(
                            id = newId(), sourceUri = img.uri.toString(), sourceDurationMs = VideoClip.MAX_IMAGE_MS,
                            trimEndMs = minOf(VideoClip.DEFAULT_IMAGE_MS, total - start), startMs = start,
                            width = img.width, height = img.height, isImage = true, displayName = name,
                        )
                    }
                } else {
                    probe.probeVideo(uri)?.takeIf { it.durationMs >= 300 }?.let { info ->
                        val verdict = info.verdict
                        if (verdict is VideoVerdict.Unsupported) {
                            refused += verdict.message
                            return@let null
                        }
                        if (!info.audioSupported) muted += info.audioCodec
                        OverlayClip(
                            id = newId(), sourceUri = uri.toString(), sourceDurationMs = info.durationMs,
                            trimEndMs = minOf(info.durationMs, total - start), startMs = start,
                            width = info.width, height = info.height, displayName = info.displayName,
                            volume = if (info.audioSupported) 1f else 0f,
                            needsProxy = verdict is VideoVerdict.Heavy,
                        )
                    }
                }) ?: continue
                val p = _project.value ?: break
                if (!OverlayLayout.fits(p.overlays, clip.startMs, clip.durationMs(), CompositionFactory.maxOverlayLanes)) {
                    blocked++
                    continue
                }
                // Stagger several new overlays so they don't sit exactly on top of each other.
                val shifted = clip.copy(x = 0.5f + 0.08f * (p.overlays.size % 3), y = 0.42f + 0.06f * (p.overlays.size % 3))
                edit { cur -> cur.copy(overlays = cur.overlays + shifted) }
                added = shifted.id
            }
            _importing.value = false
            if (refused.isNotEmpty()) AppEvents.importRefused("overlay", refused.size)
            ImportMessages.summary(refused)?.let { messageChannel.send(it) }
            if (muted.isNotEmpty()) messageChannel.send(ImportMessages.mutedAudio(muted.first()))
            if (blocked > 0) {
                val lanes = CompositionFactory.maxOverlayLanes
                messageChannel.send(
                    if (lanes == 1) "This phone shows one overlay at a time. Move the playhead to a free spot."
                    else "Up to $lanes overlays can play at the same time. Move the playhead to a free spot.",
                )
            }
            added?.let { _selection.value = Selection.Overlay(it) }
        }
    }

    private fun editOverlay(id: String, key: String? = null, transform: (OverlayClip) -> OverlayClip) =
        edit(key?.let { "$it:$id" }) { p -> p.copy(overlays = p.overlays.map { if (it.id == id) transform(it) else it }) }

    fun setOverlayOpacity(id: String, opacity: Float) = editOverlay(id, "opacity") { o ->
        val v = opacity.coerceIn(0f, 1f)
        if (o.keyframes.isEmpty()) o.copy(opacity = v) else {
            val at = (_playheadMs.value - o.startMs).coerceIn(0, o.durationMs())
            val t = Keyframes.at(Keyframes.base(o), o.keyframes, at)
            o.copy(keyframes = Keyframes.upsert(o.keyframes, at, t.copy(opacity = v)))
        }
    }

    fun setOverlayVolume(id: String, volume: Float) = editOverlay(id, "volume") { it.copy(volume = volume.coerceIn(0f, 2f)) }

    fun setOverlayFilter(id: String, filterId: String) = editOverlay(id) { it.copy(filterId = filterId) }

    /** Moves or trims an overlay on the timeline (from the overlay lane). */
    fun placeOverlay(id: String, startMs: Long, trimStartMs: Long, trimEndMs: Long) {
        val p = _project.value ?: return
        val o = p.overlays.firstOrNull { it.id == id } ?: return
        val total = totalMs()
        val start = startMs.coerceIn(0, (total - 100).coerceAtLeast(0))
        val ts = trimStartMs.coerceIn(0, o.sourceDurationMs - 100)
        val te = trimEndMs.coerceIn(ts + 100, minOf(o.sourceDurationMs, ts + (total - start)))
        if (!OverlayLayout.fits(p.overlays, start, te - ts, CompositionFactory.maxOverlayLanes, ignoreId = id)) {
            messageChannel.trySend("Too many overlays at that moment")
            return
        }
        editOverlay(id) {
            it.copy(
                startMs = start, trimStartMs = ts, trimEndMs = te,
                // Keyframes stay attached to the footage when the start is trimmed.
                keyframes = Keyframes.retime(it.keyframes, ts - it.trimStartMs, te - ts),
            )
        }
    }

    fun deleteOverlay(id: String) = edit { p -> p.copy(overlays = p.overlays.filter { it.id != id }) }

    fun duplicateOverlay(id: String) {
        val p = _project.value ?: return
        val o = p.overlays.firstOrNull { it.id == id } ?: return
        val copy = o.copy(id = newId(), x = (o.x + 0.06f).coerceAtMost(0.95f), y = (o.y + 0.06f).coerceAtMost(0.95f))
        if (!OverlayLayout.fits(p.overlays, copy.startMs, copy.durationMs(), CompositionFactory.maxOverlayLanes)) {
            messageChannel.trySend("No free overlay lane at that moment")
            return
        }
        edit { cur -> cur.copy(overlays = cur.overlays + copy) }
        _selection.value = Selection.Overlay(copy.id)
    }

    // ---- Moving objects and keyframes -----------------------------------------------------

    /** The selected object's transform at the playhead (what the on-preview handles show). */
    fun transformAtPlayhead(project: Project, selection: Selection, playheadMs: Long): Transform? = when (selection) {
        is Selection.Overlay -> project.overlays.firstOrNull { it.id == selection.id }?.let { o ->
            Keyframes.at(Keyframes.base(o), o.keyframes, (playheadMs - o.startMs).coerceIn(0, o.durationMs()))
        }
        is Selection.Text -> project.textOverlays.firstOrNull { it.id == selection.id }?.let { t ->
            Keyframes.at(Transform(t.x, t.y, 1f, t.rotation, t.opacity), t.keyframes, (playheadMs - t.startMs).coerceIn(0, t.endMs - t.startMs))
        }
        else -> null
    }

    /**
     * A move/resize/rotate from the preview handles. Static objects simply move; animated ones get
     * (or update) a keyframe at the playhead.
     */
    fun transformSelected(t: Transform) {
        val playhead = _playheadMs.value
        when (val sel = _selection.value) {
            is Selection.Overlay -> editOverlay(sel.id, "move") { o ->
                val clamped = t.copy(x = t.x.coerceIn(0f, 1f), y = t.y.coerceIn(0f, 1f), scale = t.scale.coerceIn(0.05f, 3f))
                val at = (playhead - o.startMs).coerceIn(0, o.durationMs())
                val (base, keys) = Keyframes.edit(Keyframes.base(o), o.keyframes, at, clamped)
                o.copy(x = base.x, y = base.y, scale = base.scale, rotation = base.rotation, opacity = base.opacity, keyframes = keys)
            }
            is Selection.Text -> editText(sel.id, "move") { o ->
                val clamped = t.copy(x = t.x.coerceIn(0.02f, 0.98f), y = t.y.coerceIn(0.02f, 0.98f))
                if (o.keyframes.isEmpty()) {
                    // Static text: resizing changes its size directly.
                    o.copy(x = clamped.x, y = clamped.y, rotation = clamped.rotation, scale = (o.scale * clamped.scale).coerceIn(0.3f, 4f))
                } else {
                    val at = (playhead - o.startMs).coerceIn(0, o.endMs - o.startMs)
                    o.copy(keyframes = Keyframes.upsert(o.keyframes, at, clamped.copy(scale = clamped.scale.coerceIn(0.1f, 6f))))
                }
            }
            else -> Unit
        }
    }

    /** True when the selected object has a keyframe at the playhead. */
    fun hasKeyframeAtPlayhead(project: Project, selection: Selection, playheadMs: Long): Boolean = when (selection) {
        is Selection.Overlay -> project.overlays.firstOrNull { it.id == selection.id }?.let { Keyframes.nearest(it.keyframes, playheadMs - it.startMs) != null } == true
        is Selection.Text -> project.textOverlays.firstOrNull { it.id == selection.id }?.let { Keyframes.nearest(it.keyframes, playheadMs - it.startMs) != null } == true
        else -> false
    }

    /** Adds a keyframe of the current placement at the playhead, or removes the one there. */
    fun toggleKeyframe() {
        val p = _project.value ?: return
        val sel = _selection.value
        val playhead = _playheadMs.value
        val t = transformAtPlayhead(p, sel, playhead) ?: return
        when (sel) {
            is Selection.Overlay -> editOverlay(sel.id) { o ->
                val at = (playhead - o.startMs).coerceIn(0, o.durationMs())
                if (Keyframes.nearest(o.keyframes, at) != null) o.copy(keyframes = Keyframes.remove(o.keyframes, at))
                else o.copy(keyframes = Keyframes.upsert(o.keyframes, at, t))
            }
            is Selection.Text -> editText(sel.id) { o ->
                val at = (playhead - o.startMs).coerceIn(0, o.endMs - o.startMs)
                if (Keyframes.nearest(o.keyframes, at) != null) o.copy(keyframes = Keyframes.remove(o.keyframes, at))
                else o.copy(keyframes = Keyframes.upsert(o.keyframes, at, t))
            }
            else -> return
        }
        val count = when (sel) {
            is Selection.Overlay -> _project.value?.overlays?.firstOrNull { it.id == sel.id }?.keyframes?.size
            is Selection.Text -> _project.value?.textOverlays?.firstOrNull { it.id == sel.id }?.keyframes?.size
            else -> null
        } ?: 0
        if (count == 1) messageChannel.trySend("Keyframe added. Move the playhead, then drag or pinch on the preview to animate.")
    }

    fun clearKeyframes() {
        when (val sel = _selection.value) {
            is Selection.Overlay -> editOverlay(sel.id) { it.copy(keyframes = emptyList()) }
            is Selection.Text -> editText(sel.id) { it.copy(keyframes = emptyList()) }
            else -> Unit
        }
    }

    // ---- Animated captions ----------------------------------------------------------------

    private val captioner by lazy { AutoCaptioner(app) }
    val autoCaptionsSupported: Boolean get() = captioner.isSupported

    private val _captioning = MutableStateFlow<Float?>(null)
    /** Progress of on-device transcription (0..1), or null when idle. */
    val captioning: StateFlow<Float?> = _captioning.asStateFlow()
    private var captionJob: Job? = null

    private val _captionsNeedPermission = Channel<Unit>(Channel.CONFLATED)
    val captionsNeedPermission = _captionsNeedPermission.receiveAsFlow()

    /** Listens to the clips and voiceovers on the device and adds word-by-word captions. */
    fun autoCaptions(style: CaptionStyle) {
        val p = _project.value ?: return
        if (captionJob?.isActive == true) return
        _captioning.value = 0f
        captionJob = viewModelScope.launch {
            try {
                when (val r = captioner.transcribe(p) { _captioning.value = it }) {
                    is AutoCaptioner.Result.Words -> {
                        replaceCaptions(CaptionPlanner.toOverlays(r.words, style, totalMs(), ::newId))
                        messageChannel.send("Added captions for ${r.words.size} words. Tap one to fix a word.")
                    }
                    AutoCaptioner.Result.NoSpeech -> messageChannel.send("No speech found in your clips or voiceovers")
                    AutoCaptioner.Result.NeedsPermission -> _captionsNeedPermission.send(Unit)
                    is AutoCaptioner.Result.Unavailable -> messageChannel.send(r.message)
                    is AutoCaptioner.Result.Failed -> messageChannel.send(r.message)
                }
            } finally {
                _captioning.value = null
            }
        }
    }

    fun cancelCaptions() {
        captionJob?.cancel()
    }

    /** Typed captions: the words are spread over the selected clip (or the whole video) and animated. */
    fun typedCaptions(text: String, style: CaptionStyle) {
        val p = _project.value ?: return
        val total = totalMs()
        if (total <= 0 || CaptionPlanner.tokens(text).isEmpty()) return
        val selected = (_selection.value as? Selection.Video)?.id
        val index = p.videoClips.indexOfFirst { it.id == selected }
        val (start, end) = if (index >= 0) {
            val s = TimelineMath.videoStarts(p.videoClips)[index]
            s to s + p.videoClips[index].outputDurationMs()
        } else 0L to total
        val words = CaptionPlanner.evenWords(text, start + 200, (end - 200).coerceAtLeast(start + 300))
        val overlays = CaptionPlanner.toOverlays(words, style, total, ::newId)
        edit { cur -> cur.copy(textOverlays = cur.textOverlays.filterNot { it.isCaption && it.startMs < end && it.endMs > start } + overlays) }
        _selection.value = Selection.None
    }

    private fun replaceCaptions(overlays: List<TextOverlay>) =
        edit { cur -> cur.copy(textOverlays = cur.textOverlays.filterNot { it.isCaption } + overlays) }

    /** Restyles every animated caption at once. */
    fun setCaptionStyle(style: CaptionStyle) = edit("captionStyle") { cur ->
        cur.copy(
            textOverlays = cur.textOverlays.map {
                if (it.isCaption) it.copy(captionStyle = style, background = style == CaptionStyle.PLAIN) else it
            },
        )
    }

    fun clearCaptions() = edit { cur -> cur.copy(textOverlays = cur.textOverlays.filterNot { it.isCaption }) }

    // ---- Beat sync ---------------------------------------------------------------------

    private val _detectingBeats = MutableStateFlow<String?>(null)
    /** Id of the audio clip whose beats are being detected, if any. */
    val detectingBeats: StateFlow<String?> = _detectingBeats.asStateFlow()

    /** Finds the beats of an audio clip on the device (no network) and stores them on the clip. */
    fun detectBeats(audioId: String) {
        val clip = _project.value?.audioClips?.firstOrNull { it.id == audioId } ?: return
        if (_detectingBeats.value != null) return
        _detectingBeats.value = audioId
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                // Up to 10 minutes of the used part of the song.
                val end = minOf(clip.trimEndMs, clip.trimStartMs + 600_000)
                AudioPcmReader.readMono(app, Uri.parse(clip.sourceUri), clip.trimStartMs, end)?.let { pcm ->
                    BeatDetector.detect(pcm.samples, pcm.sampleRate)?.let { r ->
                        r.copy(beatsMs = r.beatsMs.map { it + pcm.startMs })
                    }
                }
            }
            _detectingBeats.value = null
            if (result == null || result.beatsMs.size < 4) {
                messageChannel.send("Couldn't find a steady beat in this audio")
                return@launch
            }
            editAudio(audioId) { it.copy(beatsMs = result.beatsMs, bpm = result.bpm) }
            messageChannel.send("Found ${result.beatsMs.size} beats · ${result.bpm.roundToInt()} BPM")
        }
    }

    /** Re-times every video clip so cuts land on every [every]-th beat of the audio clip. */
    fun syncToBeats(audioId: String, every: Int) {
        val p = _project.value ?: return
        val clip = AudioLayout.normalize(p.audioClips).firstOrNull { it.id == audioId } ?: return
        val cuts = BeatSync.cutPoints(BeatSync.timelineBeats(clip), every)
        if (cuts.isEmpty()) return
        edit { it.copy(videoClips = BeatSync.retime(it.videoClips, cuts)) }
        messageChannel.trySend("Clips synced to the beat. Undo to go back.")
    }

    // ---- Scene effects -----------------------------------------------------------------

    fun addEffect(type: SceneEffectType) {
        val (start, end) = SceneEffects.placement(_playheadMs.value, totalMs()) ?: return
        val effect = SceneEffect(id = newId(), type = type, startMs = start, endMs = end)
        edit { p -> p.copy(effects = p.effects + effect) }
        _selection.value = Selection.Effect(effect.id)
    }

    private fun editEffect(id: String, key: String? = null, transform: (SceneEffect) -> SceneEffect) =
        edit(key?.let { "$it:$id" }) { p -> p.copy(effects = p.effects.map { if (it.id == id) transform(it) else it }) }

    fun setEffectType(id: String, type: SceneEffectType) = editEffect(id) { it.copy(type = type) }
    fun setEffectIntensity(id: String, value: Float) = editEffect(id, "intensity") { it.copy(intensity = value.coerceIn(0.05f, 1f)) }

    fun setEffectRange(id: String, startMs: Long, endMs: Long) = editEffect(id) {
        val total = totalMs()
        val start = startMs.coerceIn(0, (total - 100).coerceAtLeast(0))
        it.copy(startMs = start, endMs = endMs.coerceIn(start + 100, total.coerceAtLeast(start + 100)))
    }

    fun duplicateEffect(id: String) = edit { p ->
        val e = p.effects.firstOrNull { it.id == id } ?: return@edit p
        val length = e.endMs - e.startMs
        p.copy(effects = p.effects + e.copy(id = newId(), startMs = e.endMs, endMs = e.endMs + length))
    }

    fun deleteEffect(id: String) = edit { p -> p.copy(effects = p.effects.filter { it.id != id }) }

    // ---- Project -----------------------------------------------------------------------

    fun setAspect(aspect: CanvasAspect) = edit { it.copy(aspect = aspect) }

    /** One-tap Instagram format: canvas shape plus matching export settings. */
    fun applySocialPreset(preset: SocialPreset) = edit { SocialPresets.apply(it, preset, app.device::clampExportHeight) }

    fun deleteSelected() {
        when (val sel = _selection.value) {
            is Selection.Video -> deleteVideo(sel.id)
            is Selection.Audio -> deleteAudio(sel.id)
            is Selection.Text -> deleteText(sel.id)
            is Selection.Effect -> deleteEffect(sel.id)
            is Selection.Overlay -> deleteOverlay(sel.id)
            Selection.None -> Unit
        }
    }

    private fun newId() = UUID.randomUUID().toString().take(12)

    companion object {
        /** Past the text fade-in (CompositionFactory fades text over 250 ms). */
        const val REVEAL_AFTER_ADD_MS = 300L

        private const val MAX_UNDO = 100
    }
}
