package app.frame.studio.ui.templates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
    val category = categoryName?.let { TemplateCategory.valueOf(it) }
    val list = Templates.inCategory(category).filter { t -> proOnly == null || t.pro == proOnly }
        // New decorated frames first: they're the most eye-catching.
        .sortedByDescending { it.collage?.ownTitle == true }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(category?.label ?: "Templates", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
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
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 2.dp)) {
                    item { FilterChip(selected = category == null, onClick = { categoryName = null }, label = { Text("All") }) }
                    items(TemplateCategory.entries) { c ->
                        FilterChip(selected = category == c, onClick = { categoryName = c.name }, label = { Text(c.label) })
                    }
                    item { FilterChip(selected = proOnly == false, onClick = { proOnly = if (proOnly == false) null else false }, label = { Text("Free") }) }
                    item { FilterChip(selected = proOnly == true, onClick = { proOnly = if (proOnly == true) null else true }, label = { Text("Pro") }) }
                }
            }
            items(list, key = { it.id }) { template ->
                TemplateTile(previews, template) { onOpen(template.id) }
            }
        }
    }
}
