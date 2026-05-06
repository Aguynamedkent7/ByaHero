package com.example.byahero.core.data.model

import kotlinx.serialization.Serializable

@Serializable
data class DirectionsResponse(
    val routes: List<DirectionsRoute>
)

@Serializable
data class DirectionsRoute(
    val overview_polyline: OverviewPolyline
)

@Serializable
data class OverviewPolyline(
    val points: String
)
