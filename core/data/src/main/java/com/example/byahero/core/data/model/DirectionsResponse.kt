package com.example.byahero.core.data.model

import kotlinx.serialization.Serializable

@Serializable
data class DirectionsResponse(
    val routes: List<DirectionsRoute>
)

@Serializable
data class DirectionsRoute(
    val legs: List<DirectionsLeg>,
    val overview_polyline: OverviewPolyline
)

@Serializable
data class DirectionsLeg(
    val distance: Distance
)

@Serializable
data class Distance(
    val text: String,
    val value: Int // in meters
)

@Serializable
data class OverviewPolyline(
    val points: String
)
