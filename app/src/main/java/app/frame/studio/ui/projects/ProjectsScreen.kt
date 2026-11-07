package app.frame.studio.ui.projects

import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import app.frame.studio.domain.CanvasAspect
import app.frame.studio.export.SocialPresets
import app.frame.studio.domain.CanvasFill
import app.frame.studio.telemetry.Telemetry
import app.frame.studio.telemetry.AppEvents
import app.frame.studio.ui.common.bounceClick
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
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
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
import app.frame.studio.ui.templates.TemplateTile
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
    onOpenMagic: () -> Unit = {},
    onSeeAllProjects: () -> Unit = {},
) {
    val projects by app.repository.projects.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val actions = remember { ProjectActions() }

    var choosingCanvas by remember { mutableStateOf(false) }
    fun newProject() {
        choosingCanvas = true
    }
    if (choosingCanvas) {
        NewProjectSheet(
            lastChoiceKey = app.settings.lastCanvas,
            onDismiss = { choosingCanvas = false },
        ) { choice, customRatio ->
            choosingCanvas = false
            val key = choice?.key ?: CUSTOM_KEY
            app.settings.lastCanvas = key
            scope.launch {
                val blank = app.repository.create("Project ${projects.size + 1}")
                val shaped = when {
                    customRatio != null -> blank.copy(aspect = CanvasAspect.CUSTOM, customRatio = customRatio)
                    choice?.preset != null -> SocialPresets.apply(blank, choice.preset, app.device::clampExportHeight)
                    else -> blank
                }.copy(canvasFill = CanvasFill.BLUR)
                app.repository.save(shaped)
                AppEvents.projectCreated("blank")
                Telemetry.event("canvas_chosen", "canvas" to key)
                onNewProject(shaped.id)
            }
        }
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
        item(span = { GridItemSpan(maxLineSpan) }) { MagicCard(onClick = onOpenMagic) }
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
                        TemplateTile(app.templatePreviews, t, Modifier.width(118.dp)) { onOpenTemplate(t.id) }
                    }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            if (projects.isEmpty()) {
                SectionHeader("Your projects")
            } else {
                SectionHeader(
                    "Recent projects",
                    action = if (projects.size > RECENT_ON_HOME) "See all · ${projects.size}" else "See all",
                    onAction = onSeeAllProjects,
                )
            }
        }
        if (projects.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { EmptyProjects(onStart = ::newProject) }
        } else {
            // Home stays short: the newest few scroll sideways; everything else is under "See all".
            item(span = { GridItemSpan(maxLineSpan) }) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                    rowItems(projects.take(RECENT_ON_HOME), key = { it.id }) { project ->
                        ProjectCard(
                            app = app,
                            project = project,
                            onOpen = { onOpenProject(project.id) },
                            onRename = { actions.rename = project },
                            onDuplicate = { scope.launch { app.repository.duplicate(project.id) } },
                            onDelete = { actions.delete = project },
                            modifier = Modifier.width(168.dp),
                        )
                    }
                    if (projects.size > RECENT_ON_HOME) {
                        item { SeeAllTile(projects.size - RECENT_ON_HOME, onSeeAllProjects) }
                    }
                }
            }
        }
    }

    ProjectActionDialogs(app, actions)
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
private fun MagicCard(onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    val glass = LocalGlass.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(glass.fill)
            .border(1.dp, glass.border, shape)
            .bounceClick(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF5B63F0), Color(0xFFB45BF0), Color(0xFFF07A5B)))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = Color.White)
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Magic Movie", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "PRO",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF8E5BF0))
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }
            Text(
                "Pick photos and a song. Frame makes the movie.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

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
            .bounceClick(onClick = onStart)
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
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    val first = project.videoClips.firstOrNull()
    GlassCard(modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), elevation = 6.dp, onClick = onOpen) {
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

/** How many projects Home shows before "See all". */
private const val RECENT_ON_HOME = 10

/** Rename / delete prompts shared by Home and All projects. */
class ProjectActions {
    var rename by mutableStateOf<Project?>(null)
    var delete by mutableStateOf<Project?>(null)
}

@Composable
fun ProjectActionDialogs(app: FrameApp, actions: ProjectActions) {
    val scope = rememberCoroutineScope()
    actions.rename?.let { project ->
        TextInputDialog(
            title = "Rename project",
            initial = project.name,
            confirmLabel = "Rename",
            onDismiss = { actions.rename = null },
            onConfirm = { name ->
                actions.rename = null
                scope.launch { app.repository.save(project.copy(name = name)) }
            },
        )
    }
    actions.delete?.let { project ->
        ConfirmDialog(
            title = "Delete \"${project.name}\"?",
            message = "The project will be removed. Your original videos and finished exports are not affected.",
            confirmLabel = "Delete",
            onDismiss = { actions.delete = null },
            onConfirm = {
                actions.delete = null
                scope.launch { app.repository.delete(project.id) }
            },
        )
    }
}

/** The last tile of the Recent row: how many more, and a way to see them. */
@Composable
private fun SeeAllTile(more: Int, onClick: () -> Unit) {
    GlassCard(Modifier.width(120.dp), shape = RoundedCornerShape(20.dp), elevation = 6.dp, onClick = onClick) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text("+$more more", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
            Text("See all", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private enum class ProjectSort(val label: String) { RECENT("Recent"), NAME("A–Z") }

/** Every project: search, sort, and the same card menu as Home. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AllProjectsScreen(app: FrameApp, onBack: () -> Unit, onOpenProject: (String) -> Unit) {
    val projects by app.repository.projects.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val actions = remember { ProjectActions() }
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(ProjectSort.RECENT) }
    val shown = remember(projects, query, sort) {
        val q = query.trim()
        projects.filter { q.isEmpty() || it.name.contains(q, ignoreCase = true) }
            .let { list -> if (sort == ProjectSort.NAME) list.sortedBy { it.name.lowercase() } else list }
    }
    androidx.compose.material3.Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text("All projects · ${projects.size}") },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding(), bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    androidx.compose.material3.OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search projects") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProjectSort.entries.forEach { s ->
                            androidx.compose.material3.FilterChip(selected = sort == s, onClick = { sort = s }, label = { Text(s.label) })
                        }
                    }
                }
            }
            if (shown.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        if (projects.isEmpty()) "No projects yet." else "No projects match \"$query\".",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 32.dp),
                    )
                }
            }
            items(shown, key = { it.id }) { project ->
                ProjectCard(
                    app = app,
                    project = project,
                    onOpen = { onOpenProject(project.id) },
                    onRename = { actions.rename = project },
                    onDuplicate = { scope.launch { app.repository.duplicate(project.id) } },
                    onDelete = { actions.delete = project },
                )
            }
        }
    }
    ProjectActionDialogs(app, actions)
}
