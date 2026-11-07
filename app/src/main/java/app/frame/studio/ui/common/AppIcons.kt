package app.frame.studio.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CameraRoll
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.ui.graphics.vector.ImageVector
import app.frame.studio.domain.SceneEffectType
import app.frame.studio.magic.MagicVibe

/**
 * Interface icons for domain choices. Kept in the UI layer (the domain stays Compose-free) and
 * drawn from the app's Material icon set, so they match every other icon in the app.
 */
val SceneEffectType.icon: ImageVector
    get() = when (this) {
        SceneEffectType.RAIN -> Icons.Filled.Umbrella
        SceneEffectType.SNOW -> Icons.Filled.AcUnit
        SceneEffectType.FOG -> Icons.Filled.Cloud
        SceneEffectType.CONFETTI -> Icons.Filled.Celebration
        SceneEffectType.SPARKLE -> Icons.Filled.AutoAwesome
        SceneEffectType.HEARTS -> Icons.Filled.Favorite
        SceneEffectType.BOKEH -> Icons.Filled.BlurOn
        SceneEffectType.LIGHT_LEAK -> Icons.Filled.WbTwilight
        SceneEffectType.OLD_FILM -> Icons.Filled.CameraRoll
        SceneEffectType.GLITCH -> Icons.Filled.LiveTv
        SceneEffectType.SHAKE -> Icons.Filled.Vibration
        SceneEffectType.PULSE -> Icons.Filled.MonitorHeart
    }

val MagicVibe.icon: ImageVector
    get() = when (this) {
        MagicVibe.UPBEAT -> Icons.Filled.Bolt
        MagicVibe.CHILL -> Icons.Filled.Spa
        MagicVibe.CINEMATIC -> Icons.Filled.MovieFilter
        MagicVibe.PARTY -> Icons.Filled.Celebration
        MagicVibe.MEMORIES -> Icons.Filled.PhotoAlbum
    }
