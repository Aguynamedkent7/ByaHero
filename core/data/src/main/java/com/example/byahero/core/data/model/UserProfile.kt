package com.example.byahero.core.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    val id: String,
    val username: String?,
    val email: String?,
    @SerialName("full_name")
    val fullName: String?,
    @SerialName("avatar_url")
    val avatarUrl: String? = null,
    val role: String? = "commuter",
    @SerialName("updated_at")
    val updatedAt: String? = null,
    @SerialName("is_sharing_location")
    val isSharingLocation: Boolean = false
)
