package app.frame.studio.pro

import app.frame.studio.domain.ExportHeight
import app.frame.studio.domain.Project

/** Which export settings are part of Frame Pro. Pure so it can be unit tested. */
object ProPolicy {
    const val FREE_MAX_HEIGHT = 1080
    const val FREE_MAX_FPS = 30

    fun isProHeight(height: ExportHeight): Boolean = height.pixels > FREE_MAX_HEIGHT

    fun isProFps(fps: Int): Boolean = fps > FREE_MAX_FPS

    fun exportNeedsPro(project: Project): Boolean =
        isProHeight(project.exportHeight) || isProFps(project.exportFps)
}
