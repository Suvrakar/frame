package app.frame.studio.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.frame.studio.domain.Project
import app.frame.studio.timeline.TimelineMath
import app.frame.studio.ui.common.VideoThumbnail
import app.frame.studio.ui.common.bounceClick
import app.frame.studio.ui.common.formatDuration

/**
 * Every layer of the video, top of the stack first: texts and stickers, picture-in-picture
 * overlays, then the main video. Tap a row to select that layer (the playhead jumps into it);
 * arrows change its priority; the lock keeps it from being changed by accident.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayersSheet(
    project: Project,
    selection: Selection,
    onDismiss: () -> Unit,
    onSelect: (Selection, atMs: Long) -> Unit,
    onMove: (Selection, step: Int) -> Unit,
    onLock: (Selection, Boolean) -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val app = LocalContext.current.applicationContext as app.frame.studio.FrameApp
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding().verticalScroll(rememberScrollState())) {
            Text("Layers", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "Top of the list is drawn on top. Lock a layer to keep it from changing.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )

            val texts = project.textOverlays.reversed() // last in the list is drawn on top
            if (texts.isNotEmpty()) GroupLabel("Text & stickers")
            texts.forEachIndexed { i, t ->
                val sel = Selection.Text(t.id)
                LayerRow(
                    title = t.text.lines().first().ifBlank { "Text" },
                    subtitle = "${if (t.isSticker) "Sticker" else if (t.isCaption) "Caption" else "Text"} · ${formatDuration(t.startMs)}–${formatDuration(t.endMs)}",
                    selected = selection == sel,
                    locked = t.locked,
                    canUp = i > 0,
                    canDown = i < texts.lastIndex,
                    thumb = { IconThumb(Icons.Filled.TextFields) },
                    onClick = { onSelect(sel, t.startMs) },
                    onUp = { onMove(sel, +1) },
                    onDown = { onMove(sel, -1) },
                    onLock = { onLock(sel, !t.locked) },
                )
            }

            val overlays = project.overlays.sortedByDescending { it.layer }
            if (overlays.isNotEmpty()) GroupLabel("Overlays")
            overlays.forEachIndexed { i, o ->
                val sel = Selection.Overlay(o.id)
                LayerRow(
                    title = o.displayName.ifBlank { if (o.isImage) "Photo" else "Video" },
                    subtitle = "Layer ${overlays.size - i} · ${formatDuration(o.startMs)}–${formatDuration(o.endMs())}",
                    selected = selection == sel,
                    locked = o.locked,
                    canUp = i > 0,
                    canDown = i < overlays.lastIndex,
                    thumb = {
                        VideoThumbnail(
                            probe = app.mediaProbe, uri = o.sourceUri, atMs = o.trimStartMs, isImage = o.isImage,
                            maxSize = 160, showPlaceholderIcon = true, animate = false, modifier = Modifier.size(44.dp),
                        )
                    },
                    onClick = { onSelect(sel, o.startMs) },
                    onUp = { onMove(sel, +1) },
                    onDown = { onMove(sel, -1) },
                    onLock = { onLock(sel, !o.locked) },
                )
            }

            GroupLabel("Main video")
            val first = project.videoClips.firstOrNull()
            val mainSel = first?.let { Selection.Video(it.id) }
            LayerRow(
                title = "Main video",
                subtitle = "${project.videoClips.size} clip${if (project.videoClips.size == 1) "" else "s"} · ${formatDuration(TimelineMath.totalVideoMs(project.videoClips))}",
                selected = selection is Selection.Video,
                locked = project.mainLocked,
                canUp = false,
                canDown = false,
                thumb = {
                    if (first != null) {
                        VideoThumbnail(
                            probe = app.mediaProbe, uri = first.sourceUri, atMs = first.trimStartMs, isImage = first.isImage,
                            maxSize = 160, showPlaceholderIcon = true, animate = false, modifier = Modifier.size(44.dp),
                        )
                    } else IconThumb(Icons.Filled.Movie)
                },
                onClick = { mainSel?.let { onSelect(it, 0) } },
                onUp = {},
                onDown = {},
                onLock = { onLock(Selection.Video(first?.id.orEmpty()), !project.mainLocked) },
                showArrows = false,
            )
            Box(Modifier.size(16.dp))
        }
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
    )
}

@Composable
private fun IconThumb(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
private fun LayerRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    locked: Boolean,
    canUp: Boolean,
    canDown: Boolean,
    thumb: @Composable () -> Unit,
    onClick: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onLock: () -> Unit,
    showArrows: Boolean = true,
) {
    val shape = RoundedCornerShape(14.dp)
    val accent = MaterialTheme.colorScheme.primary
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(shape)
            .background(if (selected) accent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(if (selected) 1.5.dp else 0.dp, if (selected) accent else Color.Transparent, shape)
            .bounceClick(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.clip(RoundedCornerShape(10.dp))) { thumb() }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        if (showArrows) {
            IconButton(onClick = onUp, enabled = canUp && !locked) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Bring forward") }
            IconButton(onClick = onDown, enabled = canDown && !locked) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Send backward") }
        }
        IconButton(onClick = onLock) {
            Icon(
                if (locked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                contentDescription = if (locked) "Unlock layer" else "Lock layer",
                tint = if (locked) Color(0xFFFFB938) else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
