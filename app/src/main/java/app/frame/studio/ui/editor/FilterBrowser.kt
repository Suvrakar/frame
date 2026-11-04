package app.frame.studio.ui.editor

import app.frame.studio.ui.common.bounceClick
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.frame.studio.FrameApp
import app.frame.studio.domain.ColorAdjust
import app.frame.studio.domain.VideoClip
import app.frame.studio.effects.FilterGroup
import app.frame.studio.effects.FilterPreset
import app.frame.studio.effects.Filters
import app.frame.studio.ui.common.VideoThumbnail
import app.frame.studio.ui.templates.ProBadge
import kotlin.math.roundToInt

/** Full-screen filter library: sections with counts, search, favourites, previews on the clip. */
@Composable
fun FilterBrowser(
    frameApp: FrameApp,
    clip: VideoClip,
    onFilter: (String) -> Unit,
    onIntensity: (Float) -> Unit,
    onClose: () -> Unit,
) {
    val favorites by frameApp.settings.favoriteFilters.collectAsStateWithLifecycle()
    val isPro by frameApp.pro.isPro.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }

    val matches: (FilterPreset) -> Boolean = { f ->
        (tab == 0 || f.id in favorites) && (query.isBlank() || f.name.contains(query.trim(), ignoreCase = true) || f.group.label.contains(query.trim(), ignoreCase = true))
    }
    val sections = FilterGroup.entries.map { g -> g to Filters.all.filter { it.group == g && it.id != "none" && matches(it) } }
        .filter { it.second.isNotEmpty() }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close") }
                    Text("Filters", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    if (clip.filterId != "none") {
                        androidx.compose.material3.TextButton(onClick = { onFilter("none") }) { Text("Remove") }
                    }
                }
                TabRow(selectedTabIndex = tab, containerColor = Color.Transparent) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("All · ${Filters.all.size - 1}") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Favourites · ${favorites.size}") })
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.take(30) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    placeholder = { Text("Search filters") },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                )
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(100.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    if (sections.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                if (tab == 1 && query.isBlank()) "Tap ☆ on a filter to keep it here." else "No filters match \"$query\".",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 32.dp),
                            )
                        }
                    }
                    sections.forEach { (group, filters) ->
                        item(span = { GridItemSpan(maxLineSpan) }, key = "h_${group.name}") {
                            Row(Modifier.padding(top = 12.dp, bottom = 2.dp), verticalAlignment = Alignment.Bottom) {
                                Text(group.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    "  (${filters.size})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        items(filters, key = { it.id }) { f ->
                            FilterTile(
                                frameApp = frameApp,
                                clip = clip,
                                filter = f,
                                selected = clip.filterId == f.id,
                                favorite = f.id in favorites,
                                locked = f.pro && !isPro,
                                onClick = { onFilter(f.id) },
                                onFavorite = { frameApp.settings.toggleFavoriteFilter(f.id) },
                            )
                        }
                    }
                }
                if (clip.filterId != "none") {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        ValueSlider(
                            "${Filters.byId(clip.filterId).name} intensity",
                            "${(clip.filterIntensity * 100).roundToInt()}",
                            clip.filterIntensity,
                            0f..1f,
                            onIntensity,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterTile(
    frameApp: FrameApp,
    clip: VideoClip,
    filter: FilterPreset,
    selected: Boolean,
    favorite: Boolean,
    locked: Boolean,
    onClick: () -> Unit,
    onFavorite: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val matrix = Filters.transformFor(filter.id, 1f, ColorAdjust())
    Column(
        Modifier
            .clip(shape)
            .border(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape)
            .bounceClick(onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.25f)) {
            VideoThumbnail(
                probe = frameApp.mediaProbe,
                uri = clip.sourceUri,
                atMs = clip.trimStartMs,
                isImage = clip.isImage,
                maxSize = 240,
                colorFilter = if (matrix.isIdentity) null else ColorFilter.colorMatrix(ColorMatrix(matrix.toAndroid())),
                modifier = Modifier.fillMaxSize(),
            )
            if (filter.vignette > 0f) {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.radialGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = filter.vignette * 0.8f)),
                    ),
                )
            }
            Icon(
                if (favorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = if (favorite) "Remove from favourites" else "Add to favourites",
                tint = if (favorite) Color(0xFFFFC94D) else Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .size(28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onFavorite)
                    .padding(4.dp),
            )
            if (locked) ProBadge(Modifier.align(Alignment.BottomEnd).padding(4.dp))
        }
        Text(
            filter.name,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(vertical = 6.dp, horizontal = 8.dp),
        )
    }
}
