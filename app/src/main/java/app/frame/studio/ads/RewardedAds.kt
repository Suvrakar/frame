package app.frame.studio.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import app.frame.studio.R
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** "Watch an ad to use this once": keeps one rewarded ad preloaded after consent allows ads. */
object RewardedAds {
    private const val TAG = "RewardedAds"
    private var ad: RewardedAd? = null
    private var loading = false

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    fun preload(context: Context) {
        if (ad != null || loading || !AdsService.canShowAds.value) return
        loading = true
        RewardedAd.load(
            context.applicationContext,
            context.getString(R.string.admob_rewarded_id),
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(loaded: RewardedAd) {
                    ad = loaded
                    loading = false
                    _ready.value = true
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(TAG, "Rewarded ad failed: ${error.message}")
                    loading = false
                    _ready.value = false
                }
            },
        )
    }

    /** Shows the ad; [onReward] runs only if the user watched long enough to earn it. */
    fun show(activity: Activity, onReward: () -> Unit, onClosed: () -> Unit = {}) {
        val current = ad ?: run {
            preload(activity)
            onClosed()
            return
        }
        ad = null
        _ready.value = false
        current.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                onClosed()
                preload(activity)
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                onClosed()
                preload(activity)
            }
        }
        current.show(activity) { onReward() }
    }
}
