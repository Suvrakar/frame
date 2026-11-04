package app.frame.studio

import android.app.Application
import android.content.ComponentCallbacks2
import app.frame.studio.data.ImageNormalizer
import app.frame.studio.data.MediaProbe
import app.frame.studio.device.DeviceProfile
import app.frame.studio.data.ProjectRepository
import app.frame.studio.export.ExportManager
import app.frame.studio.templates.TemplateBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FrameApp : Application() {

    lateinit var device: DeviceProfile
        private set
    lateinit var repository: ProjectRepository
        private set
    lateinit var mediaProbe: MediaProbe
        private set
    lateinit var imageNormalizer: ImageNormalizer
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
        imageNormalizer = ImageNormalizer(this, device.maxImageLongSide)
        exportManager = ExportManager(this, repository, device)
        templateBuilder = TemplateBuilder(this, repository, mediaProbe, imageNormalizer, device)
        // Ads are initialized by AdsService once the user's consent status is known.
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        @Suppress("DEPRECATION")
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            applicationScope.launch { mediaProbe.trimMemory() }
        }
    }
}
