package app.frame.studio.ui.templates

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.frame.studio.templates.TemplatePreviews
import app.frame.studio.templates.VideoTemplate
import app.frame.studio.ui.theme.LocalGlass

/** Rendered preview of a template at its real shape, with a PRO crown when it's premium. */
@Composable
fun TemplateTile(
    previews: TemplatePreviews,
    template: VideoTemplate,
    modifier: Modifier = Modifier,
    showName: Boolean = true,
    onClick: () -> Unit,
) {
    val ratio = template.aspect.ratio ?: (9f / 16f)
    val shape = RoundedCornerShape(14.dp)
    val widthPx = with(LocalDensity.current) { 140.dp.roundToPx() }.coerceIn(240, 420)
    val bitmap by produceState<Bitmap?>(previews.cached(template, widthPx), template.id) {
        if (value == null) value = previews.preview(template, widthPx)
    }
    Column(modifier.clickable(onClick = onClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(ratio)
                .clip(shape)
                .border(1.dp, LocalGlass.current.border, shape),
        ) {
            Crossfade(bitmap, label = "preview") { b ->
                if (b != null) {
                    Image(b.asImageBitmap(), contentDescription = template.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    TemplateArt(template, Modifier.fillMaxSize(), showSlots = false)
                }
            }
            if (template.pro) ProBadge(Modifier.align(Alignment.BottomEnd).padding(6.dp))
            template.badge?.takeIf { !template.pro }?.let { badge ->
                Text(
                    badge,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.92f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        if (showName) {
            Text(
                template.name,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier.padding(start = 2.dp, top = 5.dp),
            )
        }
    }
}

@Composable
fun ProBadge(modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.WorkspacePremium, contentDescription = null, tint = Color(0xFFFFC94D), modifier = Modifier.size(13.dp))
        Text("PRO", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(start = 3.dp))
    }
}
