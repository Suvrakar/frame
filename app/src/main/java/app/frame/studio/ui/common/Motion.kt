package app.frame.studio.ui.common

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ripple
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

/** Shared motion tokens so every screen moves the same way. */
object Motion {
    /** Material "emphasized decelerate"-like curve for things arriving on screen. */
    val Emphasized = FastOutSlowInEasing
    const val SHORT = 160
    const val MEDIUM = 260
}

/**
 * Tactile press feedback: the whole element (everything before this call in the chain) eases down
 * to [pressedScale] while pressed and springs back on release, with a ripple clipped to its shape.
 * Scaling happens in a graphics layer, so it never triggers layout or recomposition of children.
 */
fun Modifier.bounceClick(
    enabled: Boolean = true,
    pressedScale: Float = 0.96f,
    showRipple: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    val chain = this
    return Modifier.composed {
        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (pressed && enabled) pressedScale else 1f,
            animationSpec = if (pressed) spring(stiffness = Spring.StiffnessHigh) else spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
            label = "press",
        )
        Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(chain)
            .clickable(
                interactionSource = interaction,
                indication = if (showRipple) ripple() else null,
                enabled = enabled,
                onClick = onClick,
            )
    }
}
