package app.frame.studio.ui.common

import androidx.compose.material.icons.filled.BubbleChart
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.SportsBasketball
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Gradient
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material.icons.filled.KeyboardDoubleArrowLeft
import androidx.compose.material.icons.filled.KeyboardDoubleArrowRight
import androidx.compose.material.icons.filled.KeyboardDoubleArrowUp
import androidx.compose.material.icons.filled.KeyboardDoubleArrowDown
import androidx.compose.material.icons.filled.Cyclone
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.LocalFireDepartment
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
import app.frame.studio.domain.TextAnimation
import app.frame.studio.domain.TransitionType
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

val TransitionType.icon: ImageVector
    get() = when (this) {
        TransitionType.NONE -> Icons.Filled.Block
        TransitionType.FADE_BLACK -> Icons.Filled.Gradient
        TransitionType.FADE_WHITE -> Icons.Filled.FlashOn
        TransitionType.ZOOM_IN -> Icons.Filled.ZoomIn
        TransitionType.ZOOM_OUT -> Icons.Filled.ZoomOut
        TransitionType.WHIP_LEFT -> Icons.Filled.KeyboardDoubleArrowLeft
        TransitionType.WHIP_RIGHT -> Icons.Filled.KeyboardDoubleArrowRight
        TransitionType.WHIP_UP -> Icons.Filled.KeyboardDoubleArrowUp
        TransitionType.WHIP_DOWN -> Icons.Filled.KeyboardDoubleArrowDown
        TransitionType.SPIN -> Icons.Filled.Cyclone
        TransitionType.BLUR -> Icons.Filled.BlurOn
        TransitionType.GLITCH -> Icons.Filled.BrokenImage
        TransitionType.PIXELATE -> Icons.Filled.GridOn
        TransitionType.IRIS -> Icons.Filled.Camera
        TransitionType.SHAKE -> Icons.Filled.Vibration
        TransitionType.BURN -> Icons.Filled.LocalFireDepartment
    }

val TextAnimation.icon: ImageVector
    get() = when (this) {
        TextAnimation.NONE -> Icons.Filled.Block
        TextAnimation.FADE -> Icons.Filled.Gradient
        TextAnimation.POP -> Icons.Filled.BubbleChart
        TextAnimation.SLIDE_UP -> Icons.Filled.ArrowUpward
        TextAnimation.SLIDE_DOWN -> Icons.Filled.ArrowDownward
        TextAnimation.SLIDE_LEFT -> Icons.AutoMirrored.Filled.ArrowBack
        TextAnimation.SLIDE_RIGHT -> Icons.AutoMirrored.Filled.ArrowForward
        TextAnimation.ZOOM -> Icons.Filled.ZoomIn
        TextAnimation.BOUNCE -> Icons.Filled.SportsBasketball
        TextAnimation.SPIN -> Icons.Filled.RotateRight
        TextAnimation.TYPEWRITER -> Icons.Filled.Keyboard
    }

val MagicVibe.icon: ImageVector
    get() = when (this) {
        MagicVibe.UPBEAT -> Icons.Filled.Bolt
        MagicVibe.CHILL -> Icons.Filled.Spa
        MagicVibe.CINEMATIC -> Icons.Filled.MovieFilter
        MagicVibe.PARTY -> Icons.Filled.Celebration
        MagicVibe.MEMORIES -> Icons.Filled.PhotoAlbum
    }
