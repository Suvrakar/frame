package app.frame.studio

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.animation.PathInterpolator
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.splashscreen.SplashScreenViewProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import app.frame.studio.ads.AdsService
import app.frame.studio.ads.RewardedAds
import app.frame.studio.export.ExportState
import app.frame.studio.ui.FrameNavHost
import app.frame.studio.ui.theme.FrameTheme
import app.frame.studio.ui.theme.isAppInDarkTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /** Set once the first real screen can draw (projects loaded). Read on the UI thread. */
    private var contentReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // The branded launch screen is the system's own starting window: it costs no start-up time.
        // It stays only while the app is genuinely not ready (and, on Android 12+, until the logo's
        // entrance has played), never longer than MAX_SPLASH_MS.
        val splash = installSplashScreen()
        val shownAt = SystemClock.uptimeMillis()
        splash.setKeepOnScreenCondition {
            val elapsed = SystemClock.uptimeMillis() - shownAt
            elapsed < MAX_SPLASH_MS && (!contentReady || (Build.VERSION.SDK_INT >= 31 && elapsed < LOGO_ENTRANCE_MS))
        }
        splash.setOnExitAnimationListener(::playSplashExit)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as FrameApp
        AdsService.gatherConsent(this)

        // Free users can watch a rewarded ad for a one-off Pro unlock; keep one ready.
        lifecycleScope.launch {
            AdsService.canShowAds.collect { ok -> if (ok && !app.pro.isPro.value) RewardedAds.preload(this@MainActivity) }
        }

        // Keep the screen awake while an export is running so the encoder is not interrupted.
        lifecycleScope.launch {
            app.exportManager.state.collect { state ->
                val busy = state is ExportState.Running || state is ExportState.Saving
                if (busy) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }

        setContent {
            val mode by app.settings.themeMode.collectAsStateWithLifecycle()
            val dark = isAppInDarkTheme(mode)
            // Status/navigation bar icons follow the app theme, not just the system setting.
            DisposableEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            FrameTheme(mode) {
                FrameNavHost(app, onReady = { contentReady = true })
            }
        }
    }

    /** The logo lifts slightly and fades while the backdrop dissolves into the app (~350 ms). */
    private fun playSplashExit(provider: SplashScreenViewProvider) {
        try {
            val exit = PathInterpolator(0.3f, 0f, 0.8f, 0.15f) // Material "emphasized accelerate"
            provider.iconView.animate()
                .scaleX(1.12f).scaleY(1.12f).alpha(0f)
                .setDuration(EXIT_MS).setInterpolator(exit)
                .start()
            provider.view.animate()
                .alpha(0f)
                .setStartDelay(EXIT_MS / 4).setDuration(EXIT_MS).setInterpolator(exit)
                .withEndAction { provider.remove() }
                .start()
        } catch (e: Exception) {
            // Some OEM splash windows expose no icon view: never let the exit block the app.
            provider.remove()
        }
    }

    private companion object {
        /** Matches windowSplashScreenAnimationDuration (values-v31/themes.xml). */
        const val LOGO_ENTRANCE_MS = 800L
        const val MAX_SPLASH_MS = 2_000L
        const val EXIT_MS = 280L
    }
}
