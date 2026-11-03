package app.frame.studio.ads

import android.app.Activity
import android.content.Context
import android.view.ViewGroup
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import app.frame.studio.R
import java.util.concurrent.atomic.AtomicBoolean

object AdsService {
    private val initialized = AtomicBoolean(false)

    fun initialize(context: Context) {
        if (initialized.compareAndSet(false, true)) {
            MobileAds.initialize(context.applicationContext)
        }
    }

    fun createBanner(activity: Activity): AdView {
        val adView = AdView(activity)
        adView.setAdSize(
            AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(
                activity,
                adWidthDp(activity),
            ),
        )
        adView.adUnitId = activity.getString(R.string.admob_banner_id)
        adView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        adView.loadAd(AdRequest.Builder().build())
        return adView
    }

    private fun adWidthDp(activity: Activity): Int {
        val displayMetrics = activity.resources.displayMetrics
        val widthPx = displayMetrics.widthPixels.toFloat()
        return (widthPx / displayMetrics.density).toInt().coerceAtLeast(320)
    }
}
