package app.frame.studio.ui.templates

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.frame.studio.templates.TemplateField
import app.frame.studio.templates.Templates
import app.frame.studio.templates.VideoTemplate
import kotlin.random.Random

private class Confetti(val x: Float, val y: Float, val size: Float, val color: Color, val circle: Boolean, val angle: Float, val speed: Float)

/**
 * The template's look drawn in Compose: gradient, confetti, emoji and the personalized title.
 * Mirrors [app.frame.studio.templates.CardRenderer] so the preview matches the video.
 */
@Composable
fun TemplateArt(
    template: VideoTemplate,
    modifier: Modifier = Modifier,
    values: Map<TemplateField, String> = Templates.previewValues,
    animated: Boolean = false,
    showSlots: Boolean = true,
) {
    val colors = remember(template.id) { template.colors.map { Color(it.toInt()) } }
    val onColor = remember(template.id) { Color(Templates.onColor(template).toInt()) }
    val pieces = remember(template.id) {
        val random = Random(template.id.hashCode())
        val palette = listOf(Color(template.accent.toInt()), Color.White, colors.last(), colors.first())
        List(36) {
            Confetti(
                x = random.nextFloat(),
                y = random.nextFloat(),
                size = 0.008f + random.nextFloat() * 0.014f,
                color = palette[random.nextInt(palette.size)].copy(alpha = 0.45f + random.nextFloat() * 0.5f),
                circle = random.nextBoolean(),
                angle = random.nextFloat() * 180f,
                speed = 0.5f + random.nextFloat(),
            )
        }
    }
    val fall: State<Float> = if (animated) {
        rememberInfiniteTransition(label = "confetti").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(6_000, easing = LinearEasing), RepeatMode.Restart),
            label = "fall",
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }
    val (title, subtitle) = remember(template.id, values) {
        template.intro?.let { Templates.fillCard(it, values) }
            ?: template.outro?.let { Templates.fillCard(it, values) }
            ?: (template.name to "")
    }
    val emoji = template.intro?.emoji?.ifBlank { null } ?: template.emoji

    BoxWithConstraints(modifier.background(Brush.linearGradient(colors))) {
        val density = LocalDensity.current
        val minSide = with(density) { minOf(maxWidth, maxHeight).toPx() }
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Transparent))))
        Canvas(Modifier.fillMaxSize()) {
            val t = fall.value
            pieces.forEach { p ->
                val y = ((p.y + t * p.speed) % 1f) * size.height
                val x = p.x * size.width
                val s = p.size * minSide
                if (p.circle) {
                    drawCircle(p.color, radius = s, center = Offset(x, y))
                } else {
                    rotate(p.angle + t * 360f * p.speed, pivot = Offset(x, y)) {
                        drawRoundRect(p.color, topLeft = Offset(x - s * 1.6f, y - s * 0.6f), size = Size(s * 3.2f, s * 1.2f), cornerRadius = CornerRadius(s * 0.4f))
                    }
                }
            }
        }
        Column(
            Modifier.fillMaxSize().padding(horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(emoji, style = TextStyle(fontSize = with(density) { (minSide * 0.2f).toSp() }))
            Text(
                title,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    color = onColor,
                    fontWeight = FontWeight.Black,
                    fontSize = with(density) { (minSide * 0.11f).toSp() },
                    lineHeight = with(density) { (minSide * 0.12f).toSp() },
                    shadow = if (onColor == Color.White) Shadow(Color.Black.copy(alpha = 0.35f), Offset(0f, 3f), 10f) else null,
                ),
            )
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle.uppercase(),
                    maxLines = 1,
                    style = TextStyle(
                        color = onColor.copy(alpha = 0.88f),
                        fontWeight = FontWeight.Medium,
                        letterSpacing = with(density) { (minSide * 0.008f).toSp() },
                        fontSize = with(density) { (minSide * 0.045f).toSp() },
                    ),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        if (showSlots) {
            // Hint at the number of photo/video slots.
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                repeat(template.slots.coerceAtMost(8)) {
                    Box(
                        Modifier
                            .size(width = 10.dp, height = 14.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(onColor.copy(alpha = 0.55f)),
                    )
                }
            }
        }
    }
}

/** Gallery card: art in a uniform 4:5 frame, badge, duration, name and meta. */
@Composable
fun TemplateCard(template: VideoTemplate, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(4f / 5f).clip(RoundedCornerShape(18.dp))) {
            TemplateArt(template, Modifier.fillMaxSize())
            template.badge?.let { badge ->
                Text(
                    badge,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White)
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                )
            }
            Text(
                "${template.aspect.label} · ${formatSeconds(template.totalMs)}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            )
        }
        Text(
            template.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp),
        )
        Text(
            "${template.slots} clips · ${template.category.label}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
        )
    }
}

fun formatSeconds(ms: Long): String = "${(ms + 500) / 1000}s"
