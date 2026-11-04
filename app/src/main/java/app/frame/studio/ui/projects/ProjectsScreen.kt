package app.frame.studio.ui.projects

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.AutoAwesomeMosaic
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.frame.studio.FrameApp
import app.frame.studio.domain.Project
import app.frame.studio.templates.TemplateCategory
import app.frame.studio.templates.Templates
import app.frame.studio.timeline.TimelineMath
import app.frame.studio.ui.common.ConfirmDialog
import app.frame.studio.ui.common.GlassCard
import app.frame.studio.ui.common.GlassTile
import app.frame.studio.ui.common.SectionHeader
import app.frame.studio.ui.common.TextInputDialog
import app.frame.studio.ui.common.VideoThumbnail
import app.frame.studio.ui.common.formatDate
import app.frame.studio.ui.common.formatDuration
import app.frame.studio.ui.templates.TemplateCard
import app.frame.studio.ui.theme.LocalGlass
import kotlinx.coroutines.launch
import java.util.Calendar
import androidx.compose.foundation.lazy.items as rowItems

/** Home tab: greeting, create card, quick starts, templates and recent projects. */
@Composable
fun ProjectsScreen(
    app: FrameApp,
    onOpenProject: (String) -> Unit,
    onNewProject: (String) -> Unit,
    onOpenExports: () -> Unit,
    onOpenTemplates: (TemplateCategory?) -> Unit,
    onOpenTemplate: (String) -> Unit,
) {
    val projects by app.repository.projects.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var renameTarget by remember { mutableStateOf<Project?>(null) }
    var deleteTarget by remember { mutableStateOf<Project?>(null) }

    fun newProject() {
        scope.launch { onNewProject(app.repository.create("Project ${projects.size + 1}").id) }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { Greeting() }
        item(span = { GridItemSpan(maxLineSpan) }) { CreateCard(onStart = ::newProject) }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader("Quick start")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassTile(Icons.Outlined.Dashboard, "Templates", Modifier.weight(1f)) { onOpenTemplates(null) }
                    GlassTile(Icons.Outlined.AutoAwesomeMosaic, "Collage", Modifier.weight(1f)) { onOpenTemplates(TemplateCategory.COLLAGE) }
                    GlassTile(Icons.Outlined.Cake, "Birthday", Modifier.weight(1f)) { onOpenTemplates(TemplateCategory.BIRTHDAY) }
                    GlassTile(Icons.Outlined.VideoLibrary, "Exports", Modifier.weight(1f)) { onOpenExports() }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader("Templates", action = "See all", onAction = { onOpenTemplates(null) })
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                    rowItems(Templates.featured, key = { it.id }) { t ->
                        TemplateCard(t, Modifier.width(132.dp)) { onOpenTemplate(t.id) }
                    }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            SectionHeader(if (projects.isEmpty()) "Your projects" else "Recent projects · ${projects.size}")
        }
        if (projects.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { EmptyProjects(onStart = ::newProject) }
        }
        items(projects, key = { it.id }) { project ->
            ProjectCard(
                app = app,
                project = project,
                onOpen = { onOpenProject(project.id) },
                onRename = { renameTarget = project },
                onDuplicate = { scope.launch { app.repository.duplicate(project.id) } },
                onDelete = { deleteTarget = project },
            )
        }
    }

    renameTarget?.let { project ->
        TextInputDialog(
            title = "Rename project",
            initial = project.name,
            confirmLabel = "Rename",
            onDismiss = { renameTarget = null },
            onConfirm = { name ->
                renameTarget = null
                scope.launch { app.repository.save(project.copy(name = name)) }
            },
        )
    }
    deleteTarget?.let { project ->
        ConfirmDialog(
            title = "Delete \"${project.name}\"?",
            message = "The project will be removed. Your original videos and finished exports are not affected.",
            confirmLabel = "Delete",
            onDismiss = { deleteTarget = null },
            onConfirm = {
                deleteTarget = null
                scope.launch { app.repository.delete(project.id) }
            },
        )
    }
}

@Composable
private fun Greeting() {
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }
    Column(Modifier.padding(top = 12.dp, bottom = 2.dp)) {
        Text(greeting, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Create a ", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
            Text("video", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** The hero: an indigo card with soft highlights and a "Start" pill. */
@Composable
private fun CreateCard(onStart: () -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    val glass = LocalGlass.current
    val top = if (glass.isDark) Color(0xFF4A52D8) else Color(0xFF5B63F0)
    val bottom = if (glass.isDark) Color(0xFF2E338F) else Color(0xFF7A81FF)
    Box(
        Modifier
            .fillMaxWidth()
            .height(156.dp)
            .shadow(16.dp, shape, ambientColor = top.copy(alpha = 0.4f), spotColor = top.copy(alpha = 0.4f))
            .clip(shape)
            .background(Brush.linearGradient(listOf(top, bottom)))
            .drawBehind {
                drawCircle(Color.White.copy(alpha = 0.10f), radius = size.height * 0.9f, center = Offset(size.width * 0.95f, size.height * 0.05f))
                drawCircle(Color.White.copy(alpha = 0.07f), radius = size.height * 0.55f, center = Offset(size.width * 0.78f, size.height * 1.05f))
            }
            .clickable(onClick = onStart)
            .padding(20.dp),
    ) {
        Column(Modifier.align(Alignment.CenterStart).fillMaxWidth(0.68f)) {
            Text("New project", style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
            Text(
                "Videos, photos, music and text. No watermark.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.82f),
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "Start",
                style = MaterialTheme.typography.labelLarge,
                color = top,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White)
                    .padding(horizontal = 18.dp, vertical = 8.dp),
            )
        }
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .size(64.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.18f))
                .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Add, contentDescription = "New project", tint = Color.White, modifier = Modifier.size(32.dp))
        }
    }
}

@Composable
private fun EmptyProjects(onStart: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), onClick = onStart) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Outlined.Movie, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
            Spacer(Modifier.height(8.dp))
            Text("No projects yet", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(
                "Start one above or pick a template.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProjectCard(
    app: FrameApp,
    project: Project,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val first = project.videoClips.firstOrNull()
    GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), elevation = 6.dp, onClick = onOpen) {
        Box(Modifier.padding(6.dp).fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(15.dp))) {
            VideoThumbnail(
                probe = app.mediaProbe,
                uri = first?.sourceUri,
                atMs = first?.trimStartMs ?: 0,
                isImage = first?.isImage == true,
                modifier = Modifier.fillMaxSize(),
            )
            if (project.videoClips.isNotEmpty()) {
                Text(
                    formatDuration(TimelineMath.totalVideoMs(project.videoClips)),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Row(Modifier.padding(start = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(project.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formatDate(project.updatedAtEpochMs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Project options", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; onRename() })
                    DropdownMenuItem(text = { Text("Duplicate") }, onClick = { menu = false; onDuplicate() })
                    DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}
