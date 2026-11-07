package app.frame.studio.ui.projects

import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Spacer
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

/** Key of the "Custom size" card. */
const val CUSTOM_KEY = "custom"

/**
 * "Where will you post it?" — the canvas is chosen before any clip is added, so every clip is
 * placed on the right shape from the start. Original is selected by default; Custom takes any
 * width × height. The size can be changed any time in the editor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewProjectSheet(
    lastChoiceKey: String?,
    onDismiss: () -> Unit,
    /** [customRatio] is width / height when the Custom card was chosen, else null. */
    onChoose: (CanvasChoice?, customRatio: Float?) -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selected by remember { mutableStateOf("original") }
    var customW by remember { mutableStateOf("1080") }
    var customH by remember { mutableStateOf("1350") }
    val custom = CustomSize.parse(customW, customH)
    val cards = listOf(NEW_VIDEO_CHOICES.last()) + NEW_VIDEO_CHOICES.dropLast(1) // Original first
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState()),
        ) {
            Text("New video", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "Where will you post it? Clips of any size are fitted automatically, and you can change this anytime.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )
            val all: List<Pair<String, @Composable (Modifier) -> Unit>> =
                cards.map { c ->
                    c.key to @Composable { m: Modifier ->
                        ChoiceCard(c.title, c.subtitle, c.aspect.ratio, c.preset?.platform?.color, c.preset == null, selected == c.key, c.key == lastChoiceKey, m) { selected = c.key }
                    }
                } + (CUSTOM_KEY to @Composable { m: Modifier ->
                    ChoiceCard("Custom size", custom?.label ?: "Width × height", custom?.ratio, null, true, selected == CUSTOM_KEY, false, m) { selected = CUSTOM_KEY }
                })
            all.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { (_, card) -> card(Modifier.weight(1f)) }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            if (selected == CUSTOM_KEY) {
                CustomSizeFields(customW, customH, { customW = it }, { customH = it }, Modifier.padding(top = 4.dp, bottom = 8.dp))
            }
            Button(
                onClick = {
                    if (selected == CUSTOM_KEY) custom?.let { onChoose(null, it.ratio) }
                    else onChoose(NEW_VIDEO_CHOICES.first { it.key == selected }, null)
                },
                enabled = selected != CUSTOM_KEY || custom != null,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).height(56.dp),
            ) { Text("Create") }
        }
    }
}

/** A width × height the user typed; valid within 100..8192 px and 1:4 .. 4:1. */
data class CustomSize(val width: Int, val height: Int) {
    val ratio: Float get() = width.toFloat() / height
    val label: String get() = "$width × $height"

    companion object {
        fun parse(w: String, h: String): CustomSize? {
            val width = w.trim().toIntOrNull() ?: return null
            val height = h.trim().toIntOrNull() ?: return null
            if (width !in 100..8192 || height !in 100..8192) return null
            val r = width.toFloat() / height
            if (r < app.frame.studio.export.ExportPlan.MIN_CUSTOM_RATIO || r > app.frame.studio.export.ExportPlan.MAX_CUSTOM_RATIO) return null
            return CustomSize(width, height)
        }
    }
}

/** Width and height fields with a swap button and a short validation hint. */
@Composable
fun CustomSizeFields(width: String, height: String, onWidth: (String) -> Unit, onHeight: (String) -> Unit, modifier: Modifier = Modifier) {
    val valid = CustomSize.parse(width, height) != null
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = width, onValueChange = { v -> onWidth(v.filter(Char::isDigit).take(4)) },
                label = { Text("Width") }, singleLine = true, isError = !valid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { val w = width; onWidth(height); onHeight(w) }) {
                Icon(Icons.Filled.SwapHoriz, contentDescription = "Swap width and height")
            }
            OutlinedTextField(
                value = height, onValueChange = { v -> onHeight(v.filter(Char::isDigit).take(4)) },
                label = { Text("Height") }, singleLine = true, isError = !valid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            if (valid) "Pixels. Exported at your chosen quality in the same shape."
            else "Use 100–8192 px, no wider or taller than 4:1.",
            style = MaterialTheme.typography.bodySmall,
            color = if (valid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun ChoiceCard(
    title: String,
    subtitle: String,
    ratio: Float?,
    color: Long?,
    neutral: Boolean,
    selected: Boolean,
    lastUsed: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val accent = MaterialTheme.colorScheme.primary
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(shape)
            .background(if (selected) accent.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(if (selected) 2.dp else 0.dp, if (selected) accent else Color.Transparent, shape)
            .bounceClick(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 12.dp),
    ) {
        // The canvas drawn at its real proportions.
        Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
            val r = (ratio ?: 1f).coerceIn(0.3f, 3f)
            val edge = if (neutral) MaterialTheme.colorScheme.outline else Color(color!!.toInt())
            Box(
                Modifier
                    .then(if (r >= 1f) Modifier.width(60.dp) else Modifier.height(60.dp))
                    .aspectRatio(r)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (neutral) MaterialTheme.colorScheme.surfaceVariant else edge.copy(alpha = 0.22f))
                    .border(1.5.dp, edge, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (color != null) Box(Modifier.size(8.dp).clip(CircleShape).background(edge))
            }
        }
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            minLines = 2,
            maxLines = 2,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (lastUsed) {
            Text("Last used", style = MaterialTheme.typography.labelSmall, color = accent)
        }
    }
}
