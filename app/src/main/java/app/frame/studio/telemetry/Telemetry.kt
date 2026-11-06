package app.frame.studio.telemetry

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * The app's only door to Firebase Analytics and Crashlytics.
 *
 * - Inactive (every call is a no-op) until app/google-services.json is added and the build
 *   enables collection; debug builds send nothing by default.
 * - Never throws and never blocks: telemetry must not be able to break the editor.
 * - Sends no personal data: only event names, counts, sizes and settings. Values that look like
 *   files or links are dropped (see [TelemetryRules]).
 */
object Telemetry {
    private const val TAG = "Telemetry"

    @Volatile private var analytics: FirebaseAnalytics? = null
    @Volatile private var crashlytics: FirebaseCrashlytics? = null

    val isActive: Boolean get() = analytics != null

    fun init(context: Context) {
        try {
            // FirebaseInitProvider has already run; no app means no google-services.json.
            if (FirebaseApp.getApps(context).isEmpty()) {
                Log.i(TAG, "Firebase not configured; analytics and crash reports are off")
                return
            }
            analytics = FirebaseAnalytics.getInstance(context)
            crashlytics = FirebaseCrashlytics.getInstance()
        } catch (e: Throwable) {
            Log.w(TAG, "Firebase unavailable", e)
        }
    }

    /** An Analytics event; invalid names and personal-looking values are dropped. */
    fun event(name: String, vararg params: Pair<String, Any?>) {
        val a = analytics ?: return
        if (!TelemetryRules.isValidName(name)) {
            Log.w(TAG, "Dropping invalid event name $name")
            return
        }
        try {
            a.logEvent(name, bundleOf(TelemetryRules.cleanParams(params.toList())))
            crashlytics?.log("event $name")
        } catch (e: Throwable) {
            Log.w(TAG, "event failed", e)
        }
    }

    fun screen(route: String?) {
        val a = analytics ?: return
        val name = TelemetryRules.screenName(route) ?: return
        try {
            a.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, Bundle().apply {
                putString(FirebaseAnalytics.Param.SCREEN_NAME, name)
                putString(FirebaseAnalytics.Param.SCREEN_CLASS, name)
            })
            crashlytics?.log("screen $name")
        } catch (e: Throwable) {
            Log.w(TAG, "screen failed", e)
        }
    }

    /** A caught error worth knowing about (shows in Crashlytics as non-fatal). */
    fun nonFatal(where: String, error: Throwable) {
        val c = crashlytics ?: return
        try {
            c.setCustomKey("where", where)
            c.recordException(error)
        } catch (e: Throwable) {
            Log.w(TAG, "nonFatal failed", e)
        }
    }

    /** Context attached to every crash report (no personal data). */
    fun setKey(key: String, value: String) {
        try {
            crashlytics?.setCustomKey(key, value)
        } catch (e: Throwable) {
            Log.w(TAG, "setKey failed", e)
        }
    }

    /** A segment for Analytics reports, e.g. device_tier = low_end. */
    fun setUserProperty(name: String, value: String) {
        try {
            if (TelemetryRules.isValidName(name)) analytics?.setUserProperty(name, value.take(36))
        } catch (e: Throwable) {
            Log.w(TAG, "setUserProperty failed", e)
        }
    }

    private fun bundleOf(params: Map<String, Any>) = Bundle().apply {
        params.forEach { (k, v) ->
            when (v) {
                is Long -> putLong(k, v)
                is Double -> putDouble(k, v)
                else -> putString(k, v.toString())
            }
        }
    }
}
