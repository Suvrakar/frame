package app.frame.studio.ui.theme

import androidx.compose.ui.graphics.Color
import app.frame.studio.domain.AudioKind

/** Timeline lane colors, tuned for contrast against the dark canvas. */
object TrackColors {
    val music = Color(0xFF1E8E5A)
    val voice = Color(0xFFD9822B)
    val sound = Color(0xFF2B7BD9)
    val text = Color(0xFF7E57C2)
    val sticker = Color(0xFFC2185B)
    val effect = Color(0xFFE09A1E)

    fun audio(kind: AudioKind): Color = when (kind) {
        AudioKind.MUSIC -> music
        AudioKind.VOICE -> voice
        AudioKind.SOUND -> sound
    }
}

/** Swatches offered for text color. */
val TextSwatches: List<Long> = listOf(
    0xFFFFFFFF, 0xFF000000, 0xFFFFD60A, 0xFFFF453A, 0xFFFF375F, 0xFFBF5AF2,
    0xFF0A84FF, 0xFF64D2FF, 0xFF30D158, 0xFFFF9F0A,
)
