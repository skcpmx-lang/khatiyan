package com.shohan.khatiyan.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.shohan.khatiyan.core.Money
import com.shohan.khatiyan.security.PinCrypto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

data class AppSettings(
    val onboarded: Boolean = false,
    val userName: String = "",
    val currencySymbol: String = Money.DEFAULT_SYMBOL,
    val notificationsEnabled: Boolean = true,
    /** Hour of day (local) for the daily reminder worker. */
    val reminderHour: Int = 9,
    val secureScreen: Boolean = true,
    val appLockEnabled: Boolean = false,
    val biometricEnabled: Boolean = false,
)

private val Context.khatiyanDataStore by preferencesDataStore(name = "khatiyan_settings")

/**
 * All app preferences (DataStore). PIN material is read/written here but never
 * exposed through the reactive [AppSettings] stream on purpose.
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val NAME = stringPreferencesKey("user_name")
        val SYMBOL = stringPreferencesKey("currency_symbol")
        val NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
        val REMINDER_HOUR = intPreferencesKey("reminder_hour")
        val SECURE = booleanPreferencesKey("secure_screen")
        val LOCK = booleanPreferencesKey("app_lock_enabled")
        val BIOMETRIC = booleanPreferencesKey("biometric_enabled")
        val PIN_SALT = stringPreferencesKey("pin_salt")
        val PIN_HASH = stringPreferencesKey("pin_hash")
    }

    val settings: Flow<AppSettings> = context.khatiyanDataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { p ->
            AppSettings(
                onboarded = p[Keys.ONBOARDED] ?: false,
                userName = p[Keys.NAME] ?: "",
                currencySymbol = p[Keys.SYMBOL] ?: Money.DEFAULT_SYMBOL,
                notificationsEnabled = p[Keys.NOTIFICATIONS] ?: true,
                reminderHour = p[Keys.REMINDER_HOUR] ?: 9,
                secureScreen = p[Keys.SECURE] ?: true,
                appLockEnabled = p[Keys.LOCK] ?: false,
                biometricEnabled = p[Keys.BIOMETRIC] ?: false,
            )
        }

    suspend fun current(): AppSettings = settings.first()

    suspend fun completeOnboarding(name: String, currencySymbol: String) {
        context.khatiyanDataStore.edit {
            it[Keys.NAME] = name.trim()
            it[Keys.SYMBOL] = currencySymbol
            it[Keys.ONBOARDED] = true
        }
    }

    suspend fun setUserName(name: String) {
        context.khatiyanDataStore.edit { it[Keys.NAME] = name.trim() }
    }

    suspend fun setCurrencySymbol(symbol: String) {
        context.khatiyanDataStore.edit { it[Keys.SYMBOL] = symbol }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.khatiyanDataStore.edit { it[Keys.NOTIFICATIONS] = enabled }
    }

    suspend fun setReminderHour(hour: Int) {
        context.khatiyanDataStore.edit { it[Keys.REMINDER_HOUR] = hour.coerceIn(0, 23) }
    }

    suspend fun setSecureScreen(secure: Boolean) {
        context.khatiyanDataStore.edit { it[Keys.SECURE] = secure }
    }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        context.khatiyanDataStore.edit { it[Keys.LOCK] = enabled }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.khatiyanDataStore.edit { it[Keys.BIOMETRIC] = enabled }
    }

    // ---- PIN (never streamed to the UI) ---------------------------------------------------

    suspend fun setPin(pin: String) {
        val secret = PinCrypto.create(pin)
        context.khatiyanDataStore.edit {
            it[Keys.PIN_SALT] = secret.saltB64
            it[Keys.PIN_HASH] = secret.hashB64
        }
    }

    suspend fun clearPin() {
        context.khatiyanDataStore.edit {
            it.remove(Keys.PIN_SALT)
            it.remove(Keys.PIN_HASH)
        }
    }

    suspend fun hasPin(): Boolean {
        val p = context.khatiyanDataStore.data.first()
        return !p[Keys.PIN_HASH].isNullOrBlank()
    }

    suspend fun verifyPin(pin: String): Boolean {
        val p = context.khatiyanDataStore.data.first()
        val salt = p[Keys.PIN_SALT] ?: return false
        val hash = p[Keys.PIN_HASH] ?: return false
        return PinCrypto.verify(pin, salt, hash)
    }
}
