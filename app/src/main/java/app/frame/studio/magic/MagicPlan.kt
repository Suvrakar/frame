package app.frame.studio.magic

import app.frame.studio.domain.AudioClip
import app.frame.studio.domain.CanvasAspect
import app.frame.studio.domain.Project
import app.frame.studio.domain.SceneEffect
import app.frame.studio.domain.SceneEffectType
import app.frame.studio.domain.TextOverlay
import app.frame.studio.domain.TransitionType
import app.frame.studio.domain.VideoClip
import app.frame.studio.timeline.BeatSync

/** The look and pacing of a Magic Movie. */
enum class MagicVibe(
    val label: String,
    val emoji: String,
    val filterId: String,
    val transition: TransitionType,
    val transitionMs: Long,
    /** Beats each photo stays on screen. */
    val beatsPerPhoto: Int,
    /** Beats for each video moment. */
    val beatsPerVideo: Int,
    /** Beat length used when there's no music. */
    val defaultBeatMs: Long,
    val effect: SceneEffectType?,
    val effectIntensity: Float,
    /** True: the effect runs through the whole movie; false: only over the title. */
    val effectWholeMovie: Boolean,
) {
    UPBEAT("Upbeat", "⚡", "punch", TransitionType.FADE_WHITE, 160, 2, 4, 480, SceneEffectType.SPARKLE, 0.7f, false),
    CHILL("Chill", "🌿", "pastel", TransitionType.FADE_BLACK, 500, 4, 8, 600, SceneEffectType.BOKEH, 0.35f, true),
    CINEMATIC("Cinematic", "🎬", "teal", TransitionType.FADE_BLACK, 600, 4, 8, 650, SceneEffectType.LIGHT_LEAK, 0.6f, false),
    PARTY("Party", "🎉", "vivid", TransitionType.FADE_WHITE, 200, 2, 4, 460, SceneEffectType.CONFETTI, 0.8f, false),
    MEMORIES("Memories", "📼", "faded_film", TransitionType.FADE_BLACK, 500, 4, 8, 620, SceneEffectType.OLD_FILM, 0.45f, true),
}

enum class MagicLength(val label: String, val targetMs: Long?) {
    SHORT("15 s", 15_000),
    MEDIUM("30 s", 30_000),
    LONG("60 s", 60_000),
    ALL("All", null),
}

/** A picked photo or video, probed and scored on the device. */
data class MagicAsset(
    val uri: String,
    val isImage: Boolean,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val displayName: String,
    /** Capture time from EXIF / video metadata, if known. */
    val takenAtMs: Long?,
    /** 0..1 quality score. */
    val score: Float,
    val hash: Long,
    /** Order the user picked it in. */
    val pickIndex: Int,
    /** Video only: best moment (source ms). */
    val bestMs: Long = durationMs / 3,
    /** Video only: heavy source, preview should use a proxy. */
    val needsProxy: Boolean = false,
    /** Video only: the video's own sound can't be played on this phone. */
    val audioMuted: Boolean = false,
)

data class MagicMusic(
    val uri: String,
    val durationMs: Long,
    val displayName: String,
    val beatsMs: List<Long>,
    val bpm: Float,
)

/** Pure planner: picks the best shots and cuts them into a paced, beat-synced movie. */
object MagicPlan {

    const val DUPLICATE_DISTANCE = 6
    const val MIN_SHOT_MS = 900L
    const val MAX_PHOTO_MS = 4_000L
    const val MAX_CLIPS = 40

    /** Drops near-duplicates (bursts, retakes), keeping the best-scored shot of each group. */
    fun dedupe(assets: List<MagicAsset>, maxDistance: Int = DUPLICATE_DISTANCE): List<MagicAsset> {
        val kept = mutableListOf<MagicAsset>()
        assets.sortedByDescending { it.score }.forEach { a ->
            val dup = kept.any { it.isImage == a.isImage && ImageStats.distance(it.hash, a.hash) <= maxDistance }
            if (!dup) kept += a
        }
        return kept
    }

    /** Capture order when dates are known, otherwise the order the user picked. */
    fun chronological(assets: List<MagicAsset>): List<MagicAsset> =
        assets.sortedWith(compareBy<MagicAsset>({ it.takenAtMs ?: Long.MAX_VALUE }, { it.pickIndex }))

    /** Typical beat interval of the music, or the vibe's default pace. */
    fun beatMs(music: MagicMusic?, vibe: MagicVibe): Long {
        val beats = music?.beatsMs.orEmpty().sorted()
        if (beats.size >= 4) {
            val gaps = beats.zipWithNext { a, b -> b - a }.sorted()
            val median = gaps[gaps.size / 2]
            if (median in 250L..1_500L) return median
        }
        return vibe.defaultBeatMs
    }

    fun shotMs(asset: MagicAsset, beat: Long, vibe: MagicVibe): Long {
        val wanted = beat * if (asset.isImage) vibe.beatsPerPhoto else vibe.beatsPerVideo
        return if (asset.isImage) {
            wanted.coerceIn(MIN_SHOT_MS, MAX_PHOTO_MS)
        } else {
            wanted.coerceAtLeast(MIN_SHOT_MS).coerceAtMost(asset.durationMs)
        }
    }

