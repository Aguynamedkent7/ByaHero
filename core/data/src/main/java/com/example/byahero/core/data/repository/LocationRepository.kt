package com.example.byahero.core.data.repository

import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.Flow

interface LocationRepository {
    fun getDeviceLocation(): Flow<LatLng>
    suspend fun updateRemoteLocation(userId: String, location: LatLng, heading: Double = 0.0, speed: Double = 0.0)
    suspend fun setSharingLocation(userId: String, isSharing: Boolean)
}
