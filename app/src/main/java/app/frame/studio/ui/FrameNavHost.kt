package app.frame.studio.ui

import androidx.compose.runtime.Composable
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

object Routes {
    const val PROJECTS = "projects"
    const val EDITOR = "editor/{projectId}"
    const val EXPORT = "export/{projectId}"
    const val EXPORTS = "exports"

    fun editor(id: String) = "editor/$id"
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
            ProjectsScreen(
                app = app,
                onOpenProject = { nav.navigate(Routes.editor(it)) },
                onOpenExports = { nav.navigate(Routes.EXPORTS) },
            )
        }
        composable(
            Routes.EDITOR,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType }),
        ) { entry ->
            val id = entry.arguments?.getString("projectId").orEmpty()
            EditorScreen(
                app = app,
                projectId = id,
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
                onOpenExports = {
                    nav.navigate(Routes.EXPORTS) { popUpTo(Routes.PROJECTS) }
                },
            )
        }
        composable(Routes.EXPORTS) {
            ExportsScreen(app = app, onBack = { nav.popBackStack() })
        }
    }
}
