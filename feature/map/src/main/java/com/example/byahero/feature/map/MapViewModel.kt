package com.example.byahero.feature.map

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.byahero.core.data.repository.AuthRepository
import com.example.byahero.core.data.repository.DirectionsRepository
import com.example.byahero.core.data.repository.LocationRepository
import com.example.byahero.core.data.repository.RouteRepository
import com.example.byahero.core.data.repository.WalkingPath
import com.google.android.gms.maps.model.LatLng
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.*

sealed class NavigationState {
    object Idle : NavigationState()
    data class Navigating(
        val origin: LatLng,
        val destination: LatLng,
        val walkToPickupPath: List<LatLng>? = null,
        val ridePath: List<LatLng>,
        val walkToDestinationPath: List<LatLng>? = null,
        val walkDistanceText: String = "",
        val rideDistanceText: String = ""
    ) : NavigationState()
}

data class ProjectedPoint(
    val point: LatLng,
    val segmentIndex: Int,
    val distance: Float
)

@HiltViewModel
class MapViewModel @Inject constructor(
    private val routeRepository: RouteRepository,
    private val directionsRepository: DirectionsRepository,
    private val locationRepository: LocationRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _routes = MutableStateFlow<List<com.example.byahero.core.data.model.Route>>(emptyList())
    val routes: StateFlow<List<com.example.byahero.core.data.model.Route>> = _routes.asStateFlow()

    private val _navigationState = MutableStateFlow<NavigationState>(NavigationState.Idle)
    val navigationState: StateFlow<NavigationState> = _navigationState.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _userLocation = MutableStateFlow<LatLng?>(null)
    val userLocation: StateFlow<LatLng?> = _userLocation.asStateFlow()

    private val _isSharingLocation = MutableStateFlow(false)
    val isSharingLocation: StateFlow<Boolean> = _isSharingLocation.asStateFlow()

    private var currentUserId: String? = null
    private var locationJob: Job? = null

    private val defaultOrigin = LatLng(8.4847, 124.6566)

    private val _simulatedJeepneyLocation = MutableStateFlow(defaultOrigin)
    val simulatedJeepneyLocation: StateFlow<LatLng> = _simulatedJeepneyLocation.asStateFlow()

    init {
        loadRoutes()
        observeCurrentUser()
    }

    private fun observeCurrentUser() {
        viewModelScope.launch {
            authRepository.currentUser.collect { user ->
                currentUserId = user?.id
            }
        }
    }

    fun startLocationTracking() {
        if (locationJob != null) return
        locationJob = viewModelScope.launch {
            locationRepository.getDeviceLocation().collectLatest { location ->
                _userLocation.value = location
                if (_isSharingLocation.value) {
                    currentUserId?.let { userId ->
                        locationRepository.updateRemoteLocation(userId, location)
                    }
                }
            }
        }
    }

    fun toggleLocationSharing() {
        viewModelScope.launch {
            val newState = !_isSharingLocation.value
            val userId = currentUserId
            if (userId != null) {
                try {
                    locationRepository.setSharingLocation(userId, newState)
                    _isSharingLocation.value = newState
                    if (newState) {
                        _userLocation.value?.let { location ->
                            locationRepository.updateRemoteLocation(userId, location)
                        }
                    }
                } catch (e: Exception) {
                    _error.value = "Failed to update sharing status"
                }
            } else {
                _error.value = "You must be logged in to share location"
            }
        }
    }

    fun onDestinationSelected(destination: LatLng, fallbackOrigin: LatLng? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            
            val allRoutes = _routes.value
            if (allRoutes.isEmpty()) {
                _error.value = "No routes available"
                _isLoading.value = false
                return@launch
            }

            val currentOrigin = _userLocation.value ?: fallbackOrigin ?: defaultOrigin
            
            val pickupWalkCache = mutableMapOf<LatLng, WalkingPath?>()
            val dropoffWalkCache = mutableMapOf<LatLng, WalkingPath?>()

            var bestNav: NavigationState.Navigating? = null
            var minEffectiveCost = Double.MAX_VALUE

            for (routeData in allRoutes) {
                val routePath = routeData.pathCoordinates.map { it.toLatLng() }
                if (routePath.size < 2) continue

                // Check top 3 physical candidates to explore detour alternatives
                val pickupCandidates = findTopCandidatePoints(currentOrigin, routePath, count = 3)
                val dropoffCandidates = findTopCandidatePoints(destination, routePath, count = 3)

                for (pickup in pickupCandidates) {
                    val walkToPickup = pickupWalkCache.getOrPut(pickup.point) {
                        directionsRepository.getWalkingDirections(currentOrigin, pickup.point)
                    }
                    if (walkToPickup == null) continue

                    val (effectivePickup, effectiveWalkPath) = findFirstPointOnRoute(walkToPickup.points, routePath) 
                        ?: Pair(pickup, walkToPickup.points)
                    
                    val walkToDist = estimatePathDistance(effectiveWalkPath)

                    for (dropoff in dropoffCandidates) {
                        val walkFromDropoff = dropoffWalkCache.getOrPut(dropoff.point) {
                            directionsRepository.getWalkingDirections(dropoff.point, destination)
                        }
                        if (walkFromDropoff == null) continue

                        val ridePath = constructRidePath(routePath, effectivePickup, dropoff)
                        val rideDist = estimatePathDistance(ridePath)
                        
                        // COST FUNCTION: Minimize Total Travel "Effort"
                        // effort = walking_distance + (riding_distance / speed_ratio)
                        // This prevents picking a stop that leads into a long detour loop.
                        val speedRatio = 4.0 // Assuming Jeepney is 4x faster than walking
                        val totalEffectiveCost = (walkToDist + walkFromDropoff.distanceMeters) + (rideDist / speedRatio)
                        
                        if (totalEffectiveCost < minEffectiveCost) {
                            minEffectiveCost = totalEffectiveCost
                            
                            bestNav = NavigationState.Navigating(
                                origin = currentOrigin,
                                destination = destination,
                                walkToPickupPath = effectiveWalkPath,
                                ridePath = ridePath,
                                walkToDestinationPath = walkFromDropoff.points,
                                walkDistanceText = "${((walkToDist + walkFromDropoff.distanceMeters) / 1000.0).format(1)} km walking",
                                rideDistanceText = "${(rideDist / 1000.0).format(1)} km ride"
                            )
                        }
                    }
                }
            }

            if (bestNav != null) {
                _navigationState.value = bestNav
            } else {
                _error.value = "Could not find a walkable route."
            }

            _isLoading.value = false
        }
    }

    private fun Double.format(digits: Int) = "%.${digits}f".format(this)

    private fun findFirstPointOnRoute(walkPath: List<LatLng>, routePath: List<LatLng>): Pair<ProjectedPoint, List<LatLng>>? {
        val limit = (walkPath.size * 0.9).toInt().coerceAtLeast(1)
        for (i in 0 until limit) {
            val p = walkPath[i]
            for (j in 0 until routePath.size - 1) {
                val nearest = getNearestPointOnSegment(p, routePath[j], routePath[j+1])
                if (calculateDistance(p, nearest) < 15.0) {
                    return Pair(
                        ProjectedPoint(nearest, j, 0f),
                        walkPath.subList(0, i + 1)
                    )
                }
            }
        }
        return null
    }

    private fun estimatePathDistance(path: List<LatLng>): Int {
        var dist = 0f
        for (i in 0 until path.size - 1) {
            dist += calculateDistance(path[i], path[i+1])
        }
        return dist.toInt()
    }

    private fun findTopCandidatePoints(target: LatLng, polyline: List<LatLng>, count: Int): List<ProjectedPoint> {
        val allProjections = mutableListOf<ProjectedPoint>()
        for (i in 0 until polyline.size - 1) {
            val p1 = polyline[i]
            val p2 = polyline[i + 1]
            val nearestOnSegment = getNearestPointOnSegment(target, p1, p2)
            val distance = calculateDistance(target, nearestOnSegment)
            allProjections.add(ProjectedPoint(nearestOnSegment, i, distance))
        }
        for (i in polyline.indices) {
            val distance = calculateDistance(target, polyline[i])
            allProjections.add(ProjectedPoint(polyline[i], i.coerceAtMost(polyline.size - 2), distance))
        }
        return allProjections.sortedBy { it.distance }.distinctBy { it.point }.take(count)
    }

    private fun getNearestPointOnSegment(p: LatLng, a: LatLng, b: LatLng): LatLng {
        val lat0 = p.latitude
        val lng0 = p.longitude
        val lat1 = a.latitude
        val lng1 = a.longitude
        val lat2 = b.latitude
        val lng2 = b.longitude
        val dLat = lat2 - lat1
        val dLng = lng2 - lng1
        if (dLat == 0.0 && dLng == 0.0) return a
        val t = ((lat0 - lat1) * dLat + (lng0 - lng1) * dLng) / (dLat * dLat + dLng * dLng)
        return when {
            t < 0.0 -> a
            t > 1.0 -> b
            else -> LatLng(lat1 + t * dLat, lng1 + t * dLng)
        }
    }

    private fun constructRidePath(
        fullPath: List<LatLng>,
        pickup: ProjectedPoint,
        dropoff: ProjectedPoint
    ): List<LatLng> {
        val result = mutableListOf<LatLng>()
        val startIdx = pickup.segmentIndex
        val endIdx = dropoff.segmentIndex
        if (startIdx <= endIdx) {
            result.add(pickup.point)
            for (i in startIdx + 1..endIdx) {
                result.add(fullPath[i])
            }
            result.add(dropoff.point)
        } else {
            result.add(pickup.point)
            for (i in startIdx downTo endIdx + 1) {
                result.add(fullPath[i])
            }
            result.add(dropoff.point)
        }
        return result.distinct()
    }

    private fun calculateDistance(start: LatLng, end: LatLng): Float {
        val results = FloatArray(1)
        Location.distanceBetween(start.latitude, start.longitude, end.latitude, end.longitude, results)
        return results[0]
    }

    private fun loadRoutes() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val fetchedRoutes = routeRepository.getRoutes()
                _routes.value = fetchedRoutes
                val routeToSimulate = fetchedRoutes.find { it.code == "RC" } ?: fetchedRoutes.firstOrNull()
                routeToSimulate?.let { route ->
                    val path = route.pathCoordinates.map { it.toLatLng() }
                    startSmoothSimulation(path)
                }
            } catch (e: Exception) {
                _error.value = "Failed to load routes"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun List<Double>.toLatLng(): LatLng {
        return if (this.size >= 2) {
            if (this[0] > 90.0) LatLng(this[1], this[0]) else LatLng(this[0], this[1])
        } else LatLng(0.0, 0.0)
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

    private fun interpolate(fraction: Float, a: LatLng, b: LatLng): LatLng {
        val lat = (b.latitude - a.latitude) * fraction + a.latitude
        val lng = (b.longitude - a.longitude) * fraction + a.longitude
        return LatLng(lat, lng)
    }

    override fun onCleared() {
        super.onCleared()
        locationJob?.cancel()
    }
}
