package app.frame.studio

import android.app.Application
import app.frame.studio.data.MediaProbe
import app.frame.studio.device.DeviceProfile
import app.frame.studio.data.ProjectRepository
import app.frame.studio.export.ExportManager
import app.frame.studio.templates.TemplateBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class FrameApp : Application() {

    lateinit var device: DeviceProfile
        private set
    lateinit var repository: ProjectRepository
        private set
    lateinit var mediaProbe: MediaProbe
        private set
    lateinit var exportManager: ExportManager
        private set
    lateinit var templateBuilder: TemplateBuilder
        private set

    /** Outlives screens; used for last-moment saves when an editor closes. */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        device = DeviceProfile(this)
        repository = ProjectRepository(this, device.defaultExportHeight)
        mediaProbe = MediaProbe(this, device)
        exportManager = ExportManager(this, repository, device)
        templateBuilder = TemplateBuilder(this, repository, mediaProbe, device)
        // Ads are initialized by AdsService once the user's consent status is known.
    }
}
