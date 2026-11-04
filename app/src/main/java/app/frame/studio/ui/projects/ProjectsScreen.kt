package app.frame.studio.ui.projects

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.frame.studio.BuildConfig
import app.frame.studio.FrameApp
import app.frame.studio.R
import app.frame.studio.ads.AdsService
import app.frame.studio.ads.BannerAd
import app.frame.studio.domain.Project
import app.frame.studio.templates.Templates
import app.frame.studio.timeline.TimelineMath
import app.frame.studio.ui.templates.TemplateCard
import app.frame.studio.ui.common.ConfirmDialog
import app.frame.studio.ui.common.TextInputDialog
import app.frame.studio.ui.common.VideoThumbnail
import app.frame.studio.ui.common.formatDate
import app.frame.studio.ui.common.formatDuration
import app.frame.studio.ui.common.openUrl
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(
    app: FrameApp,
    onOpenProject: (String) -> Unit,
    onNewProject: (String) -> Unit,
    onOpenExports: () -> Unit,
    onOpenTemplates: () -> Unit,
    onOpenTemplate: (String) -> Unit,
) {
    val projects by app.repository.projects.collectAsStateWithLifecycle()
    val history by app.repository.history.collectAsStateWithLifecycle()
    val privacyRequired by AdsService.privacyOptionsRequired.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = LocalActivity.current

    var renameTarget by remember { mutableStateOf<Project?>(null) }
    var deleteTarget by remember { mutableStateOf<Project?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }

    fun newProject() {
        scope.launch { onNewProject(app.repository.create("Project ${projects.size + 1}").id) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Frame", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Privacy policy") },
                                onClick = {
                                    menuOpen = false
                                    context.openUrl(context.getString(R.string.privacy_policy_url))
                                },
                            )
                            if (privacyRequired) {
                                DropdownMenuItem(
                                    text = { Text("Privacy choices") },
                                    onClick = {
                                        menuOpen = false
                                        activity?.let(AdsService::showPrivacyOptions)
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("About") },
                                onClick = {
                                    menuOpen = false
                                    showAbout = true
                                },
                            )
                        }
                    }
                },
            )
        },
        bottomBar = { BannerAd() },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                HeroCard(onClick = ::newProject)
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                QuickRow(exportCount = history.size, onOpenExports = onOpenExports)
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionHeader("Templates", action = "See all", onAction = onOpenTemplates)
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    rowItems(Templates.featured, key = { it.id }) { t ->
                        TemplateCard(t, Modifier.width(132.dp)) { onOpenTemplate(t.id) }
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    if (projects.isEmpty()) "Projects" else "Projects · ${projects.size}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (projects.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { EmptyProjects() }
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
    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text("Frame") },
            text = {
                Text("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n\nA fast video editor. Exports never carry a watermark.")
            },
            confirmButton = { TextButton(onClick = { showAbout = false }) { Text("OK") } },
        )
    }
}

@Composable
private fun SectionHeader(title: String, action: String, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun HeroCard(onClick: () -> Unit) {
    val gradient = Brush.linearGradient(listOf(Color(0xFF1A73E8), Color(0xFF7B4DFF), Color(0xFFE5398F)))
    Box(
        Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(gradient)
            .clickable(onClick = onClick)
            .padding(20.dp),
    ) {
        Column(Modifier.align(Alignment.BottomStart)) {
            Text("New project", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
            Text("Videos, photos, music and text", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .size(52.dp)
                .clip(CircleShape)
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = Color(0xFF1A73E8), modifier = Modifier.size(30.dp))
        }
    }
}

@Composable
private fun QuickRow(exportCount: Int, onOpenExports: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onOpenExports)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.VideoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("My exports", style = MaterialTheme.typography.titleSmall)
            Text(
                if (exportCount == 0) "Finished videos show up here" else "$exportCount video${if (exportCount == 1) "" else "s"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyProjects() {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.Movie, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(8.dp))
        Text("Your projects will appear here", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onOpen)) {
        Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(16.dp))) {
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
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Row(Modifier.padding(start = 4.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(project.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    formatDate(project.updatedAtEpochMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Project options")
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
