package app.frame.studio.ui.common

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import app.frame.studio.data.MediaProbe

/** Frame of a video (or a photo) loaded off the main thread and cached. */
@Composable
fun VideoThumbnail(
    probe: MediaProbe,
    uri: String?,
    atMs: Long,
    modifier: Modifier = Modifier,
    rotationDegrees: Int = 0,
    isImage: Boolean = false,
    maxSize: Int = 320,
    colorFilter: ColorFilter? = null,
    showPlaceholderIcon: Boolean = true,
    animate: Boolean = true,
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, uri, atMs, isImage, maxSize) {
        value = when {
            uri == null -> null
            isImage -> probe.imageThumbnail(uri, maxSize)
            else -> probe.thumbnail(uri, atMs, maxSize)
        }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        if (animate) {
            Crossfade(targetState = bitmap, label = "thumbnail", modifier = Modifier.matchParentSize()) { bmp ->
                ThumbnailContent(bmp, colorFilter, rotationDegrees, showPlaceholderIcon)
            }
        } else {
            Box(Modifier.matchParentSize()) {
                ThumbnailContent(bitmap, colorFilter, rotationDegrees, showPlaceholderIcon)
            }
        }
    }
}

@Composable
private fun ThumbnailContent(bmp: Bitmap?, colorFilter: ColorFilter?, rotationDegrees: Int, showPlaceholderIcon: Boolean) {
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            colorFilter = colorFilter,
            modifier = Modifier.fillMaxSize().rotate(rotationDegrees.toFloat()),
        )
    } else if (showPlaceholderIcon) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                Icons.Outlined.Movie,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
