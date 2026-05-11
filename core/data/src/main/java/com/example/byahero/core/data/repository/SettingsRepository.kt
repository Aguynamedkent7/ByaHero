package com.example.byahero.core.data.repository

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val isNotificationsEnabled: Flow<Boolean>
    val notifyJeepneyNear: Flow<Boolean>
    val notifyJeepneyDistance: Flow<Boolean>
    val notifyStopDistance: Flow<Boolean>
    val isDarkMode: Flow<Boolean>

    suspend fun setNotificationsEnabled(enabled: Boolean)
    suspend fun setNotifyJeepneyNear(enabled: Boolean)
    suspend fun setNotifyJeepneyDistance(enabled: Boolean)
    suspend fun setNotifyStopDistance(enabled: Boolean)
    suspend fun setDarkMode(enabled: Boolean)
}
