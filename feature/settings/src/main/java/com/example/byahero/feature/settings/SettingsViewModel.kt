package com.example.byahero.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.byahero.core.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val isNotificationsEnabled: Boolean = true,
    val notifyJeepneyNear: Boolean = true,
    val notifyJeepneyDistance: Boolean = false,
    val notifyStopDistance: Boolean = false,
    val isDarkMode: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.isNotificationsEnabled,
        settingsRepository.notifyJeepneyNear,
        settingsRepository.notifyJeepneyDistance,
        settingsRepository.notifyStopDistance,
        settingsRepository.isDarkMode
    ) { isNotificationsEnabled, notifyJeepneyNear, notifyJeepneyDistance, notifyStopDistance, isDarkMode ->
        SettingsUiState(
            isNotificationsEnabled = isNotificationsEnabled,
            notifyJeepneyNear = notifyJeepneyNear,
            notifyJeepneyDistance = notifyJeepneyDistance,
            notifyStopDistance = notifyStopDistance,
            isDarkMode = isDarkMode
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setNotificationsEnabled(enabled)
        }
    }

    fun setNotifyJeepneyNear(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setNotifyJeepneyNear(enabled)
        }
    }

    fun setNotifyJeepneyDistance(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setNotifyJeepneyDistance(enabled)
        }
    }

    fun setNotifyStopDistance(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setNotifyStopDistance(enabled)
        }
    }

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setDarkMode(enabled)
        }
    }
}
