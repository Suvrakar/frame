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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.frame.studio.templates.CollageLayout
import app.frame.studio.templates.CollageStyle
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
        val collage = template.collage
        if (collage != null) {
            CollagePreview(template, collage, values, onColor, minSide)
        } else {
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
        }
        if (showSlots && collage == null) {
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
                template.aspect.label,
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
            "${Templates.mediaLabel(template)} · ${formatSeconds(template.totalMs)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
        )
    }
}

fun formatSeconds(ms: Long): String = "${(ms + 500) / 1000}s"

/** Code-drawn preview of a collage layout: title band plus framed photo placeholders. */
@Composable
private fun CollagePreview(
    template: VideoTemplate,
    style: CollageStyle,
    values: Map<TemplateField, String>,
    onColor: Color,
    minSide: Float,
) {
    val density = LocalDensity.current
    val accent = Color(template.accent.toInt())
    val tileTop = Color.White.copy(alpha = 0.92f)
    val tileBottom = Color(template.colors.last().toInt()).copy(alpha = 0.55f)
    val (title, subtitle) = remember(template.id, values) {
        template.header?.let { Templates.fillCard(it, values) } ?: (template.name to "")
    }
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val cells = CollageLayout.cells(style, size.width, size.height)
            val r = minOf(size.width, size.height) * 0.025f
            if (style == CollageStyle.FILMSTRIP) {
                val first = cells.first()
                val last = cells.last()
                val inset = if (size.height >= size.width) first.width * 0.19f else first.height * 0.19f
                drawRoundRect(
                    Color(0xF0141414),
                    topLeft = Offset(minOf(first.left, last.left) - inset, first.top - inset * 0.6f),
                    size = Size(
                        maxOf(first.right, last.right) - minOf(first.left, last.left) + inset * 2,
                        maxOf(first.bottom, last.bottom) - first.top + inset * 1.2f,
                    ),
                    cornerRadius = CornerRadius(r * 0.4f),
                )
            }
            cells.forEach { c ->
                rotate(c.rotation, pivot = Offset(c.centerX, c.centerY)) {
                    val photoTop: Float
                    val photoLeft: Float
                    val photoW: Float
                    val photoH: Float
                    if (style == CollageStyle.POLAROIDS) {
                        drawRect(Color.White, topLeft = Offset(c.left, c.top), size = Size(c.width, c.height))
                        val inset = c.width * 0.06f
                        photoLeft = c.left + inset
                        photoTop = c.top + inset
                        photoW = c.width - inset * 2
                        photoH = c.height * 0.8f - inset
                    } else {
                        if (style != CollageStyle.FILMSTRIP) {
                            val mat = r * 0.35f
                            drawRoundRect(Color.White, topLeft = Offset(c.left - mat, c.top - mat), size = Size(c.width + mat * 2, c.height + mat * 2), cornerRadius = CornerRadius(r + mat))
                        }
                        photoLeft = c.left
                        photoTop = c.top
                        photoW = c.width
                        photoH = c.height
                    }
                    drawRoundRect(
                        Brush.verticalGradient(listOf(tileTop, tileBottom), startY = photoTop, endY = photoTop + photoH),
                        topLeft = Offset(photoLeft, photoTop),
                        size = Size(photoW, photoH),
                        cornerRadius = CornerRadius(if (style == CollageStyle.POLAROIDS) 0f else r),
                    )
                    // Classic "photo" glyph: a sun and two hills.
                    val s = minOf(photoW, photoH)
                    drawCircle(accent.copy(alpha = 0.9f), radius = s * 0.09f, center = Offset(photoLeft + photoW * 0.7f, photoTop + photoH * 0.32f))
                    val base = photoTop + photoH * 0.8f
                    val hills = Path().apply {
                        moveTo(photoLeft + photoW * 0.18f, base)
                        lineTo(photoLeft + photoW * 0.4f, base - s * 0.32f)
                        lineTo(photoLeft + photoW * 0.56f, base - s * 0.12f)
                        lineTo(photoLeft + photoW * 0.66f, base - s * 0.22f)
                        lineTo(photoLeft + photoW * 0.84f, base)
                        close()
                    }
                    drawPath(hills, Color(template.colors.first().toInt()).copy(alpha = 0.65f))
                }
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(CollageLayout.HEADER_FRACTION)
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                style = TextStyle(
                    color = onColor,
                    fontWeight = FontWeight.Black,
                    fontSize = with(density) { (minSide * 0.075f).toSp() },
                    shadow = if (onColor == Color.White) Shadow(Color.Black.copy(alpha = 0.3f), Offset(0f, 2f), 6f) else null,
                ),
            )
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle.uppercase(),
                    maxLines = 1,
                    style = TextStyle(
                        color = onColor.copy(alpha = 0.85f),
                        fontWeight = FontWeight.Medium,
                        fontSize = with(density) { (minSide * 0.032f).toSp() },
                        letterSpacing = with(density) { (minSide * 0.006f).toSp() },
                    ),
                )
            }
        }
    }
}
