package app.frame.studio.ui.templates

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.frame.studio.templates.TemplateCategory
import app.frame.studio.templates.Templates
import app.frame.studio.templates.VideoTemplate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatesScreen(
    onBack: (() -> Unit)?,
    onOpen: (String) -> Unit,
    initialCategory: TemplateCategory? = null,
) {
    var categoryName by rememberSaveable(initialCategory) { mutableStateOf(initialCategory?.name) }
    val category = categoryName?.let { TemplateCategory.valueOf(it) }
    val list = Templates.inCategory(category)

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Templates", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(selected = category == null, onClick = { categoryName = null }, label = { Text("All") })
                    }
                    items(TemplateCategory.entries) { c ->
                        FilterChip(selected = category == c, onClick = { categoryName = c.name }, label = { Text(c.label) })
                    }
                }
            }
            if (category == null || category == TemplateCategory.BIRTHDAY) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    BirthdaySpotlight(onOpen)
                }
            }
            items(list, key = { it.id }) { template ->
                TemplateCard(template) { onOpen(template.id) }
            }
        }
    }
}

/** A featured strip for the most-requested collection. */
@Composable
fun BirthdaySpotlight(onOpen: (String) -> Unit) {
    val birthday = Templates.inCategory(TemplateCategory.BIRTHDAY)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFF2A1240), Color(0xFF4A1E5C))))
            .padding(vertical = 14.dp),
    ) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🎂", style = MaterialTheme.typography.headlineSmall)
            Column(Modifier.padding(start = 10.dp)) {
                Text("Birthday collection", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                Text("Add a name and age, pick your photos — done.", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.75f))
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(top = 12.dp),
        ) {
            items(birthday, key = { it.id }) { t -> MiniTemplate(t) { onOpen(t.id) } }
        }
    }
}

@Composable
fun MiniTemplate(template: VideoTemplate, onClick: () -> Unit) {
    Column(Modifier.width(104.dp).clickable(onClick = onClick)) {
        Box(Modifier.width(104.dp).height(130.dp).clip(RoundedCornerShape(14.dp))) {
            TemplateArt(template, Modifier.fillMaxSize(), showSlots = false)
        }
        Text(
            template.name,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            maxLines = 1,
            modifier = Modifier.padding(top = 6.dp, start = 2.dp),
        )
    }
}
