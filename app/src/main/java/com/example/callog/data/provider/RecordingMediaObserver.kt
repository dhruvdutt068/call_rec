package com.example.callog.data.provider

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.example.callog.core.diagnostics.DeveloperLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ContentObserver monitoring changes to MediaStore.Audio.Media.EXTERNAL_CONTENT_URI.
 *
 * Implements Phase 6.2: Real-time recording discovery:
 * - Debounces rapid ContentObserver callbacks
 * - Filters redundant events
 * - Safe registration/unregistration lifecycle
 * - Triggers asynchronous reconciliation scanning
 */
@Singleton
class RecordingMediaObserver @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "RecordingMediaObserver"
        private const val DEBOUNCE_DELAY_MS = 1200L
    }

    private val isRegistered = AtomicBoolean(false)
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var debounceJob: Job? = null

    private var onMediaChangedListener: (() -> Unit)? = null

    private val contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            DeveloperLogger.info(TAG, "MediaStore audio changed: uri=$uri, selfChange=$selfChange")
            handleMediaChange()
        }
    }

    /**
     * Sets listener invoked when new audio media changes are detected.
     */
    fun setOnMediaChangedListener(listener: () -> Unit) {
        this.onMediaChangedListener = listener
    }

    /**
     * Registers the observer for audio media uri changes.
     */
    fun register() {
        if (isRegistered.compareAndSet(false, true)) {
            try {
                context.contentResolver.registerContentObserver(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    true,
                    contentObserver
                )
                DeveloperLogger.info(TAG, "Successfully registered MediaStore ContentObserver")
            } catch (e: Exception) {
                isRegistered.set(false)
                DeveloperLogger.error(TAG, "Failed to register ContentObserver: ${e.message}", e)
            }
        }
    }

    /**
     * Unregisters the observer when no longer needed.
     */
    fun unregister() {
        if (isRegistered.compareAndSet(true, false)) {
            try {
                context.contentResolver.unregisterContentObserver(contentObserver)
                debounceJob?.cancel()
                DeveloperLogger.info(TAG, "Successfully unregistered MediaStore ContentObserver")
            } catch (e: Exception) {
                DeveloperLogger.warning(TAG, "Error unregistering ContentObserver: ${e.message}")
            }
        }
    }

    private fun handleMediaChange() {
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(DEBOUNCE_DELAY_MS)
            DeveloperLogger.info(TAG, "Debounce complete. Dispatching media changed event.")
            try {
                onMediaChangedListener?.invoke()
            } catch (e: Exception) {
                DeveloperLogger.error(TAG, "Error in onMediaChangedListener: ${e.message}", e)
            }
        }
    }
}
