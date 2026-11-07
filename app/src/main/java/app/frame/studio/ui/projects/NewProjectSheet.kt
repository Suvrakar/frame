package app.frame.studio.ui.projects

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.frame.studio.domain.CanvasAspect
import app.frame.studio.export.SocialPreset
import app.frame.studio.export.SocialPresets
import app.frame.studio.ui.common.bounceClick

/** One choice in the "New video" sheet: a destination format, or "Original" (null preset). */
data class CanvasChoice(val title: String, val subtitle: String, val aspect: CanvasAspect, val preset: SocialPreset?, val color: Long) {
    val key: String get() = preset?.id ?: "original"
}

/** The formats people post most, in order; everything else lives in the editor's Canvas tool. */
val NEW_VIDEO_CHOICES: List<CanvasChoice> = buildList {
    fun add(id: String, title: String) {
        SocialPresets.all.firstOrNull { it.id == id }?.let { add(CanvasChoice(title, it.aspect.label, it.aspect, it, it.platform.color)) }
    }
    add("tt_video", "TikTok")
    add("yt_short", "YouTube Shorts")
    add("ig_reel", "Instagram Reels")
    add("yt_video", "YouTube")
    add("fb_story", "Facebook Story")
    add("ig_story", "Instagram Story")
    add("ig_feed", "Instagram Post")
    add("fb_feed", "Facebook Post")
    add("ig_square", "Square")
    add("wa_status", "WhatsApp Status")
    add(CanvasChoice("Original", "Follows your first clip", CanvasAspect.ORIGINAL, null, 0xFF9AA0A6))
}

/**
 * "Where will you post it?" — the canvas is chosen before any clip is added, so every clip is
 * placed on the right shape from the start (and nothing needs fixing by hand). The last choice is
 * offered first. The size can be changed any time in the editor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewProjectSheet(lastChoiceKey: String?, onDismiss: () -> Unit, onChoose: (CanvasChoice) -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val ordered = NEW_VIDEO_CHOICES.sortedBy { if (it.key == lastChoiceKey) 0 else 1 }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding()) {
            Text("New video", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "Where will you post it? Clips of any size are fitted automatically, and you can change this anytime.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(bottom = 16.dp),
            ) {
                items(ordered, key = { it.key }) { choice ->
                    ChoiceCard(choice, lastUsed = choice.key == lastChoiceKey) { onChoose(choice) }
                }
            }
        }
    }
}

@Composable
private fun ChoiceCard(choice: CanvasChoice, lastUsed: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val accent = MaterialTheme.colorScheme.primary
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(shape)
            .background(if (lastUsed) accent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(if (lastUsed) 1.5.dp else 0.dp, if (lastUsed) accent else Color.Transparent, shape)
            .bounceClick(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
    ) {
        // The canvas drawn at its real proportions.
        Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
            val r = (choice.aspect.ratio ?: 1f).coerceIn(0.3f, 3f)
            Box(
                Modifier
                    .then(if (r >= 1f) Modifier.width(60.dp) else Modifier.height(60.dp))
                    .aspectRatio(r)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (choice.preset == null) MaterialTheme.colorScheme.surfaceVariant
                        else Color(choice.color.toInt()).copy(alpha = 0.22f),
                    )
                    .border(1.5.dp, if (choice.preset == null) MaterialTheme.colorScheme.outline else Color(choice.color.toInt()), RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (choice.preset == null) "Auto" else choice.aspect.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            if (choice.preset != null) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(Color(choice.color.toInt())))
            }
            Text(
                choice.title,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = if (choice.preset != null) 5.dp else 0.dp),
            )
        }
        Text(
            if (lastUsed) "Last used" else choice.subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = if (lastUsed) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
