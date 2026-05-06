package com.example.byahero.core.data.repository

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.LatLng
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable

@Serializable
data class LocationUpdate(
    val user_id: String,
    val lat: Double,
    val lng: Double,
    val heading: Double,
    val speed: Double
)

@Singleton
class LocationRepositoryImpl @Inject constructor(
    private val context: Context,
    private val supabaseClient: SupabaseClient
) : LocationRepository {

    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    override fun getDeviceLocation(): Flow<LatLng> = callbackFlow {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
            .setMinUpdateIntervalMillis(2000)
            .build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let {
                    trySend(LatLng(it.latitude, it.longitude))
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )

        awaitClose {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    override suspend fun updateRemoteLocation(userId: String, location: LatLng, heading: Double, speed: Double) {
        val update = LocationUpdate(
            user_id = userId,
            lat = location.latitude,
            lng = location.longitude,
            heading = heading,
            speed = speed
        )
        
        supabaseClient.postgrest["locations"].upsert(update)
    }

    override suspend fun setSharingLocation(userId: String, isSharing: Boolean) {
        supabaseClient.postgrest["profiles"].update(
            mapOf("is_sharing_location" to isSharing)
        ) {
            filter {
                eq("id", userId)
            }
        }
    }
}
