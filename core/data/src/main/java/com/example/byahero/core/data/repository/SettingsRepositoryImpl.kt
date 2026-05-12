package com.example.byahero.core.data.repository

import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
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

    private val _homeLocation = MutableStateFlow(settings.getStringOrNull(KEY_HOME_LOCATION))
    override val homeLocation = _homeLocation.asStateFlow()

    private val _workLocation = MutableStateFlow(settings.getStringOrNull(KEY_WORK_LOCATION))
    override val workLocation = _workLocation.asStateFlow()

    private val _savedPlaces = MutableStateFlow<List<SavedPlace>>(loadSavedPlaces())
    override val savedPlaces = _savedPlaces.asStateFlow()

    private fun loadSavedPlaces(): List<SavedPlace> {
        val json = settings.getStringOrNull(KEY_SAVED_PLACES) ?: return emptyList()
        return try {
            Json.decodeFromString<List<SavedPlace>>(json)
        } catch (e: Exception) {
            emptyList()
        }
    }

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

    override suspend fun setHomeLocation(location: String?) {
        if (location == null) settings.remove(KEY_HOME_LOCATION)
        else settings[KEY_HOME_LOCATION] = location
        _homeLocation.value = location
    }

    override suspend fun setWorkLocation(location: String?) {
        if (location == null) settings.remove(KEY_WORK_LOCATION)
        else settings[KEY_WORK_LOCATION] = location
        _workLocation.value = location
    }

    override suspend fun addSavedPlace(name: String, location: String) {
        val current = _savedPlaces.value.toMutableList()
        current.add(SavedPlace(UUID.randomUUID().toString(), name, location))
        savePlaces(current)
    }

    override suspend fun deleteSavedPlace(id: String) {
        val current = _savedPlaces.value.filter { it.id != id }
        savePlaces(current)
    }

    private fun savePlaces(places: List<SavedPlace>) {
        val json = Json.encodeToString(places)
        settings[KEY_SAVED_PLACES] = json
        _savedPlaces.value = places
    }

    companion object {
        private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
        private const val KEY_NOTIFY_JEEPNEY_NEAR = "notify_jeepney_near"
        private const val KEY_NOTIFY_JEEPNEY_DISTANCE = "notify_jeepney_distance"
        private const val KEY_NOTIFY_STOP_DISTANCE = "notify_stop_distance"
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_HOME_LOCATION = "home_location"
        private const val KEY_WORK_LOCATION = "work_location"
        private const val KEY_SAVED_PLACES = "saved_places_list"
    }
}
