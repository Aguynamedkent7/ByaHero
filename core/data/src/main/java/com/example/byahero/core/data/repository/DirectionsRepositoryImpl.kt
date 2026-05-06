package com.example.byahero.core.data.repository

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.example.byahero.core.data.BuildConfig
import com.example.byahero.core.data.model.DirectionsResponse
import com.google.android.gms.maps.model.LatLng
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import java.security.MessageDigest

class DirectionsRepositoryImpl(private val context: Context) : DirectionsRepository {

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    private fun getSignature(): String {
        try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
            }

            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures
            }

            if (!signatures.isNullOrEmpty()) {
                val md = MessageDigest.getInstance("SHA-1")
                md.update(signatures[0].toByteArray())
                return md.digest().joinToString(":") { String.format("%02X", it) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return ""
    }

    override suspend fun getWalkingDirections(origin: LatLng, destination: LatLng): WalkingPath? {
        return try {
            val originStr = "${origin.latitude},${origin.longitude}"
            val destStr = "${destination.latitude},${destination.longitude}"
            
            val response: HttpResponse = client.get("https://maps.googleapis.com/maps/api/directions/json") {
                header("X-Android-Package", context.packageName)
                header("X-Android-Cert", getSignature())
                parameter("origin", originStr)
                parameter("destination", destStr)
                parameter("mode", "walking")
                parameter("key", BuildConfig.MAPS_API_KEY)
            }
            
            val responseBody = response.body<String>()
            val directionsResponse = Json { ignoreUnknownKeys = true; isLenient = true }.decodeFromString<DirectionsResponse>(responseBody)

            if (directionsResponse.routes.isNotEmpty()) {
                val route = directionsResponse.routes.first()
                val path = decodePoly(route.overview_polyline.points)
                val distance = route.legs.sumOf { it.distance.value }
                
                WalkingPath(path, distance)
            } else {
                Log.e("DirectionsAPI", "No routes found. Raw response: $responseBody")
                null
            }
        } catch (e: Exception) {
            Log.e("DirectionsAPI", "Exception calling Directions API: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    // Standard Polyline Decoder algorithm
    private fun decodePoly(encoded: String): List<LatLng> {
        val poly = ArrayList<LatLng>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat

            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dlng

            val p = LatLng(
                lat.toDouble() / 1E5,
                lng.toDouble() / 1E5
            )
            poly.add(p)
        }

        return poly
    }
}
