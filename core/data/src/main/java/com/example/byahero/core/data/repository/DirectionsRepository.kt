package com.example.byahero.core.data.repository

import com.google.android.gms.maps.model.LatLng

interface DirectionsRepository {
    suspend fun getWalkingDirections(origin: LatLng, destination: LatLng): List<LatLng>
}
