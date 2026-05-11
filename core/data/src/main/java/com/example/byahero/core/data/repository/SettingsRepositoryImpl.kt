package com.example.byahero.core.data.repository

import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

class SettingsRepositoryImpl @Inject constructor() : SettingsRepository {
    private val settings = Settings()

    private val _isNotificationsEnabled = MutableStateFlow(settings.getBoolean(KEY_NOTIFICATIONS_ENABLED, true))
    override val isNotificationsEnabled = _isNotificationsEnabled.asStateFlow()

    private val _notifyJeepneyNear = MutableStateFlow(settings.getBoolean(KEY_NOTIFY_JEEPNEY_NEAR, true))
    override val notifyJeepneyNear = _notifyJeepneyNear.asStateFlow()

    private val _notifyJeepneyDistance = MutableStateFlow(settings.getBoolean(KEY_NOTIFY_JEEPNEY_DISTANCE, false))
    override val notifyJeepneyDistance = _notifyJeepneyDistance.asStateFlow()

    private val _notifyStopDistance = MutableStateFlow(settings.getBoolean(KEY_NOTIFY_STOP_DISTANCE, false))
    override val notifyStopDistance = _notifyStopDistance.asStateFlow()

    private val _isDarkMode = MutableStateFlow(settings.getBoolean(KEY_DARK_MODE, false))
    override val isDarkMode = _isDarkMode.asStateFlow()

    override suspend fun setNotificationsEnabled(enabled: Boolean) {
        settings[KEY_NOTIFICATIONS_ENABLED] = enabled
        _isNotificationsEnabled.value = enabled
    }

    override suspend fun setNotifyJeepneyNear(enabled: Boolean) {
        settings[KEY_NOTIFY_JEEPNEY_NEAR] = enabled
        _notifyJeepneyNear.value = enabled
    }

    override suspend fun setNotifyJeepneyDistance(enabled: Boolean) {
        settings[KEY_NOTIFY_JEEPNEY_DISTANCE] = enabled
        _notifyJeepneyDistance.value = enabled
    }

    override suspend fun setNotifyStopDistance(enabled: Boolean) {
        settings[KEY_NOTIFY_STOP_DISTANCE] = enabled
        _notifyStopDistance.value = enabled
    }

    override suspend fun setDarkMode(enabled: Boolean) {
        settings[KEY_DARK_MODE] = enabled
        _isDarkMode.value = enabled
    }

    companion object {
        private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
        private const val KEY_NOTIFY_JEEPNEY_NEAR = "notify_jeepney_near"
        private const val KEY_NOTIFY_JEEPNEY_DISTANCE = "notify_jeepney_distance"
        private const val KEY_NOTIFY_STOP_DISTANCE = "notify_stop_distance"
        private const val KEY_DARK_MODE = "dark_mode"
    }
}
