package com.example.byahero.core.data.repository

import com.google.android.gms.maps.model.LatLng

data class PlacePrediction(
    val placeId: String,
    val primaryText: String,
    val secondaryText: String
)

interface PlacesRepository {
    suspend fun getPredictions(query: String): List<PlacePrediction>
    suspend fun getPlaceCoordinates(placeId: String): LatLng?
    suspend fun getAddressFromCoordinates(latLng: LatLng): String?
}
