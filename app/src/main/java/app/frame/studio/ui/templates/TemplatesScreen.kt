package app.frame.studio.ui.templates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.frame.studio.templates.TemplateCategory
import app.frame.studio.templates.TemplatePreviews
import app.frame.studio.templates.Templates

/** Masonry gallery of every template, rendered at its real shape. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatesScreen(
    previews: TemplatePreviews,
    onBack: (() -> Unit)?,
    onOpen: (String) -> Unit,
    initialCategory: TemplateCategory? = null,
) {
    var categoryName by rememberSaveable(initialCategory) { mutableStateOf(initialCategory?.name) }
    var proOnly by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    val category = categoryName?.let { TemplateCategory.valueOf(it) }
    val q = query.trim()
    val list = Templates.inCategory(category)
        .filter { t -> proOnly == null || t.pro == proOnly }
        .filter { t ->
            q.isEmpty() || listOf(t.name, t.tagline, t.category.label, t.collage?.label.orEmpty(), t.badge.orEmpty())
                .any { it.contains(q, ignoreCase = true) }
        }
        // New decorated frames first: they're the most eye-catching.
        .sortedByDescending { it.collage?.ownTitle == true }

    val focus = LocalFocusManager.current
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Templates", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
                // Search stays pinned above the grid.
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.take(40) },
                    placeholder = { Text("Search birthday, invitation, polaroid…") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, contentDescription = "Clear search") }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                )
                // Categories as tabs, pinned under the search bar.
                val tabs = listOf<TemplateCategory?>(null) + TemplateCategory.entries
                ScrollableTabRow(
                    selectedTabIndex = tabs.indexOf(category).coerceAtLeast(0),
                    containerColor = Color.Transparent,
                    edgePadding = 14.dp,
                    divider = {},
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    tabs.forEach { c ->
                        Tab(
                            selected = c == category,
                            onClick = { categoryName = c?.name },
                            text = {
                                Text(
                                    "${c?.label ?: "All"} ${Templates.inCategory(c).size}",
                                    fontWeight = if (c == category) FontWeight.Bold else FontWeight.Normal,
                                )
                            },
                        )
                    }
                }
            }
        },
    ) { padding ->
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Adaptive(108.dp),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalItemSpacing = 12.dp,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            item(span = StaggeredGridItemSpan.FullLine) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (q.isEmpty()) "${list.size} templates" else "${list.size} results for \"$q\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    FilterChip(selected = proOnly == false, onClick = { proOnly = if (proOnly == false) null else false }, label = { Text("Free") })
                    FilterChip(selected = proOnly == true, onClick = { proOnly = if (proOnly == true) null else true }, label = { Text("Pro") })
                }
            }
            if (list.isEmpty()) {
                item(span = StaggeredGridItemSpan.FullLine) {
                    Text(
                        "No templates match. Try \"birthday\", \"love\" or \"collage\".",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 32.dp),
                    )
                }
            }
            items(list, key = { it.id }) { template ->
                TemplateTile(previews, template) { onOpen(template.id) }
            }
        }
    }
}
