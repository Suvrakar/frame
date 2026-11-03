package app.frame.studio.ui.editor

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.frame.studio.FrameApp
import app.frame.studio.domain.AudioClip
import app.frame.studio.domain.Project
import app.frame.studio.domain.TextOverlay
import app.frame.studio.domain.VideoClip
import app.frame.studio.export.centerCrop
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
import java.util.UUID

sealed interface Selection {
    data object None : Selection
    data class Video(val id: String) : Selection
    data class Audio(val id: String) : Selection
    data class Text(val id: String) : Selection
}

class EditorViewModel(private val app: FrameApp, projectId: String) : ViewModel() {

    private val repository = app.repository
    private val probe = app.mediaProbe

    private val _project = MutableStateFlow(repository.get(projectId))
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
                if (stored.exportHeight != current.exportHeight) {
                    _project.value = current.copy(exportHeight = stored.exportHeight)
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
            _project.value?.let { repository.save(it) }
        }
    }

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

    fun addVideos(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _importing.value = true
            val clips = mutableListOf<VideoClip>()
            var failed = 0
            for (uri in uris) {
                probe.persistPermission(uri)
                val info = probe.probeVideo(uri)
                if (info == null || info.durationMs < 100) {
                    failed++
                    continue
                }
                clips += VideoClip(
                    id = newId(),
                    sourceUri = uri.toString(),
                    sourceDurationMs = info.durationMs,
                    trimEndMs = info.durationMs,
                    width = info.width,
                    height = info.height,
                    displayName = info.displayName,
                )
            }
            _importing.value = false
            if (failed > 0) messageChannel.send("$failed file${if (failed > 1) "s" else ""} couldn't be opened as video")
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
            _selection.value = Selection.Video(clips.first().id)
        }
    }

    fun addAudio(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _importing.value = true
            val clips = mutableListOf<AudioClip>()
            var failed = 0
            for (uri in uris) {
                probe.persistPermission(uri)
                val info = probe.probeAudio(uri)
                if (info == null || info.durationMs < 100) {
                    failed++
                    continue
                }
                clips += AudioClip(
                    id = newId(),
                    sourceUri = uri.toString(),
                    sourceDurationMs = info.durationMs,
                    trimEndMs = info.durationMs,
                    displayName = info.displayName,
                )
            }
            _importing.value = false
            if (failed > 0) messageChannel.send("$failed file${if (failed > 1) "s" else ""} couldn't be opened as audio")
            if (clips.isEmpty()) return@launch
            edit { p -> p.copy(audioClips = p.audioClips + clips) }
            _selection.value = Selection.Audio(clips.first().id)
        }
    }

    fun addText(text: String) {
        val total = totalMs()
        val start = _playheadMs.value.coerceIn(0, (total - 500).coerceAtLeast(0))
        val overlay = TextOverlay(
            id = newId(),
            text = text,
            startMs = start,
            endMs = (start + 3_000).coerceAtMost(total.coerceAtLeast(start + 500)),
        )
        edit { p -> p.copy(textOverlays = p.textOverlays + overlay) }
        _selection.value = Selection.Text(overlay.id)
    }

    private fun checkSources() {
        viewModelScope.launch {
            val p = _project.value ?: return@launch
            val uris = (p.videoClips.map { it.sourceUri } + p.audioClips.map { it.sourceUri }).toSet()
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

    fun trimVideo(id: String, startMs: Long, endMs: Long) =
        editVideo(id, "trim") { clip ->
            val trimmed = TimelineOps.trim(clip, startMs, endMs)
            val half = trimmed.outputDurationMs() / 2
            trimmed.copy(fadeInMs = trimmed.fadeInMs.coerceAtMost(half), fadeOutMs = trimmed.fadeOutMs.coerceAtMost(half))
        }

    fun setSpeed(id: String, speed: Float) = editVideo(id) { it.copy(speed = speed.coerceIn(0.5f, 2f)) }
    fun setVideoVolume(id: String, volume: Float) = editVideo(id, "volume") { it.copy(volume = volume.coerceIn(0f, 1f)) }
    fun setVideoFadeIn(id: String, ms: Long) = editVideo(id, "fadeIn") { it.copy(fadeInMs = ms.coerceIn(0, it.outputDurationMs() / 2)) }
    fun setVideoFadeOut(id: String, ms: Long) = editVideo(id, "fadeOut") { it.copy(fadeOutMs = ms.coerceIn(0, it.outputDurationMs() / 2)) }
    fun rotate(id: String) = editVideo(id) { TimelineOps.rotate(it) }
    fun setCrop(id: String, ratio: Float?) = editVideo(id) { clip ->
        clip.copy(crop = ratio?.let { centerCrop(clip.width, clip.height, it) })
    }

    fun splitSelected() {
        val p = _project.value ?: return
        val playhead = _playheadMs.value
        when (val sel = _selection.value) {
            is Selection.Audio -> {
                val pos = TimelineMath.locateAudio(p.audioClips, playhead)
                val clip = p.audioClips.getOrNull(pos?.index ?: -1)
                if (pos == null || clip == null || clip.id != sel.id) {
                    messageChannel.trySend("Move the playhead over the selected music clip to split it")
                    return
                }
                edit { it.copy(audioClips = TimelineOps.splitAudio(it.audioClips, sel.id, pos.offsetInClipMs)) }
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
                    _selection.value = Selection.Video(after[pos.index + 1].id)
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

    fun trimAudio(id: String, startMs: Long, endMs: Long) =
        editAudio(id, "trim") { TimelineOps.trimAudio(it, startMs, endMs) }

    fun setAudioVolume(id: String, volume: Float) = editAudio(id, "volume") { it.copy(volume = volume.coerceIn(0f, 1f)) }
    fun setAudioFadeIn(id: String, ms: Long) = editAudio(id, "fadeIn") { it.copy(fadeInMs = ms.coerceIn(0, it.outputDurationMs() / 2)) }
    fun setAudioFadeOut(id: String, ms: Long) = editAudio(id, "fadeOut") { it.copy(fadeOutMs = ms.coerceIn(0, it.outputDurationMs() / 2)) }

    fun moveAudio(id: String, delta: Int) = edit { p ->
        val index = p.audioClips.indexOfFirst { it.id == id }
        p.copy(audioClips = TimelineOps.reorder(p.audioClips, index, index + delta))
    }

    fun deleteAudio(id: String) = edit { p -> p.copy(audioClips = TimelineOps.deleteAudio(p.audioClips, id)) }

    // ---- Text overlays -----------------------------------------------------------------

    private fun editText(id: String, key: String? = null, transform: (TextOverlay) -> TextOverlay) =
        edit(key?.let { "$it:$id" }) { p ->
            p.copy(textOverlays = p.textOverlays.map { if (it.id == id) transform(it) else it })
        }

    fun setText(id: String, text: String) = editText(id, "text") { it.copy(text = text) }

    fun setTextRange(id: String, startMs: Long, endMs: Long) = editText(id, "range") {
        val total = totalMs()
        val start = startMs.coerceIn(0, (total - 100).coerceAtLeast(0))
        it.copy(startMs = start, endMs = endMs.coerceIn(start + 100, total.coerceAtLeast(start + 100)))
    }

    fun setTextPosition(id: String, x: Float, y: Float) =
        editText(id, "position") { it.copy(x = x.coerceIn(0.05f, 0.95f), y = y.coerceIn(0.05f, 0.95f)) }

    fun deleteText(id: String) = edit { p -> p.copy(textOverlays = p.textOverlays.filter { it.id != id }) }

    // ---- Project -----------------------------------------------------------------------

    fun setTransition(ms: Long) = edit("transition") { it.copy(crossfadeMs = ms.coerceIn(0, 2_000)) }

    fun deleteSelected() {
        when (val sel = _selection.value) {
            is Selection.Video -> deleteVideo(sel.id)
            is Selection.Audio -> deleteAudio(sel.id)
            is Selection.Text -> deleteText(sel.id)
            Selection.None -> Unit
        }
    }

    private fun newId() = UUID.randomUUID().toString().take(12)

    companion object {
        private const val MAX_UNDO = 100
    }
}
