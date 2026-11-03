package app.frame.studio.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.ViewGroup
import app.frame.studio.R
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Gathers GDPR/US-state consent through Google's UMP SDK and only then initializes the Mobile Ads
 * SDK. Banners are rendered only once [canShowAds] is true.
 */
object AdsService {
    private const val TAG = "AdsService"
    private val initialized = AtomicBoolean(false)
    private val consentRequested = AtomicBoolean(false)

    private val _canShowAds = MutableStateFlow(false)
    val canShowAds: StateFlow<Boolean> = _canShowAds.asStateFlow()

    private val _privacyOptionsRequired = MutableStateFlow(false)
    val privacyOptionsRequired: StateFlow<Boolean> = _privacyOptionsRequired.asStateFlow()

    private lateinit var consentInformation: ConsentInformation

    /** Call from the launcher activity's onCreate. Safe to call repeatedly. */
    fun gatherConsent(activity: Activity) {
        consentInformation = UserMessagingPlatform.getConsentInformation(activity)
        // Consent from a previous session lets ads start loading immediately.
        if (consentInformation.canRequestAds()) initialize(activity)
        if (!consentRequested.compareAndSet(false, true)) return

        val params = ConsentRequestParameters.Builder().apply {
            if (app.frame.studio.BuildConfig.DEBUG) {
                setConsentDebugSettings(
                    ConsentDebugSettings.Builder(activity).build(),
                )
            }
        }.build()

        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (formError != null) Log.w(TAG, "Consent form: ${formError.message}")
                    onConsentResolved(activity)
                }
            },
            { requestError ->
                Log.w(TAG, "Consent info update failed: ${requestError.message}")
                onConsentResolved(activity)
            },
        )
    }

    private fun onConsentResolved(activity: Activity) {
        _privacyOptionsRequired.value = consentInformation.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        if (consentInformation.canRequestAds()) initialize(activity)
    }

    /** Re-opens the consent form so users can change their choice (required in the EEA/UK). */
    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError ->
            if (formError != null) Log.w(TAG, "Privacy options: ${formError.message}")
            if (::consentInformation.isInitialized) {
                _canShowAds.value = consentInformation.canRequestAds()
                if (consentInformation.canRequestAds()) initialize(activity)
            }
        }
    }

    private fun initialize(context: Context) {
        if (initialized.compareAndSet(false, true)) {
            MobileAds.initialize(context.applicationContext) { _canShowAds.value = true }
        } else {
            _canShowAds.value = true
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