    /** Best shots that fit the target length, in story order. */
    fun select(assets: List<MagicAsset>, vibe: MagicVibe, length: MagicLength, beat: Long): List<MagicAsset> {
        val unique = dedupe(assets)
        val target = length.targetMs
        val chosen = if (target == null) {
            unique.take(MAX_CLIPS)
        } else {
            val result = mutableListOf<MagicAsset>()
            var total = 0L
            for (a in unique) { // best first
                if (result.size >= MAX_CLIPS) break
                if (total >= target) break
                result += a
                total += shotMs(a, beat, vibe)
            }
            result
        }
        return chronological(chosen)
    }

    fun build(
        assets: List<MagicAsset>,
        vibe: MagicVibe,
        length: MagicLength,
        title: String,
        subtitle: String?,
        music: MagicMusic?,
        aspect: CanvasAspect,
        projectId: String,
        nowMs: Long,
        newId: () -> String,
    ): Project {
        require(assets.isNotEmpty()) { "Pick at least one photo or video" }
        val beat = beatMs(music, vibe)
        val shots = select(assets, vibe, length, beat)

        var clips = shots.mapIndexed { index, a ->
            val ms = shotMs(a, beat, vibe)
            val base = if (a.isImage) {
                VideoClip(
                    id = newId(), sourceUri = a.uri, sourceDurationMs = VideoClip.MAX_IMAGE_MS, trimEndMs = ms,
                    width = a.width, height = a.height, isImage = true, displayName = a.displayName,
                )
            } else {
                // Centre the moment on the sharpest frame the analyzer found.
                val start = (a.bestMs - ms / 2).coerceIn(0, (a.durationMs - ms).coerceAtLeast(0))
                VideoClip(
                    id = newId(), sourceUri = a.uri, sourceDurationMs = a.durationMs,
                    trimStartMs = start, trimEndMs = start + ms,
                    width = a.width, height = a.height, displayName = a.displayName,
                    volume = if (a.audioMuted) 0f else if (music != null) 0.2f else 1f,
                    needsProxy = a.needsProxy,
                )
            }
            base.copy(
                filterId = vibe.filterId,
                transition = if (index == shots.lastIndex) TransitionType.NONE else vibe.transition,
                transitionMs = vibe.transitionMs,
                fadeInMs = if (index == 0) 300 else 0,
                fadeOutMs = if (index == shots.lastIndex) 600 else 0,
            )
        }

        val audio = mutableListOf<AudioClip>()
        if (music != null) {
            val total = clips.sumOf { it.outputDurationMs() }
            val track = AudioClip(
                id = newId(), sourceUri = music.uri, sourceDurationMs = music.durationMs,
                trimEndMs = minOf(music.durationMs, total), startMs = 0, displayName = music.displayName,
                fadeInMs = 300, fadeOutMs = 1_500, beatsMs = music.beatsMs, bpm = music.bpm,
            )
            // Land every cut on a beat.
            val every = vibe.beatsPerPhoto.coerceAtLeast(1)
            clips = BeatSync.retime(clips, BeatSync.cutPoints(BeatSync.timelineBeats(track), every))
            val retimedTotal = clips.sumOf { it.outputDurationMs() }
            audio += track.copy(trimEndMs = minOf(music.durationMs, retimedTotal))
        }

        val totalMs = clips.sumOf { it.outputDurationMs() }
        val firstMs = clips.first().outputDurationMs()
        val titleEnd = maxOf(firstMs, minOf(3_000L, totalMs))
        val overlays = mutableListOf<TextOverlay>()
        if (title.isNotBlank()) {
            overlays += TextOverlay(
                id = newId(), text = title.trim(), startMs = 200, endMs = titleEnd.coerceAtMost(totalMs),
                y = 0.42f, scale = 1.5f, bold = true, background = false,
            )
        }
        subtitle?.takeIf { it.isNotBlank() }?.let {
            overlays += TextOverlay(
                id = newId(), text = it, startMs = 450, endMs = titleEnd.coerceAtMost(totalMs),
                y = 0.52f, scale = 0.8f, background = false,
            )
        }

        val effects = vibe.effect?.let { type ->
            val end = if (vibe.effectWholeMovie) totalMs else titleEnd.coerceAtMost(totalMs)
            listOf(SceneEffect(id = newId(), type = type, startMs = 0, endMs = end, intensity = vibe.effectIntensity))
        }.orEmpty()

        return Project(
            id = projectId,
            name = title.ifBlank { "Magic Movie" },
            createdAtEpochMs = nowMs,
            updatedAtEpochMs = nowMs,
            videoClips = clips,
            audioClips = audio,
            textOverlays = overlays,
            effects = effects,
            aspect = aspect,
        )
    }

    /**
     * "June 2026", "May – July 2026" or "Dec 2025 – Jan 2026" from capture dates; null when
     * unknown. [monthYear] formats a time as e.g. "June 2026", [month] as "June".
     */
    fun dateSpan(
        assets: List<MagicAsset>,
        monthYear: (Long) -> String,
        month: (Long) -> String,
        year: (Long) -> Int,
    ): String? {
        val dates = assets.mapNotNull { it.takenAtMs }.sorted()
        if (dates.isEmpty()) return null
        val a = dates.first()
        val b = dates.last()
        return when {
            monthYear(a) == monthYear(b) -> monthYear(b)
            year(a) == year(b) -> "${month(a)} – ${monthYear(b)}"
            else -> "${monthYear(a)} – ${monthYear(b)}"
        }
    }
}
