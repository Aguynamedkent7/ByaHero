package com.example.byahero.core.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
data class SavedPlace(
    val id: String,
    val name: String,
    val location: String // "lat,lng"
)

interface SettingsRepository {
    val isNotificationsEnabled: Flow<Boolean>
    val notifyJeepneyNear: Flow<Boolean>
    val notifyJeepneyDistance: Flow<Boolean>
    val notifyStopDistance: Flow<Boolean>
    val isDarkMode: Flow<Boolean>
    val homeLocation: Flow<String?>
    val workLocation: Flow<String?>
    val savedPlaces: Flow<List<SavedPlace>>

    suspend fun setNotificationsEnabled(enabled: Boolean)
    suspend fun setNotifyJeepneyNear(enabled: Boolean)
    suspend fun setNotifyJeepneyDistance(enabled: Boolean)
    suspend fun setNotifyStopDistance(enabled: Boolean)
    suspend fun setDarkMode(enabled: Boolean)
    suspend fun setHomeLocation(location: String?)
    suspend fun setWorkLocation(location: String?)
    suspend fun addSavedPlace(name: String, location: String)
    suspend fun deleteSavedPlace(id: String)
}
