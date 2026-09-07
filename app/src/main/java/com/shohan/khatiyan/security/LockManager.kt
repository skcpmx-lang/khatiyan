package com.shohan.khatiyan.security

import android.os.SystemClock
import androidx.compose.runtime.mutableStateOf
import com.shohan.khatiyan.data.settings.SettingsRepository

/**
 * In-memory lock state (Phase 29). PIN/biometric unlock happens once per cold
 * start and after ≥2 minutes away — long enough that reading a long bill or
 * a phone call doesn't annoy, short enough to be a real gate. Nothing about
 * the PIN touches disk beyond its PBKDF2 salt/hash in DataStore.
 */
class LockManager(@Suppress("unused") private val settings: SettingsRepository) {

    companion object {
        const val LOCK_AFTER_BACKGROUND_MS = 120_000L
    }

    /** Compose-observable so the root can re-render on lock transitions. */
    val locked = mutableStateOf(false)

    fun onColdStart(settings: com.shohan.khatiyan.data.settings.AppSettings) {
        locked.value = settings.appLockEnabled
    }

    fun onBackground(pausedAtElapsed: Long) {
        // remember only; decision happens on resume
        lastPausedElapsed = pausedAtElapsed
    }

    private var lastPausedElapsed: Long = 0

    fun onResume(settings: com.shohan.khatiyan.data.settings.AppSettings) {
        if (!settings.appLockEnabled) {
            locked.value = false
            return
        }
        if (lastPausedElapsed == 0L) {
            locked.value = true
            return
        }
        val away = SystemClock.elapsedRealtime() - lastPausedElapsed
        if (away >= LOCK_AFTER_BACKGROUND_MS) locked.value = true
    }

    fun unlock() {
        locked.value = false
    }
}
