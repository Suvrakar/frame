package app.frame.studio

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import app.frame.studio.ads.AdsService
import app.frame.studio.export.ExportState
import app.frame.studio.ui.FrameNavHost
import app.frame.studio.ui.theme.FrameTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as FrameApp
        AdsService.gatherConsent(this)

        // Keep the screen awake while an export is running so the encoder is not interrupted.
        lifecycleScope.launch {
            app.exportManager.state.collect { state ->
                val busy = state is ExportState.Running || state is ExportState.Saving
                if (busy) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }

        setContent {
            FrameTheme {
                FrameNavHost(app)
            }
        }
    }
}
