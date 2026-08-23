package com.owlmedia.racecontrol.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("racecontrol_settings")

/**
 * User settings.
 *
 * Keys and defaults deliberately mirror the iOS `@AppStorage` values so the two
 * apps behave identically out of the box: day-before ON, one-hour OFF,
 * fifteen-minute ON; practice OFF, qualifying/sprint/race ON.
 */
@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val BASE_URL = stringPreferencesKey("api_base_url")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val NOTIFY_DAY_BEFORE = booleanPreferencesKey("notify_day_before")
        val NOTIFY_1_HOUR = booleanPreferencesKey("notify_1hour")
        val NOTIFY_15_MIN = booleanPreferencesKey("notify_15min")
        val NOTIFY_PRACTICE = booleanPreferencesKey("notify_practice")
        val NOTIFY_QUALIFYING = booleanPreferencesKey("notify_qualifying")
        val NOTIFY_SPRINT = booleanPreferencesKey("notify_sprint")
        val NOTIFY_RACE = booleanPreferencesKey("notify_race")
    }

    companion object {
        /**
         * The emulator's alias for the host machine, the Android counterpart of
         * what `localhost` does for the iOS Simulator. A physical device needs
         * the machine's LAN address, set in Settings.
         */
        const val DEFAULT_BASE_URL = "http://10.0.2.2:8000"
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            baseUrl = prefs[Keys.BASE_URL] ?: DEFAULT_BASE_URL,
            notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: false,
            notifyDayBefore = prefs[Keys.NOTIFY_DAY_BEFORE] ?: true,
            notify1Hour = prefs[Keys.NOTIFY_1_HOUR] ?: false,
            notify15Min = prefs[Keys.NOTIFY_15_MIN] ?: true,
            notifyPractice = prefs[Keys.NOTIFY_PRACTICE] ?: false,
            notifyQualifying = prefs[Keys.NOTIFY_QUALIFYING] ?: true,
            notifySprint = prefs[Keys.NOTIFY_SPRINT] ?: true,
            notifyRace = prefs[Keys.NOTIFY_RACE] ?: true,
        )
    }

    suspend fun setBaseUrl(value: String) = edit { it[Keys.BASE_URL] = value.trim() }
    suspend fun setNotificationsEnabled(value: Boolean) = edit { it[Keys.NOTIFICATIONS_ENABLED] = value }
    suspend fun setNotifyDayBefore(value: Boolean) = edit { it[Keys.NOTIFY_DAY_BEFORE] = value }
    suspend fun setNotify1Hour(value: Boolean) = edit { it[Keys.NOTIFY_1_HOUR] = value }
    suspend fun setNotify15Min(value: Boolean) = edit { it[Keys.NOTIFY_15_MIN] = value }
    suspend fun setNotifyPractice(value: Boolean) = edit { it[Keys.NOTIFY_PRACTICE] = value }
    suspend fun setNotifyQualifying(value: Boolean) = edit { it[Keys.NOTIFY_QUALIFYING] = value }
    suspend fun setNotifySprint(value: Boolean) = edit { it[Keys.NOTIFY_SPRINT] = value }
    suspend fun setNotifyRace(value: Boolean) = edit { it[Keys.NOTIFY_RACE] = value }

    suspend fun resetBaseUrl() = setBaseUrl(DEFAULT_BASE_URL)

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}

data class AppSettings(
    val baseUrl: String = SettingsDataStore.DEFAULT_BASE_URL,
    val notificationsEnabled: Boolean = false,
    val notifyDayBefore: Boolean = true,
    val notify1Hour: Boolean = false,
    val notify15Min: Boolean = true,
    val notifyPractice: Boolean = false,
    val notifyQualifying: Boolean = true,
    val notifySprint: Boolean = true,
    val notifyRace: Boolean = true,
) {
    /** Whether a given FastF1 session identifier should raise a reminder. */
    fun wantsSession(identifier: String?): Boolean = when (identifier) {
        "FP1", "FP2", "FP3" -> notifyPractice
        "Q" -> notifyQualifying
        "S", "SQ", "SS" -> notifySprint
        "R" -> notifyRace
        else -> false
    }
}
