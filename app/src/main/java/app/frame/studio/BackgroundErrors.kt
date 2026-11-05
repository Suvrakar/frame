package app.frame.studio

import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler

/**
 * Last-resort handler for fire-and-forget work in app-wide scopes (autosave on exit, export
 * bookkeeping, cache trimming): an unexpected failure there is logged instead of killing the app
 * while the user is mid-edit. Expected failures must still be handled where they happen; this only
 * catches bugs. Report these as non-fatal errors once crash reporting is added.
 */
val BackgroundErrors = CoroutineExceptionHandler { _, e ->
    Log.e("Frame", "Uncaught error in background work", e)
}
