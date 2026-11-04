package app.frame.studio.export

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import app.frame.studio.data.ProjectRepository
import app.frame.studio.domain.ExportRecord
import app.frame.studio.domain.Project
import app.frame.studio.timeline.TimelineMath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

sealed interface ExportState {
    data object Idle : ExportState
    data class Running(val projectId: String, val progress: Int?) : ExportState
    data class Saving(val projectId: String) : ExportState
    data class Done(val record: ExportRecord, val savedToGallery: Boolean) : ExportState
    data class Failed(val projectId: String, val message: String) : ExportState
}

/** Runs one export at a time. Lives for the whole process so it survives screen changes. */
@OptIn(UnstableApi::class)
class ExportManager(
    private val context: Context,
    private val repository: ProjectRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow<ExportState>(ExportState.Idle)
    val state: StateFlow<ExportState> = _state.asStateFlow()

    private var transformer: Transformer? = null
    private var progressJob: Job? = null
    private var outputFile: File? = null

    val isBusy: Boolean
        get() = _state.value is ExportState.Running || _state.value is ExportState.Saving

    /** Must be called on the main thread. */
    fun start(project: Project, saveToGallery: Boolean) {
        if (isBusy) return
        val composition = try {
            CompositionFactory.build(project)
        } catch (e: Exception) {
            _state.value = ExportState.Failed(project.id, e.message ?: "Could not prepare export")
            return
        }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(repository.exportsDir, "Frame_$stamp.mp4")
        outputFile = file
        _state.value = ExportState.Running(project.id, 0)

        val listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                finishTransform()
                onExportCompleted(project, file, exportResult, saveToGallery)
            }

            override fun onError(
                composition: Composition,
                exportResult: ExportResult,
                exportException: ExportException,
            ) {
                finishTransform()
                file.delete()
                Log.e(TAG, "Export failed", exportException)
                _state.value = ExportState.Failed(project.id, describe(exportException))
            }
        }

        val t = Transformer.Builder(context)
            .setVideoMimeType(ExportPolicy.videoMimeType)
            .setAudioMimeType(ExportPolicy.audioMimeType)
            .setEncoderFactory(
                DefaultEncoderFactory.Builder(context)
                    .setEnableFallback(true)
                    .setRequestedVideoEncoderSettings(
                        VideoEncoderSettings.Builder()
                            .setBitrate(
                                ExportPlan.bitrate(
                                    ExportPlan.outputSize(project.videoClips, project.exportHeight, project.aspect),
                                    project.exportFps,
                                    project.exportQuality,
                                ),
                            )
                            .build(),
                    )
                    .build(),
            )
            .addListener(listener)
            .build()
        transformer = t
        try {
            t.start(composition, file.absolutePath)
        } catch (e: Exception) {
            finishTransform()
            file.delete()
            _state.value = ExportState.Failed(project.id, e.message ?: "Export could not start")
            return
        }

        progressJob = scope.launch {
            val holder = ProgressHolder()
            while (isActive) {
                val current = transformer ?: break
                val progressState = current.getProgress(holder)
                val value = if (progressState == Transformer.PROGRESS_STATE_AVAILABLE) holder.progress else null
                val s = _state.value
                if (s is ExportState.Running) _state.value = s.copy(progress = value)
                delay(250)
            }
        }
    }

    fun cancel() {
        val running = _state.value as? ExportState.Running ?: return
        transformer?.cancel()
        finishTransform()
        outputFile?.delete()
        _state.value = ExportState.Failed(running.projectId, CANCELLED)
    }

    fun acknowledge() {
        if (!isBusy) _state.value = ExportState.Idle
    }

    private fun finishTransform() {
        progressJob?.cancel()
        progressJob = null
        transformer = null
    }

    private fun onExportCompleted(project: Project, file: File, result: ExportResult, saveToGallery: Boolean) {
        _state.value = ExportState.Saving(project.id)
        scope.launch {
            val galleryUri = if (saveToGallery) GallerySaver.save(context, file, file.name) else null
            val record = ExportRecord(
                id = UUID.randomUUID().toString(),
                projectId = project.id,
                filePath = file.absolutePath,
                createdAtEpochMs = System.currentTimeMillis(),
                height = ExportPolicy.outputHeight(project.exportHeight),
                projectName = project.name,
                durationMs = if (result.durationMs > 0) result.durationMs else TimelineMath.totalVideoMs(project.videoClips),
                sizeBytes = file.length(),
                galleryUri = galleryUri?.toString(),
            )
            repository.addExport(record)
            _state.value = ExportState.Done(record, savedToGallery = galleryUri != null)
        }
    }

    private fun describe(e: ExportException): String = when (e.errorCode) {
        ExportException.ERROR_CODE_IO_FILE_NOT_FOUND,
        ExportException.ERROR_CODE_IO_NO_PERMISSION,
        -> "A source file is no longer available. Re-import it and try again."
        ExportException.ERROR_CODE_ENCODER_INIT_FAILED,
        ExportException.ERROR_CODE_ENCODING_FORMAT_UNSUPPORTED,
        -> "This device can't encode at this resolution. Try 720p."
        ExportException.ERROR_CODE_DECODER_INIT_FAILED,
        ExportException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        -> "One of the clips uses a format this device can't decode."
        else -> "Export failed (${e.errorCodeName})."
    }

    companion object {
        private const val TAG = "ExportManager"
        const val CANCELLED = "Export cancelled"
    }
}
