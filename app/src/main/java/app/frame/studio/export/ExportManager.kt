package app.frame.studio.export

import app.frame.studio.telemetry.AppEvents
import app.frame.studio.telemetry.Telemetry
import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.common.util.Clock
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.transformer.AudioEncoderSettings
import androidx.media3.transformer.DefaultAssetLoaderFactory
import androidx.media3.transformer.DefaultDecoderFactory
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import app.frame.studio.data.ProjectRepository
import app.frame.studio.device.DeviceProfile
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
    private val device: DeviceProfile,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + app.frame.studio.BackgroundErrors)
    private val _state = MutableStateFlow<ExportState>(ExportState.Idle)
    val state: StateFlow<ExportState> = _state.asStateFlow()

    private var transformer: Transformer? = null
    private var progressJob: Job? = null
    private var outputFile: File? = null
    private var startedAtMs = 0L
    private var retries = 0

    val isBusy: Boolean
        get() = _state.value is ExportState.Running || _state.value is ExportState.Saving

    /** Must be called on the main thread. */
    fun start(requested: Project, saveToGallery: Boolean) {
        if (isBusy) return
        // Never ask the encoder for a size, frame rate or audio bitrate it can't produce.
        val height = device.clampExportHeight(requested.exportHeight)
        val size = ExportPlan.outputSize(requested.videoClips, height, requested.aspect)
        val project = requested.copy(
            exportHeight = height,
            exportFps = device.clampFrameRate(size.width, size.height, requested.exportFps),
            audioBitrateKbps = device.clampAudioBitrate(requested.audioBitrateKbps),
        )
        startedAtMs = android.os.SystemClock.elapsedRealtime()
        retries = 0
        AppEvents.exportStarted(
            height = ExportPolicy.outputHeight(project.exportHeight),
            fps = project.exportFps,
            clips = project.videoClips.size,
            overlays = project.overlays.size,
            texts = project.textOverlays.size,
            audioTracks = project.audioClips.size,
            durationSec = TimelineMath.totalVideoMs(project.videoClips) / 1000,
        )
        runExport(project, saveToGallery, attempt = 0)
    }

    /** One Transformer run; [attempt] counts automatic retries of transient pipeline failures. */
    private fun runExport(project: Project, saveToGallery: Boolean, attempt: Int) {
        val composition = try {
            CompositionFactory.build(project)
        } catch (e: Exception) {
            reportFailure("build", e, e.javaClass.simpleName)
            _state.value = ExportState.Failed(project.id, e.message ?: "Could not prepare export")
            return
        }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(repository.exportsDir, "Frame_$stamp.mp4")
        outputFile = file
        _state.value = ExportState.Running(project.id, 0)
        retries = attempt

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
                Log.e(TAG, "Export failed (attempt ${attempt + 1})", exportException)
                if (attempt < MAX_RETRIES && isTransient(project, exportException)) {
                    // Media3's multi-input compositor has a rare timing race with overlays; a fresh
                    // run almost always succeeds, so retry once before showing an error.
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        // Skip the retry if the user cancelled in the meantime.
                        if (_state.value is ExportState.Running) runExport(project, saveToGallery, attempt + 1)
                    }, RETRY_DELAY_MS) // let the failed pipeline finish releasing first
                    return
                }
                reportFailure("export", exportException, exportException.errorCodeName)
                _state.value = ExportState.Failed(project.id, describe(exportException))
            }
        }

        val t = newTransformer(context, device, project, listener)
        transformer = t
        try {
            t.start(composition, file.absolutePath)
        } catch (e: Exception) {
            finishTransform()
            file.delete()
            reportFailure("start", e, e.javaClass.simpleName)
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

    /** Failures worth one automatic retry: frame-processing errors and any error with overlays. */
    private fun isTransient(project: Project, e: ExportException): Boolean =
        e.errorCode == ExportException.ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED || CompositionFactory.hasOverlays(project)

    fun cancel() {
        val running = _state.value as? ExportState.Running ?: return
        transformer?.cancel()
        finishTransform()
        outputFile?.delete()
        AppEvents.exportCancelled()
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
            AppEvents.exportCompleted(
                height = record.height,
                durationSec = record.durationMs / 1000,
                sizeMb = record.sizeBytes / 1_000_000,
                tookSec = (android.os.SystemClock.elapsedRealtime() - startedAtMs) / 1000,
                retries = retries,
                savedToGallery = galleryUri != null,
            )
            _state.value = ExportState.Done(record, savedToGallery = galleryUri != null)
        }
    }

    /** A failed export is both an Analytics event and a Crashlytics non-fatal (with the cause). */
    private fun reportFailure(stage: String, e: Throwable, code: String) {
        AppEvents.exportFailed(code, retries)
        Telemetry.setKey("export_stage", stage)
        Telemetry.nonFatal("export", e)
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
        private const val MAX_RETRIES = 2
        /** A retry started immediately collided with the failed export's teardown. */
        private const val RETRY_DELAY_MS = 750L
        private const val TAG = "ExportManager"

        /**
         * The exporter used by the app (and by the device tests). Slow phones get a longer muxer
         * watchdog: a heavy timeline can legitimately take more than Media3's default 10 s to
         * produce a frame there, and the default would abort a healthy export.
         */
        /**
         * Decoders for export: if a hardware decoder can't be created (budget chips often allow
         * only a few at once) another one is tried instead of failing. Emulator-only "goldfish"
         * decoders, which stall on long timelines, are tried last.
         */
        private fun robustDecoderFactory(context: Context) = DefaultDecoderFactory.Builder(context)
            .setEnableDecoderFallback(true)
            .setMediaCodecSelector { mimeType, secure, tunneling ->
                MediaCodecUtil.getDecoderInfos(mimeType, secure, tunneling)
                    .sortedBy { if (it.name.contains("goldfish", ignoreCase = true)) 1 else 0 }
            }
            .build()

        fun newTransformer(
            context: Context,
            device: DeviceProfile,
            project: Project,
            listener: Transformer.Listener,
        ): Transformer = Transformer.Builder(context)
            .setVideoMimeType(ExportPolicy.videoMimeType)
            .setAudioMimeType(ExportPolicy.audioMimeType)
            .setMaxDelayBetweenMuxerSamplesMs(if (device.isLowEnd) 90_000 else 30_000)
            .setAssetLoaderFactory(
                DefaultAssetLoaderFactory(context, robustDecoderFactory(context), Clock.DEFAULT, null),
            )
            .setEncoderFactory(
                DefaultEncoderFactory.Builder(context)
                    .setEnableFallback(true)
                    .setRequestedAudioEncoderSettings(
                        AudioEncoderSettings.Builder().setBitrate(project.audioBitrateKbps * 1_000).build(),
                    )
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
        const val CANCELLED = "Export cancelled"
    }
}
