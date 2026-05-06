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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import kotlin.math.*

sealed class NavigationState {
    object Idle : NavigationState()
    data class SelectingRoute(val destination: LatLng, val options: List<NavigationOption>) : NavigationState()
    data class SelectingJeep(val option: NavigationOption, val origin: LatLng, val destination: LatLng, val jeeps: List<JeepneyInstance>) : NavigationState()
    data class Navigating(
        val origin: LatLng,
        val destination: LatLng,
        val walkToPickupPath: List<LatLng>? = null,
        val ridePath: List<LatLng>,
        val walkToDestinationPath: List<LatLng>? = null,
        val walkDistanceText: String = "",
        val rideDistanceText: String = "",
        val selectedJeep: JeepneyInstance? = null,
        val journeyState: JourneyState = JourneyState.WalkingToPickup,
        val isJeepNear: Boolean = false
    ) : NavigationState()
}

enum class JourneyState {
    WalkingToPickup,
    WaitingForJeep,
    Onboard,
    ApproachingDropoff
}

data class JeepneyInstance(
    val id: String,
    val routeCode: String,
    val currentLocation: LatLng,
    val etaMinutes: Int = 0
)

data class NavigationOption(
    val routeName: String,
    val routeCode: String,
    val walkToPickupPath: List<LatLng>,
    val ridePath: List<LatLng>,
    val walkFromDropoffPath: List<LatLng>,
    val totalWalkMeters: Int,
    val rideMeters: Int
) {
    val walkDistanceText = "${String.format(Locale.US, "%.1f", totalWalkMeters / 1000.0)} km walking"
    val rideDistanceText = "${String.format(Locale.US, "%.1f", rideMeters / 1000.0)} km ride"
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

    private val _simulatedJeepneys = MutableStateFlow<List<JeepneyInstance>>(emptyList())
    val simulatedJeepneys: StateFlow<List<JeepneyInstance>> = _simulatedJeepneys.asStateFlow()

    private var currentUserId: String? = null
    private var locationJob: Job? = null

    private val defaultOrigin = LatLng(8.4847, 124.6566)

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

    fun cancelNavigation() {
        _navigationState.value = NavigationState.Idle
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
            val options = mutableListOf<NavigationOption>()
            
            val pickupWalkCache = mutableMapOf<LatLng, WalkingPath?>()
            val dropoffWalkCache = mutableMapOf<LatLng, WalkingPath?>()

            for (routeData in allRoutes) {
                val routePath = routeData.pathCoordinates.map { it.toLatLng() }
                if (routePath.size < 2) continue

                val pickupCandidates = findTopCandidatePoints(currentOrigin, routePath, count = 3)
                val dropoffCandidates = findTopCandidatePoints(destination, routePath, count = 3)

                var bestOptionForThisRoute: NavigationOption? = null
                var minEffortForThisRoute = Double.MAX_VALUE

                for (pickup in pickupCandidates) {
                    val walkToPickup = pickupWalkCache.getOrPut(pickup.point) {
                        directionsRepository.getWalkingDirections(currentOrigin, pickup.point)
                    } ?: continue

                    val (effectivePickup, effectiveWalkPath) = findFirstPointOnRoute(walkToPickup.points, routePath) 
                        ?: Pair(pickup, walkToPickup.points)
                    
                    val walkToDist = estimatePathDistance(effectiveWalkPath)

                    for (dropoff in dropoffCandidates) {
                        val walkFromDropoff = dropoffWalkCache.getOrPut(dropoff.point) {
                            directionsRepository.getWalkingDirections(dropoff.point, destination)
                        } ?: continue

                        val ridePath = constructRidePath(routePath, effectivePickup, dropoff)
                        val rideDist = estimatePathDistance(ridePath)
                        
                        val speedRatio = 4.0
                        val effortScore = (walkToDist + walkFromDropoff.distanceMeters) + (rideDist / speedRatio)
                        
                        if (effortScore < minEffortForThisRoute) {
                            minEffortForThisRoute = effortScore
                            bestOptionForThisRoute = NavigationOption(
                                routeName = routeData.name,
                                routeCode = routeData.code,
                                walkToPickupPath = effectiveWalkPath,
                                ridePath = ridePath,
                                walkFromDropoffPath = walkFromDropoff.points,
                                totalWalkMeters = walkToDist + walkFromDropoff.distanceMeters,
                                rideMeters = rideDist
                            )
                        }
                    }
                }
                
                bestOptionForThisRoute?.let { options.add(it) }
            }

            if (options.isNotEmpty()) {
                val sortedOptions = options.sortedBy { it.totalWalkMeters }
                if (sortedOptions.size == 1) {
                    showJeepSelection(sortedOptions.first(), currentOrigin, destination)
                } else {
                    _navigationState.value = NavigationState.SelectingRoute(destination, sortedOptions)
                }
            } else {
                _error.value = "No suitable route found."
            }

            _isLoading.value = false
        }
    }

    fun showJeepSelection(option: NavigationOption, origin: LatLng, destination: LatLng) {
        val pickupPoint = option.walkToPickupPath.last()
        val pickupIdx = findNearestPointIndex(pickupPoint, option.ridePath)

        val relevantJeeps = _simulatedJeepneys.value
            .filter { it.routeCode == option.routeCode }
            .map { jeep ->
                val jeepIdx = findNearestPointIndex(jeep.currentLocation, option.ridePath)
                val dist = if (jeepIdx <= pickupIdx) {
                    calculatePathDistance(option.ridePath.subList(jeepIdx, pickupIdx + 1))
                } else {
                    calculatePathDistance(option.ridePath.subList(jeepIdx, option.ridePath.size)) + 
                    calculatePathDistance(option.ridePath.subList(0, pickupIdx + 1))
                }
                val eta = (dist / 250).toInt()
                jeep.copy(etaMinutes = eta)
            }
            .sortedBy { it.etaMinutes }

        _navigationState.value = NavigationState.SelectingJeep(option, origin, destination, relevantJeeps)
    }

    fun selectJeep(jeep: JeepneyInstance, state: NavigationState.SelectingJeep) {
        val navState = NavigationState.Navigating(
            origin = state.origin,
            destination = state.destination,
            walkToPickupPath = state.option.walkToPickupPath,
            ridePath = state.option.ridePath,
            walkToDestinationPath = state.option.walkFromDropoffPath,
            walkDistanceText = state.option.walkDistanceText,
            rideDistanceText = state.option.rideDistanceText,
            selectedJeep = jeep,
            journeyState = JourneyState.WalkingToPickup
        )
        _navigationState.value = navState
        monitorJourney(navState)
    }

    private fun monitorJourney(nav: NavigationState.Navigating) {
        viewModelScope.launch {
            while (_navigationState.value is NavigationState.Navigating) {
                val currentNav = _navigationState.value as NavigationState.Navigating
                val jeep = _simulatedJeepneys.value.find { it.id == currentNav.selectedJeep?.id } ?: currentNav.selectedJeep
                
                if (jeep != null) {
                    val pickupLoc = currentNav.walkToPickupPath?.last() ?: currentNav.ridePath.first()
                    val dropoffLoc = currentNav.walkToDestinationPath?.first() ?: currentNav.ridePath.last()
                    val jeepIdx = findNearestPointIndex(jeep.currentLocation, currentNav.ridePath)
                    val pickupIdx = findNearestPointIndex(pickupLoc, currentNav.ridePath)
                    val dropoffIdx = findNearestPointIndex(dropoffLoc, currentNav.ridePath)
                    
                    val isPastPickup = jeepIdx > pickupIdx
                    val distToPickup = calculateDistance(jeep.currentLocation, pickupLoc)
                    val isJeepNear = distToPickup < 500f // 500m threshold

                    var nextState = currentNav.copy(isJeepNear = isJeepNear)

                    when (currentNav.journeyState) {
                        JourneyState.WalkingToPickup, JourneyState.WaitingForJeep -> {
                            if (isPastPickup) {
                                _error.value = "You missed the jeep!"
                                _navigationState.value = NavigationState.Idle
                                return@launch
                            }
                            if (currentNav.journeyState == JourneyState.WalkingToPickup && calculateDistance(_userLocation.value ?: currentNav.origin, pickupLoc) < 30f) {
                                nextState = nextState.copy(journeyState = JourneyState.WaitingForJeep)
                            }
                        }
                        JourneyState.Onboard -> {
                            val distToDropoff = calculatePathDistance(currentNav.ridePath.subList(jeepIdx, dropoffIdx + 1))
                            if (distToDropoff < 100f) {
                                nextState = nextState.copy(journeyState = JourneyState.ApproachingDropoff)
                            }
                        }
                        else -> {}
                    }
                    _navigationState.value = nextState
                }
                delay(1000)
            }
        }
    }

    fun confirmBoarded() {
        (_navigationState.value as? NavigationState.Navigating)?.let {
            _navigationState.value = it.copy(journeyState = JourneyState.Onboard)
        }
    }

    fun selectRoute(option: NavigationOption, origin: LatLng, destination: LatLng) {
        showJeepSelection(option, origin, destination)
    }

    private fun List<Double>.toLatLng(): LatLng {
        return if (this.size >= 2) {
            if (this[0] > 90.0) LatLng(this[1], this[0]) else LatLng(this[0], this[1])
        } else LatLng(0.0, 0.0)
    }

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

    private fun calculatePathDistance(path: List<LatLng>): Float {
        var dist = 0f
        for (i in 0 until path.size - 1) {
            dist += calculateDistance(path[i], path[i + 1])
        }
        return dist
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
                    startMultiJeepSimulation(route.code, path)
                }
            } catch (e: Exception) {
                _error.value = "Failed to load routes"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun startMultiJeepSimulation(routeCode: String, path: List<LatLng>) {
        if (path.isEmpty()) return
        val loopDurationMs = 300_000L
        val segmentDurationMs = loopDurationMs / path.size
        val jeepConfigs = listOf(
            Pair("J1", 0),
            Pair("J2", (path.size * 0.25).toInt()),
            Pair("J3", (path.size * 0.5).toInt()),
            Pair("J4", (path.size * 0.75).toInt())
        )
        jeepConfigs.forEach { (id, startIndex) ->
            viewModelScope.launch {
                var currentIndex = startIndex
                while (true) {
                    val startPoint = path[currentIndex]
                    val nextIndex = (currentIndex + 1) % path.size
                    val endPoint = path[nextIndex]
                    val frameRateMs = 32f
                    var elapsedTime = 0f
                    while (elapsedTime < segmentDurationMs) {
                        val fraction = elapsedTime / segmentDurationMs.toFloat()
                        updateJeepLocation(id, routeCode, interpolate(fraction, startPoint, endPoint))
                        delay(frameRateMs.toLong())
                        elapsedTime += frameRateMs
                    }
                    updateJeepLocation(id, routeCode, endPoint)
                    currentIndex = nextIndex
                }
            }
        }
    }

    private fun updateJeepLocation(id: String, routeCode: String, location: LatLng) {
        val currentJeeps = _simulatedJeepneys.value.toMutableList()
        val index = currentJeeps.indexOfFirst { it.id == id }
        val newInstance = JeepneyInstance(id, routeCode, location)
        if (index >= 0) currentJeeps[index] = newInstance else currentJeeps.add(newInstance)
        _simulatedJeepneys.value = currentJeeps
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
