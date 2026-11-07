package app.frame.studio.ui.templates

import android.graphics.RectF
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.frame.studio.templates.PhotoAdjustSession
import app.frame.studio.templates.PhotoFit
import app.frame.studio.templates.PhotoFraming
import app.frame.studio.ui.common.GlassBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Frame / collage templates: the real design with the user's photos. Tap a photo to select it,
 * drag to move it inside its frame, pinch to zoom. The photo always fills its frame (no gaps,
 * no stretching). "Create video" renders the movie with this framing.
 */
@Composable
fun AdjustPhotosScreen(
    session: PhotoAdjustSession,
    creating: Boolean,
    onCancel: () -> Unit,
    onCreate: (Map<String, PhotoFit>) -> Unit,
) {
    BackHandler(enabled = !creating, onBack = onCancel)
    val fits = remember(session) { mutableStateMapOf<String, PhotoFit>() }
    var page by remember(session) { mutableStateOf(0) }
    var selected by remember(session) { mutableStateOf<String?>(null) }
    var frame by remember(session) { mutableStateOf<Pair<ImageBitmap, Map<String, RectF>>?>(null) }
    val renderLock = remember(session) { Mutex() }

    // Redraw the page whenever a fit changes; while dragging, only the newest state is drawn.
    LaunchedEffect(session, page) {
        snapshotFlow { fits.toMap() }.collect { snapshot ->
            val (bitmap, slots) = withContext(Dispatchers.Default) { renderLock.withLock { session.render(page, snapshot) } }
            frame = bitmap.asImageBitmap() to slots
        }
    }

    GlassBackground {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCancel, enabled = !creating) { Icon(Icons.Filled.Close, contentDescription = "Cancel") }
                Text("Adjust photos", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (session.pages > 1) {
                    IconButton(onClick = { page--; selected = null }, enabled = page > 0) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous page")
                    }
                    Text("${page + 1} / ${session.pages}", style = MaterialTheme.typography.labelLarge)
                    IconButton(onClick = { page++; selected = null }, enabled = page < session.pages - 1) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next page")
                    }
                }
            }

            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                val density = LocalDensity.current
                val boxW = with(density) { maxWidth.toPx() }
                val boxH = with(density) { maxHeight.toPx() }
                val scale = minOf(boxW / session.width, boxH / session.height)
                val shownW = session.width * scale
                val shownH = session.height * scale
                val current = frame
                Box(
                    Modifier
                        .size(with(density) { shownW.toDp() }, with(density) { shownH.toDp() })
                        .pointerInput(session, page, current) {
                            val slots = current?.second ?: return@pointerInput
                            fun hit(p: Offset): String? = slots.entries.firstOrNull { it.value.contains(p.x / scale, p.y / scale) }?.key
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                val uri = hit(down.position) ?: return@awaitEachGesture
                                selected = uri
                                val slot = slots[uri] ?: return@awaitEachGesture
                                val (bw, bh) = session.photoSize(uri) ?: return@awaitEachGesture
                                do {
                                    val event = awaitPointerEvent()
                                    val zoom = event.calculateZoom()
                                    val pan = event.calculatePan()
                                    if (zoom != 1f || pan != Offset.Zero) {
                                        var fit = fits[uri] ?: PhotoFit.CENTER
                                        fit = PhotoFraming.zoom(bw, bh, slot.width(), slot.height(), fit, zoom)
                                        fit = PhotoFraming.pan(bw, bh, slot.width(), slot.height(), fit, pan.x / scale, pan.y / scale)
                                        fits[uri] = fit
                                    }
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                } while (event.changes.any { it.pressed })
                            }
                        },
                ) {
                    if (current == null) {
                        CircularProgressIndicator(Modifier.align(Alignment.Center))
                    } else {
                        Image(current.first, contentDescription = "Template preview", contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
                        val outline = MaterialTheme.colorScheme.primary
                        Canvas(Modifier.fillMaxSize()) {
                            current.second[selected]?.let { r ->
                                drawRoundRect(
                                    color = outline,
                                    topLeft = Offset(r.left * scale, r.top * scale),
                                    size = Size(r.width() * scale, r.height() * scale),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()),
                                    style = Stroke(width = 3.dp.toPx()),
                                )
                            }
                        }
                    }
                }
            }

            Text(
                if (selected == null) "Tap a photo, then drag to move it and pinch to zoom." else "Drag to move · pinch to zoom",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(horizontal = 16.dp),
            )
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { selected?.let { fits.remove(it) } },
                    enabled = !creating && selected != null && fits.containsKey(selected),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.height(56.dp),
                ) {
                    Icon(Icons.Filled.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Reset")
                }
                Button(
                    onClick = { onCreate(fits.toMap()) },
                    enabled = !creating,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f).height(56.dp),
                ) {
                    if (creating) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("Create video")
                    }
                }
            }
        }
    }
}
