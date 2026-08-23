package com.owlmedia.racecontrol.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.owlmedia.racecontrol.data.local.AppSettings
import com.owlmedia.racecontrol.data.local.SecureTokenStore
import com.owlmedia.racecontrol.data.local.SettingsDataStore
import com.owlmedia.racecontrol.data.remote.ApiException
import com.owlmedia.racecontrol.data.repository.RaceControlRepository
import com.owlmedia.racecontrol.notifications.ReminderRefreshScheduler
import com.owlmedia.racecontrol.notifications.SessionReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ConnectionStatus {
    data object Unknown : ConnectionStatus
    data object Checking : ConnectionStatus
    data object Ok : ConnectionStatus
    data class Failed(val message: String) : ConnectionStatus
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsStore: SettingsDataStore,
    private val tokenStore: SecureTokenStore,
    private val repository: RaceControlRepository,
    private val reminderScheduler: SessionReminderScheduler,
    private val refreshScheduler: ReminderRefreshScheduler,
) : ViewModel() {

    val settings: StateFlow<AppSettings> =
        settingsStore.settings.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            AppSettings(),
        )

    val token: StateFlow<String> = tokenStore.token

    private val _status = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Unknown)
    val status: StateFlow<ConnectionStatus> = _status.asStateFlow()

    /**
     * Android convention is to persist a setting the moment it changes, so
     * there is no Save button (the iOS sheet has Cancel/Save because a modal
     * sheet implies a commit point).
     */
    fun setBaseUrl(value: String) {
        viewModelScope.launch {
            settingsStore.setBaseUrl(value)
            _status.value = ConnectionStatus.Unknown
        }
    }

    fun resetBaseUrl() {
        viewModelScope.launch {
            settingsStore.resetBaseUrl()
            _status.value = ConnectionStatus.Unknown
        }
    }

    fun setToken(value: String) {
        tokenStore.setToken(value)
        _status.value = ConnectionStatus.Unknown
    }

    /**
     * Two-step probe, mirroring the iOS Settings screen: reachability against
     * the deliberately-unauthenticated /api/health, then an authenticated call,
     * so a bad token is reported here rather than as errors all over the app.
     */
    fun testConnection() {
        viewModelScope.launch {
            _status.value = ConnectionStatus.Checking

            val health = repository.health()
            if (health.isFailure) {
                _status.value = ConnectionStatus.Failed(
                    repository.messageFor(health.exceptionOrNull() ?: Exception())
                )
                return@launch
            }
            val code = health.getOrNull() ?: 0
            if (code !in 200..299) {
                _status.value = ConnectionStatus.Failed("Server reachable but unhealthy")
                return@launch
            }

            repository.seasons()
                .onSuccess { _status.value = ConnectionStatus.Ok }
                .onFailure { error ->
                    _status.value = when {
                        error is ApiException.Server && error.status == 401 ->
                            ConnectionStatus.Failed(
                                if (tokenStore.currentToken().isEmpty()) {
                                    "This server requires an API token"
                                } else {
                                    "API token rejected"
                                }
                            )
                        else -> ConnectionStatus.Failed(repository.messageFor(error))
                    }
                }
        }
    }

    fun setNotificationsEnabled(value: Boolean) = update {
        settingsStore.setNotificationsEnabled(value)
    }

    fun setNotifyDayBefore(value: Boolean) = update { settingsStore.setNotifyDayBefore(value) }
    fun setNotify1Hour(value: Boolean) = update { settingsStore.setNotify1Hour(value) }
    fun setNotify15Min(value: Boolean) = update { settingsStore.setNotify15Min(value) }
    fun setNotifyPractice(value: Boolean) = update { settingsStore.setNotifyPractice(value) }
    fun setNotifyQualifying(value: Boolean) = update { settingsStore.setNotifyQualifying(value) }
    fun setNotifySprint(value: Boolean) = update { settingsStore.setNotifySprint(value) }
    fun setNotifyRace(value: Boolean) = update { settingsStore.setNotifyRace(value) }

    fun canScheduleExactAlarms(): Boolean = reminderScheduler.canScheduleExact()

    /** Any reminder-affecting change rebuilds the whole alarm window. */
    private fun update(block: suspend () -> Unit) {
        viewModelScope.launch {
            block()
            refreshScheduler.refreshNow()
        }
    }
}
