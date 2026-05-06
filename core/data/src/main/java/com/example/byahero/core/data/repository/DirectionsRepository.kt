package com.example.byahero.core.data.repository

import com.google.android.gms.maps.model.LatLng

data class WalkingPath(
    val points: List<LatLng>,
    val distanceMeters: Int
)

interface DirectionsRepository {
    suspend fun getWalkingDirections(origin: LatLng, destination: LatLng): WalkingPath?
}
