package com.example.byahero.feature.map

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.byahero.core.data.repository.DirectionsRepository
import com.example.byahero.core.data.repository.RouteRepository
import com.google.android.gms.maps.model.LatLng
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class NavigationState {
    object Idle : NavigationState()
    data class Navigating(
        val origin: LatLng,
        val destination: LatLng,
        val walkToPickupPath: List<LatLng>? = null,
        val ridePath: List<LatLng>,
        val walkToDestinationPath: List<LatLng>? = null
    ) : NavigationState()
}

@HiltViewModel
class MapViewModel @Inject constructor(
    private val routeRepository: RouteRepository,
    private val directionsRepository: DirectionsRepository
) : ViewModel() {

    private val _routes = MutableStateFlow<List<com.example.byahero.core.data.model.Route>>(emptyList())
    val routes: StateFlow<List<com.example.byahero.core.data.model.Route>> = _routes.asStateFlow()

    private val _navigationState = MutableStateFlow<NavigationState>(NavigationState.Idle)
    val navigationState: StateFlow<NavigationState> = _navigationState.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Assuming USTP is the origin for the prototype
    private val defaultOrigin = LatLng(8.4847, 124.6566)

    private val _simulatedJeepneyLocation = MutableStateFlow(defaultOrigin)
    val simulatedJeepneyLocation: StateFlow<LatLng> = _simulatedJeepneyLocation.asStateFlow()

    init {
        loadRoutes()
    }

    fun onDestinationSelected(destination: LatLng) {
        viewModelScope.launch {
            _isLoading.value = true
            
            val allRoutes = _routes.value
            if (allRoutes.isEmpty()) {
                _error.value = "No routes available"
                _isLoading.value = false
                return@launch
            }

            // Find the best route based on minimum walking distance
            var bestRouteSegment: List<LatLng>? = null
            var pickupPoint: LatLng? = null
            var dropoffPoint: LatLng? = null
            var minWalkDistance = Float.MAX_VALUE

            for (routeData in allRoutes) {
                val routePath = routeData.pathCoordinates.map { it.toLatLng() }
                if (routePath.isEmpty()) continue

                val pickupIndex = findNearestPointIndex(defaultOrigin, routePath)
                val dropoffIndex = findNearestPointIndex(destination, routePath)

                // Allow bidirectional routing for the prototype
                val walkToPickup = calculateDistance(defaultOrigin, routePath[pickupIndex])
                val walkFromDropoff = calculateDistance(routePath[dropoffIndex], destination)
                val totalWalk = walkToPickup + walkFromDropoff

                if (totalWalk < minWalkDistance) {
                    minWalkDistance = totalWalk
                    bestRouteSegment = if (pickupIndex <= dropoffIndex) {
                        routePath.subList(pickupIndex, dropoffIndex + 1)
                    } else {
                        routePath.subList(dropoffIndex, pickupIndex + 1).reversed()
                    }
                    pickupPoint = routePath[pickupIndex]
                    dropoffPoint = routePath[dropoffIndex]
                }
            }

            if (bestRouteSegment != null && pickupPoint != null && dropoffPoint != null) {
                // Fetch walking directions in parallel
                val walkToPickupDeferred = async { directionsRepository.getWalkingDirections(defaultOrigin, pickupPoint!!) }
                val walkFromDropoffDeferred = async { directionsRepository.getWalkingDirections(dropoffPoint!!, destination) }
                
                val walkToPickup = walkToPickupDeferred.await()
                val walkFromDropoff = walkFromDropoffDeferred.await()

                _navigationState.value = NavigationState.Navigating(
                    origin = defaultOrigin,
                    destination = destination,
                    walkToPickupPath = walkToPickup.ifEmpty { listOf(defaultOrigin, pickupPoint!!) }, // Fallback to straight line
                    ridePath = bestRouteSegment,
                    walkToDestinationPath = walkFromDropoff.ifEmpty { listOf(dropoffPoint!!, destination) } // Fallback to straight line
                )
            } else {
                _error.value = "No suitable route found in that direction."
            }

            _isLoading.value = false
        }
    }

    private fun findNearestPointIndex(point: LatLng, path: List<LatLng>): Int {
        var minDistance = Float.MAX_VALUE
        var nearestIndex = 0

        for (i in path.indices) {
            val distance = calculateDistance(point, path[i])
            if (distance < minDistance) {
                minDistance = distance
                nearestIndex = i
            }
        }
        return nearestIndex
    }

    private fun calculateDistance(start: LatLng, end: LatLng): Float {
        val results = FloatArray(1)
        Location.distanceBetween(
            start.latitude, start.longitude,
            end.latitude, end.longitude,
            results
        )
        return results[0]
    }

    private fun loadRoutes() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val fetchedRoutes = routeRepository.getRoutes()
                _routes.value = fetchedRoutes
                
                // Start simulation for the new route
                val routeToSimulate = fetchedRoutes.find { it.code == "RC" } ?: fetchedRoutes.firstOrNull()
                routeToSimulate?.let { route ->
                    val path = route.pathCoordinates.map { it.toLatLng() }
                    startSmoothSimulation(path)
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load routes"
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun List<Double>.toLatLng(): LatLng {
        return if (this.size >= 2) {
            // Robust check: CDO is at Lat ~8, Lng ~124.
            // If the first value is > 90, it's definitely Longitude.
            if (this[0] > 90.0) {
                LatLng(this[1], this[0]) // Swap [lng, lat] to [lat, lng]
            } else {
                LatLng(this[0], this[1]) // Already [lat, lng]
            }
        } else {
            LatLng(0.0, 0.0)
        }
    }

    private fun startSmoothSimulation(route: List<LatLng>) {
        if (route.isEmpty()) return
        
        viewModelScope.launch {
            var currentIndex = 0
            
            while (true) {
                val startPoint = route[currentIndex]
                val nextIndex = (currentIndex + 1) % route.size
                val endPoint = route[nextIndex]

                if (nextIndex == 0) {
                    _simulatedJeepneyLocation.value = endPoint
                    currentIndex = nextIndex
                    delay(1000)
                    continue
                }

                val animationDurationMs = 3000f
                val frameRateMs = 16f
                var elapsedTime = 0f

                while (elapsedTime < animationDurationMs) {
                    val fraction = elapsedTime / animationDurationMs
                    _simulatedJeepneyLocation.value = interpolate(fraction, startPoint, endPoint)
                    
                    delay(frameRateMs.toLong())
                    elapsedTime += frameRateMs
                }

                _simulatedJeepneyLocation.value = endPoint
                currentIndex = nextIndex
                delay(500)
            }
        }
    }

    // Linear interpolation between two coordinates
    private fun interpolate(fraction: Float, a: LatLng, b: LatLng): LatLng {
        val lat = (b.latitude - a.latitude) * fraction + a.latitude
        val lng = (b.longitude - a.longitude) * fraction + a.longitude
        return LatLng(lat, lng)
    }
}