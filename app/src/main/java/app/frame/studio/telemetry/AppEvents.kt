package app.frame.studio.telemetry

/**
 * Every Analytics event the app sends, in one place (names are what you'll see in the Firebase
 * console). Keep parameters to counts, sizes and settings — never names, text or file paths.
 */
object AppEvents {
    fun projectCreated(source: String) = Telemetry.event("project_created", "source" to source)

    fun exportStarted(height: Int, fps: Int, clips: Int, overlays: Int, texts: Int, audioTracks: Int, durationSec: Long) =
        Telemetry.event(
            "export_started",
            "height" to height, "fps" to fps, "clips" to clips, "overlays" to overlays,
            "texts" to texts, "audio_tracks" to audioTracks, "duration_sec" to durationSec,
        )

    fun exportCompleted(height: Int, durationSec: Long, sizeMb: Long, tookSec: Long, retries: Int, savedToGallery: Boolean) =
        Telemetry.event(
            "export_completed",
            "height" to height, "duration_sec" to durationSec, "size_mb" to sizeMb,
            "took_sec" to tookSec, "retries" to retries, "saved_to_gallery" to savedToGallery,
        )

    fun exportFailed(errorCode: String, retries: Int) =
        Telemetry.event("export_failed", "error_code" to errorCode, "retries" to retries)

    fun exportCancelled() = Telemetry.event("export_cancelled")

    fun magicMovieCreated(media: Int, skipped: Int, withMusic: Boolean, vibe: String, length: String) =
        Telemetry.event(
            "magic_movie_created",
            "media" to media, "skipped" to skipped, "with_music" to withMusic, "vibe" to vibe, "length" to length,
        )

    fun magicMovieFailed(reason: String) = Telemetry.event("magic_movie_failed", "reason" to reason)

    fun templateUsed(templateId: String, skipped: Int) =
        Telemetry.event("template_used", "template_id" to templateId, "skipped" to skipped)

    /** Files the user picked that this phone can't play (kind = video, overlay, audio). */
    fun importRefused(kind: String, count: Int) =
        Telemetry.event("import_refused", "kind" to kind, "count" to count)

    fun proPurchaseStarted() = Telemetry.event("pro_purchase_started")

    fun proPurchased() = Telemetry.event("pro_purchased")
}
