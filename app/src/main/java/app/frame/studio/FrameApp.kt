package app.frame.studio

import android.app.Application
import app.frame.studio.ads.AdsService

class FrameApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AdsService.initialize(this)
    }
}
