package app.frame.studio

import app.frame.studio.telemetry.Telemetry
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler

/**
 * Last-resort handler for fire-and-forget work in app-wide scopes (autosave on exit, export
 * bookkeeping, cache trimming): an unexpected failure there is logged instead of killing the app
 * while the user is mid-edit. Expected failures must still be handled where they happen; this only
 * catches bugs, and reports them to Crashlytics as non-fatal errors.
 */
val BackgroundErrors = CoroutineExceptionHandler { _, e ->
    Log.e("Frame", "Uncaught error in background work", e)
    Telemetry.nonFatal("background", e)
}
