package app.frame.studio.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import app.frame.studio.ads.BannerAd
import app.frame.studio.templates.TemplateCategory
import app.frame.studio.ui.common.GlassBackground
import app.frame.studio.ui.common.GlassBottomBar
import app.frame.studio.ui.common.NavTab
import app.frame.studio.ui.settings.SettingsScreen
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.frame.studio.FrameApp
import app.frame.studio.ui.common.LoadingScreen
import app.frame.studio.ui.editor.EditorScreen
import app.frame.studio.ui.export.ExportScreen
import app.frame.studio.ui.exports.ExportsScreen
import app.frame.studio.ui.projects.ProjectsScreen
import app.frame.studio.ui.templates.TemplateDetailScreen
import app.frame.studio.ui.templates.TemplatesScreen

object Routes {
    const val PROJECTS = "projects"
    const val EDITOR = "editor/{projectId}?import={import}"
    const val EXPORT = "export/{projectId}"
    const val EXPORTS = "exports"
    const val TEMPLATES = "templates?category={category}"
    const val SETTINGS = "settings"

    fun templates(category: TemplateCategory? = null) = "templates?category=${category?.name.orEmpty()}"
    const val TEMPLATE = "template/{templateId}"

    fun template(id: String) = "template/$id"

    fun editor(id: String, autoImport: Boolean = false) = "editor/$id?import=$autoImport"
    fun export(id: String) = "export/$id"
}

@Composable
fun FrameNavHost(app: FrameApp) {
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        app.repository.load()
        ready = true
    }
    if (!ready) {
        LoadingScreen()
        return
    }

    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.PROJECTS) {
        composable(Routes.PROJECTS) {
            TabShell(nav, Routes.PROJECTS) {
                ProjectsScreen(
                    app = app,
                    onOpenProject = { nav.navigate(Routes.editor(it)) },
                    onNewProject = { nav.navigate(Routes.editor(it, autoImport = true)) },
                    onOpenExports = { nav.switchTab(Routes.EXPORTS) },
                    onOpenTemplates = { nav.switchTab(Routes.templates(it)) },
                    onOpenTemplate = { nav.navigate(Routes.template(it)) },
                )
            }
        }
        composable(
            Routes.TEMPLATES,
            arguments = listOf(
                navArgument("category") {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) { entry ->
            val category = entry.arguments?.getString("category")
                ?.takeIf { it.isNotBlank() }
                ?.let { runCatching { TemplateCategory.valueOf(it) }.getOrNull() }
            TabShell(nav, Routes.TEMPLATES) {
                TemplatesScreen(onBack = null, onOpen = { nav.navigate(Routes.template(it)) }, initialCategory = category)
            }
        }
        composable(Routes.EXPORTS) {
            TabShell(nav, Routes.EXPORTS) { ExportsScreen(app = app, onBack = null) }
        }
        composable(Routes.SETTINGS) {
            TabShell(nav, Routes.SETTINGS) { SettingsScreen(app) }
        }
        composable(
            Routes.TEMPLATE,
            arguments = listOf(navArgument("templateId") { type = NavType.StringType }),
        ) { entry ->
            TemplateDetailScreen(
                app = app,
                templateId = entry.arguments?.getString("templateId").orEmpty(),
                onBack = { nav.popBackStack() },
                onCreated = { projectId ->
                    nav.navigate(Routes.editor(projectId)) { popUpTo(Routes.PROJECTS) }
                },
            )
        }
        composable(
            Routes.EDITOR,
            arguments = listOf(
                navArgument("projectId") { type = NavType.StringType },
                navArgument("import") {
                    type = NavType.BoolType
                    defaultValue = false
                },
            ),
        ) { entry ->
            val id = entry.arguments?.getString("projectId").orEmpty()
            EditorScreen(
                app = app,
                projectId = id,
                autoImport = entry.arguments?.getBoolean("import") ?: false,
                onBack = { nav.popBackStack() },
                onExport = { nav.navigate(Routes.export(id)) },
            )
        }
        composable(
            Routes.EXPORT,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType }),
        ) { entry ->
            val id = entry.arguments?.getString("projectId").orEmpty()
            ExportScreen(
                app = app,
                projectId = id,
                onBack = { nav.popBackStack() },
                onOpenExports = { nav.switchTab(Routes.EXPORTS) },
            )
        }
    }
}

private val TABS = listOf(
    NavTab(Routes.PROJECTS, "Home", Icons.Outlined.Home, Icons.Filled.Home),
    NavTab(Routes.TEMPLATES, "Templates", Icons.Outlined.Dashboard, Icons.Filled.Dashboard),
    NavTab(Routes.EXPORTS, "Exports", Icons.Outlined.VideoLibrary, Icons.Filled.VideoLibrary),
    NavTab(Routes.SETTINGS, "Settings", Icons.Outlined.Settings, Icons.Filled.Settings),
)

/** Top-level tabs keep one back stack entry each, like Google's apps. */
private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(Routes.PROJECTS) { saveState = true }
        launchSingleTop = true
        restoreState = !route.startsWith("templates?category=") || route == Routes.templates(null)
    }
}

/** Glass background, content, banner and the floating bottom navigation shared by all tabs. */
@Composable
private fun TabShell(nav: NavHostController, current: String, content: @Composable () -> Unit) {
    GlassBackground {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) { content() }
            BannerAd(insetNavigationBar = false)
            GlassBottomBar(
                tabs = TABS,
                current = current,
                onSelect = { tab ->
                    if (tab.route != current) {
                        nav.switchTab(if (tab.route == Routes.TEMPLATES) Routes.templates(null) else tab.route)
                    }
                },
            )
        }
    }
}
