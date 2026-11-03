package app.frame.studio

import android.app.Application
import app.frame.studio.data.MediaProbe
import app.frame.studio.data.ProjectRepository
import app.frame.studio.export.ExportManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class FrameApp : Application() {

    lateinit var repository: ProjectRepository
        private set
    lateinit var mediaProbe: MediaProbe
        private set
    lateinit var exportManager: ExportManager
        private set

    /** Outlives screens; used for last-moment saves when an editor closes. */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        repository = ProjectRepository(this)
        mediaProbe = MediaProbe(this)
        exportManager = ExportManager(this, repository)
        // Ads are initialized by AdsService once the user's consent status is known.
    }
}
