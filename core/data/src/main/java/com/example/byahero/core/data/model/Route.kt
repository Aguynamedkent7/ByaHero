package com.example.byahero.core.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Route(
    val id: Long? = null,
    val name: String,
    val code: String,
    @SerialName("path_coordinates")
    val pathCoordinates: List<List<Double>>,
    @SerialName("created_at")
    val createdAt: String? = null
)
