package com.example.byahero.feature.map

import android.location.Location
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.byahero.core.common.notification.NotificationHelper
import com.example.byahero.core.data.repository.AuthRepository
import com.example.byahero.core.data.repository.DirectionsRepository
import com.example.byahero.core.data.repository.LocationRepository
import com.example.byahero.core.data.repository.RouteRepository
import com.example.byahero.core.data.repository.SettingsRepository
import com.example.byahero.core.data.repository.WalkingPath
import com.example.byahero.core.data.repository.SavedPlace
import com.google.android.gms.maps.model.LatLng
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import kotlin.math.*

@Stable
sealed class NavigationState {
    object Idle : NavigationState()
    @Immutable
    data class Searching(val query: String = "", val predictions: List<com.example.byahero.core.data.repository.PlacePrediction> = emptyList()) : NavigationState()
    @Immutable
    data class SelectingRoute(val destination: LatLng, val options: List<NavigationOption>) : NavigationState()
    @Immutable
    data class SelectingJeep(val option: NavigationOption, val origin: LatLng, val destination: LatLng, val jeeps: List<JeepneyInstance>, val allOptions: List<NavigationOption> = emptyList()) : NavigationState()
    @Immutable
    data class Navigating(
        val origin: LatLng,
        val destination: LatLng,
        val walkToPickupPath: List<LatLng>? = null,
        val ridePath: List<LatLng>,
        val walkToDestinationPath: List<LatLng>? = null,
        val jeepToPickupPath: List<LatLng>? = null,
        val walkDistanceText: String = "",
        val rideDistanceText: String = "",
        val selectedJeep: JeepneyInstance? = null,
        val journeyState: JourneyState = JourneyState.WalkingToPickup,
        val isJeepNear: Boolean = false
    ) : NavigationState()

    @Immutable
    data class PinpointingLocation(val type: String, val currentCenter: LatLng) : NavigationState()

    object SelectingSavedPlace : NavigationState()
    
    @Immutable
    data class AddingSavedPlace(val query: String = "", val predictions: List<com.example.byahero.core.data.repository.PlacePrediction> = emptyList()) : NavigationState()

    // Driver States
    object DriverIdle : NavigationState()
    @Immutable
    data class DriverSelectingRoute(val availableRoutes: List<com.example.byahero.core.data.model.Route>) : NavigationState()
    @Immutable
    data class DriverNavigating(
        val route: com.example.byahero.core.data.model.Route,
        val passengers: List<LatLng>
    ) : NavigationState()
}

enum class JourneyState {
    WalkingToPickup,
    WaitingForJeep,
    Onboard,
    ApproachingDropoff
}

@Immutable
data class JeepneyInstance(
    val id: String,
    val routeCode: String,
    val currentLocation: LatLng,
    val etaMinutes: Int = 0
)

@Immutable
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

@Immutable
data class ProjectedPoint(
    val point: LatLng,
    val segmentIndex: Int,
    val distance: Float
)
@HiltViewModel
class MapViewModel @Inject constructor(
    private val routeRepository: com.example.byahero.core.data.repository.RouteRepository,
    private val directionsRepository: com.example.byahero.core.data.repository.DirectionsRepository,
    private val locationRepository: com.example.byahero.core.data.repository.LocationRepository,
    private val authRepository: com.example.byahero.core.data.repository.AuthRepository,
    private val placesRepository: com.example.byahero.core.data.repository.PlacesRepository,
    private val settingsRepository: SettingsRepository,
    private val notificationHelper: NotificationHelper
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

    private val _userAddress = MutableStateFlow<String?>(null)
    val userAddress: StateFlow<String?> = _userAddress.asStateFlow()

    private val _isSharingLocation = MutableStateFlow(false)
    val isSharingLocation: StateFlow<Boolean> = _isSharingLocation.asStateFlow()

    private val _userRole = MutableStateFlow<String?>("commuter")
    val userRole: StateFlow<String?> = _userRole.asStateFlow()

    private val _simulatedJeepneys = MutableStateFlow<List<JeepneyInstance>>(emptyList())
    val simulatedJeepneys: StateFlow<List<JeepneyInstance>> = _simulatedJeepneys.asStateFlow()

    private val _driverBearing = MutableStateFlow(0f)
    val driverBearing: StateFlow<Float> = _driverBearing.asStateFlow()

    private val _homeLocation = MutableStateFlow<LatLng?>(null)
    val homeLocation: StateFlow<LatLng?> = _homeLocation.asStateFlow()

    private val _workLocation = MutableStateFlow<LatLng?>(null)
    val workLocation: StateFlow<LatLng?> = _workLocation.asStateFlow()

    private val _savedPlaces = MutableStateFlow<List<SavedPlace>>(emptyList())
    val savedPlaces: StateFlow<List<SavedPlace>> = _savedPlaces.asStateFlow()

    private var searchJob: Job? = null
    private var currentUserId: String? = null
    private var locationJob: Job? = null
    private var currentNavigationOption: NavigationOption? = null // Track current route for re-selection

    private val defaultOrigin = LatLng(8.4847, 124.6566)

    init {
        loadRoutes()
        observeCurrentUser()
        observeSavedLocations()
    }

    private fun observeSavedLocations() {
        viewModelScope.launch {
            settingsRepository.homeLocation.collect { locStr ->
                _homeLocation.value = locStr?.toLatLng()
            }
        }
        viewModelScope.launch {
            settingsRepository.workLocation.collect { locStr ->
                _workLocation.value = locStr?.toLatLng()
            }
        }
        viewModelScope.launch {
            settingsRepository.savedPlaces.collect { places ->
                _savedPlaces.value = places
            }
        }
    }

    private fun String.toLatLng(): LatLng? {
        return try {
            val parts = this.split(",")
            LatLng(parts[0].toDouble(), parts[1].toDouble())
        } catch (e: Exception) {
            null
        }
    }

    private fun LatLng.toLocString(): String = "${latitude},${longitude}"

    fun onQuickActionClick(type: String) {
        when (type) {
            "Home" -> {
                _homeLocation.value?.let { onDestinationSelected(it) } 
                    ?: run { _navigationState.value = NavigationState.PinpointingLocation("Home", _userLocation.value ?: defaultOrigin) }
            }
            "Work" -> {
                _workLocation.value?.let { onDestinationSelected(it) } 
                    ?: run { _navigationState.value = NavigationState.PinpointingLocation("Work", _userLocation.value ?: defaultOrigin) }
            }
            "Saved" -> {
                _navigationState.value = NavigationState.SelectingSavedPlace
            }
        }
    }

    fun startAddingSavedPlace() {
        _navigationState.value = NavigationState.AddingSavedPlace()
    }

    fun startPinpointingCustomPlace() {
        _navigationState.value = NavigationState.PinpointingLocation("Custom", _userLocation.value ?: defaultOrigin)
    }

    fun updateSavedPlaceSearch(query: String) {
        val currentState = _navigationState.value as? NavigationState.AddingSavedPlace ?: return
        _navigationState.value = currentState.copy(query = query)
        
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            val predictions = placesRepository.getPredictions(query)
            val currentNavState = _navigationState.value
            if (currentNavState is NavigationState.AddingSavedPlace && currentNavState.query == query) {
                _navigationState.value = currentNavState.copy(predictions = predictions)
            }
        }
    }

    fun selectSavedPlacePrediction(prediction: com.example.byahero.core.data.repository.PlacePrediction) {
        viewModelScope.launch {
            _isLoading.value = true
            val coordinates = placesRepository.getPlaceCoordinates(prediction.placeId)
            if (coordinates != null) {
                confirmAddSavedPlace(prediction.primaryText, coordinates)
            }
            _isLoading.value = false
        }
    }

    fun deleteSavedPlace(id: String) {
        viewModelScope.launch {
            settingsRepository.deleteSavedPlace(id)
        }
    }

    fun updatePinpointCenter(center: LatLng) {
        val currentState = _navigationState.value as? NavigationState.PinpointingLocation ?: return
        _navigationState.value = currentState.copy(currentCenter = center)
    }

    fun confirmPinpoint() {
        val state = _navigationState.value as? NavigationState.PinpointingLocation ?: return
        val locString = state.currentCenter.toLocString()
        
        viewModelScope.launch {
            when (state.type) {
                "Home" -> settingsRepository.setHomeLocation(locString)
                "Work" -> settingsRepository.setWorkLocation(locString)
                else -> {}
            }
            _navigationState.value = NavigationState.Idle
            onDestinationSelected(state.currentCenter)
        }
    }

    fun confirmAddSavedPlace(name: String, location: LatLng) {
        viewModelScope.launch {
            settingsRepository.addSavedPlace(name, location.toLocString())
            _navigationState.value = NavigationState.SelectingSavedPlace
        }
    }

    private fun observeCurrentUser() {
        viewModelScope.launch {
            authRepository.currentUser.collect { user ->
                currentUserId = user?.id
                if (user != null) {
                    val role = authRepository.getUserRole(user.id)
                    _userRole.value = role ?: "commuter"
                    if (role == "driver") {
                        _navigationState.value = NavigationState.DriverIdle
                    }
                } else {
                    _userRole.value = "commuter"
                    _navigationState.value = NavigationState.Idle
                }
            }
        }
    }

    fun startSearching() {
        _navigationState.value = NavigationState.Searching()
    }

    fun updateSearchQuery(query: String) {
        val currentState = _navigationState.value as? NavigationState.Searching ?: return
        _navigationState.value = currentState.copy(query = query)
        
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300) // Debounce
            val predictions = placesRepository.getPredictions(query)
            // Ensure we are still in Searching state before updating
            val currentNavState = _navigationState.value
            if (currentNavState is NavigationState.Searching && currentNavState.query == query) {
                _navigationState.value = currentNavState.copy(predictions = predictions)
            }
        }
    }

    fun selectPrediction(prediction: com.example.byahero.core.data.repository.PlacePrediction) {
        viewModelScope.launch {
            _isLoading.value = true
            val coordinates = placesRepository.getPlaceCoordinates(prediction.placeId)
            if (coordinates != null) {
                onDestinationSelected(coordinates)
            } else {
                _error.value = "Could not find coordinates for ${prediction.primaryText}"
            }
            _isLoading.value = false
        }
    }

    fun startLocationTracking() {
        if (locationJob != null) return
        locationJob = viewModelScope.launch {
            locationRepository.getDeviceLocation().collectLatest { location ->
                val prevLoc = _userLocation.value
                _userLocation.value = location
                
                // Only update address if location changed significantly (> 10m) or address is null
                if (_userAddress.value == null || prevLoc == null || calculateDistance(prevLoc, location) > 10f) {
                    viewModelScope.launch {
                        _userAddress.value = placesRepository.getAddressFromCoordinates(location)
                    }
                }

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

    fun navigateBack() {
        when (val state = _navigationState.value) {
            is NavigationState.SelectingRoute -> {
                _navigationState.value = NavigationState.Idle
            }
            is NavigationState.SelectingJeep -> {
                if (state.allOptions.size > 1) {
                    _navigationState.value = NavigationState.SelectingRoute(state.destination, state.allOptions)
                } else {
                    _navigationState.value = NavigationState.Idle
                }
            }
            is NavigationState.Searching -> {
                _navigationState.value = NavigationState.Idle
            }
            is NavigationState.SelectingSavedPlace -> {
                _navigationState.value = NavigationState.Idle
            }
            is NavigationState.AddingSavedPlace -> {
                _navigationState.value = NavigationState.SelectingSavedPlace
            }
            is NavigationState.PinpointingLocation -> {
                if (state.type == "Custom") {
                    _navigationState.value = NavigationState.AddingSavedPlace()
                } else {
                    _navigationState.value = NavigationState.Idle
                }
            }
            else -> {
                _navigationState.value = NavigationState.Idle
            }
        }
    }

    private var driverSimulationJob: Job? = null

    fun selectDriverRoute() {
        _navigationState.value = NavigationState.DriverSelectingRoute(_routes.value)
    }

    fun startDriverTrip(route: com.example.byahero.core.data.model.Route) {
        // Generate simulated passengers (dots) along the route
        val path = route.pathCoordinates.map { it.toLatLng() }
        val simulatedPassengers = mutableListOf<LatLng>()
        if (path.size > 5) {
            val random = java.util.Random()
            val numPassengers = 5 + random.nextInt(6) // 5 to 10 passengers
            for (i in 0 until numPassengers) {
                val idx = random.nextInt(path.size - 1)
                simulatedPassengers.add(path[idx])
            }
        }

        _navigationState.value = NavigationState.DriverNavigating(route, simulatedPassengers)

        // Cancel real location tracking for driver simulation
        locationJob?.cancel()
        locationJob = null

        driverSimulationJob?.cancel()
        driverSimulationJob = viewModelScope.launch {
            if (path.isEmpty()) return@launch
            val loopDurationMs = 300_000L // 5 mins per loop
            val segmentDurationMs = loopDurationMs / path.size
            var currentIndex = 0
            
            while (true) {
                val startPoint = path[currentIndex]
                val nextIndex = (currentIndex + 1) % path.size
                val endPoint = path[nextIndex]
                val frameRateMs = 33L // ~30fps updates for smooth movement without excessive CPU usage
                var elapsedTime = 0f
                
                val bearing = calculateBearing(startPoint, endPoint)
                _driverBearing.value = bearing
                
                while (elapsedTime < segmentDurationMs) {
                    val fraction = elapsedTime / segmentDurationMs.toFloat()
                    val newLoc = interpolate(fraction, startPoint, endPoint)
                    _userLocation.value = newLoc
                    delay(frameRateMs)
                    elapsedTime += frameRateMs
                }
                _userLocation.value = endPoint
                currentIndex = nextIndex
            }
        }
    }

    private fun calculateBearing(start: LatLng, end: LatLng): Float {
        val lat1 = Math.toRadians(start.latitude)
        val lng1 = Math.toRadians(start.longitude)
        val lat2 = Math.toRadians(end.latitude)
        val lng2 = Math.toRadians(end.longitude)

        val y = sin(lng2 - lng1) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(lng2 - lng1)
        return ((Math.toDegrees(atan2(y, x)) + 360) % 360).toFloat()
    }

    fun endDriverTrip() {
        driverSimulationJob?.cancel()
        driverSimulationJob = null
        _navigationState.value = NavigationState.DriverIdle
        // Resume real location tracking
        startLocationTracking()
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
            
            // 1. Identify candidate points for all routes
            val routeCandidateMap = allRoutes.mapNotNull { routeData ->
                val routePath = routeData.pathCoordinates.map { it.toLatLng() }
                if (routePath.size < 2) return@mapNotNull null
                val pickups = findTopCandidatePoints(currentOrigin, routePath, count = 2)
                val dropoffs = findTopCandidatePoints(destination, routePath, count = 2)
                routeData to (pickups to dropoffs)
            }.toMap()

            // 2. Collect unique walking requests (Origin -> Pickup and Dropoff -> Destination)
            data class WalkRequest(val start: LatLng, val end: LatLng)
            val uniqueRequests = mutableSetOf<WalkRequest>()
            routeCandidateMap.values.forEach { (pickups, dropoffs) ->
                pickups.forEach { uniqueRequests.add(WalkRequest(currentOrigin, it.point)) }
                dropoffs.forEach { uniqueRequests.add(WalkRequest(it.point, destination)) }
            }

            // 3. Fetch all walking directions in parallel
            val walkPathCache = mutableMapOf<WalkRequest, WalkingPath?>()
            val fetchJobs = uniqueRequests.map { req ->
                async {
                    val path = directionsRepository.getWalkingDirections(req.start, req.end)
                    req to path
                }
            }
            fetchJobs.forEach { job ->
                val (req, path) = job.await()
                walkPathCache[req] = path
            }

            // 4. Evaluate all combinations for each route to find the "Best Effort" option
            val options = routeCandidateMap.mapNotNull { entry ->
                val routeData = entry.key
                val candidates = entry.value
                val (pickups, dropoffs) = candidates
                val routePath = routeData.pathCoordinates.map { it.toLatLng() }
                
                var bestOptionForThisRoute: NavigationOption? = null
                var minEffortForThisRoute = Double.MAX_VALUE

                for (pickup in pickups) {
                    val walkToReq = WalkRequest(currentOrigin, pickup.point)
                    val walkToPickup = walkPathCache[walkToReq] ?: continue
                    
                    val (effectivePickup, effectiveWalkPath) = findFirstPointOnRoute(walkToPickup.points, routePath) 
                        ?: Pair(pickup, walkToPickup.points)
                    val walkToDist = estimatePathDistance(effectiveWalkPath)

                    for (dropoff in dropoffs) {
                        val walkFromReq = WalkRequest(dropoff.point, destination)
                        val walkFromDropoff = walkPathCache[walkFromReq] ?: continue

                        val ridePath = constructRidePath(routePath, effectivePickup, dropoff)
                        val rideDist = estimatePathDistance(ridePath)
                        
                        // Effort Score: Walking is "heavier" than riding
                        val walkEffort = walkToDist + walkFromDropoff.distanceMeters
                        val rideEffort = rideDist / 5.0 // Ride is 5x easier than walk
                        val totalEffort = walkEffort + rideEffort
                        
                        if (totalEffort < minEffortForThisRoute) {
                            minEffortForThisRoute = totalEffort
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
                bestOptionForThisRoute
            }

            if (options.isNotEmpty()) {
                // Final sort: prioritize lowest walking distance, then lowest ride distance
                val sortedOptions = options.sortedBy { it.totalWalkMeters + (it.rideMeters / 5.0) }
                if (sortedOptions.size == 1) {
                    showJeepSelection(sortedOptions.first(), currentOrigin, destination, sortedOptions)
                } else {
                    _navigationState.value = NavigationState.SelectingRoute(destination, sortedOptions)
                }
            } else {
                _error.value = "No suitable route found."
            }

            _isLoading.value = false
        }
    }

    private fun calculateForwardDistance(path: List<LatLng>, start: LatLng, end: LatLng): Float {
        val startIdx = findNearestPointIndex(start, path)
        val endIdx = findNearestPointIndex(end, path)
        var totalDist = 0f
        if (startIdx <= endIdx) {
            for (i in startIdx until endIdx) {
                totalDist += calculateDistance(path[i], path[i + 1])
            }
        } else {
            for (i in startIdx until path.size - 1) {
                totalDist += calculateDistance(path[i], path[i + 1])
            }
            totalDist += calculateDistance(path.last(), path.first())
            for (i in 0 until endIdx) {
                totalDist += calculateDistance(path[i], path[i + 1])
            }
        }
        return totalDist
    }

    fun showJeepSelection(option: NavigationOption, origin: LatLng, destination: LatLng, allOptions: List<NavigationOption> = emptyList()) {
        val pickupPoint = option.walkToPickupPath.lastOrNull() ?: option.ridePath.first()
        val fullRoute = _routes.value.find { it.code == option.routeCode }?.pathCoordinates?.map { it.toLatLng() } ?: emptyList()

        val relevantJeeps = _simulatedJeepneys.value
            .filter { it.routeCode == option.routeCode }
            .map { jeep ->
                val dist = if (fullRoute.isNotEmpty()) {
                    calculateForwardDistance(fullRoute, jeep.currentLocation, pickupPoint)
                } else {
                    calculateDistance(jeep.currentLocation, pickupPoint)
                }
                val eta = (dist / 250).toInt()
                jeep.copy(etaMinutes = eta)
            }
            .sortedBy { it.etaMinutes }

        _navigationState.value = NavigationState.SelectingJeep(option, origin, destination, relevantJeeps, allOptions)
    }

    fun selectJeep(jeep: JeepneyInstance, state: NavigationState.SelectingJeep) {
        val pickupLoc = state.option.walkToPickupPath.lastOrNull() ?: state.option.ridePath.first()
        val fullRoute = _routes.value.find { it.code == state.option.routeCode }?.pathCoordinates?.map { it.toLatLng() } ?: emptyList()
        
        if (fullRoute.isNotEmpty()) {
            val distForward = calculateForwardDistance(fullRoute, jeep.currentLocation, pickupLoc)
            val totalRouteDist = calculatePathDistance(fullRoute)
            if (distForward > totalRouteDist * 0.9 && calculateDistance(jeep.currentLocation, pickupLoc) > 150f) {
                _error.value = "That jeepney has already passed your pickup point. Please select another one."
                return
            }
        }

        currentNavigationOption = state.option // Store the option for potential re-selection
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

        // Show initial distance notification
        viewModelScope.launch {
            if (settingsRepository.isNotificationsEnabled.first() && settingsRepository.notifyJeepneyDistance.first()) {
                val dist = if (fullRoute.isNotEmpty()) {
                    calculateForwardDistance(fullRoute, jeep.currentLocation, pickupLoc)
                } else {
                    calculateDistance(jeep.currentLocation, pickupLoc)
                }
                notificationHelper.showJourneyNotification(
                    "Jeepney Selected",
                    "Your jeepney is ${String.format(Locale.US, "%.1f", dist / 1000.0)} km away."
                )
            }
        }
    }

    private fun monitorJourney(nav: NavigationState.Navigating) {
        viewModelScope.launch {
            while (_navigationState.value is NavigationState.Navigating) {
                val currentNav = _navigationState.value as NavigationState.Navigating
                val jeep = _simulatedJeepneys.value.find { it.id == currentNav.selectedJeep?.id } ?: currentNav.selectedJeep
                
                if (jeep != null) {
                    val pickupLoc = currentNav.walkToPickupPath?.last() ?: currentNav.ridePath.first()
                    val dropoffLoc = currentNav.walkToDestinationPath?.first() ?: currentNav.ridePath.last()
                    
                    val jeepIdxOnRide = findNearestPointIndex(jeep.currentLocation, currentNav.ridePath)
                    val dropoffIdxOnRide = findNearestPointIndex(dropoffLoc, currentNav.ridePath)
                    
                    val fullRoute = _routes.value.find { it.code == jeep.routeCode }?.pathCoordinates?.map { it.toLatLng() } ?: emptyList()

                    val jeepToPickup = if (currentNav.journeyState == JourneyState.WalkingToPickup || currentNav.journeyState == JourneyState.WaitingForJeep) {
                        if (fullRoute.isNotEmpty()) {
                            constructForwardPath(fullRoute, jeep.currentLocation, pickupLoc)
                        } else null
                    } else null
                    
                    val distToPickup = calculateDistance(jeep.currentLocation, pickupLoc)
                    val isJeepNear = distToPickup < 500f

                    // Notify when jeep is near
                    if (isJeepNear && currentNav.journeyState != JourneyState.Onboard && currentNav.journeyState != JourneyState.ApproachingDropoff) {
                        viewModelScope.launch {
                            if (settingsRepository.isNotificationsEnabled.first() && settingsRepository.notifyJeepneyNear.first()) {
                                notificationHelper.showJourneyNotification(
                                    "Jeepney is Near!",
                                    "Your jeepney is approaching the pickup point."
                                )
                            }
                        }
                    }
                    
                    var isPastPickup = false
                    if (fullRoute.isNotEmpty() && (currentNav.journeyState == JourneyState.WalkingToPickup || currentNav.journeyState == JourneyState.WaitingForJeep)) {
                        val distForward = calculateForwardDistance(fullRoute, jeep.currentLocation, pickupLoc)
                        val totalRouteDist = calculatePathDistance(fullRoute)
                        isPastPickup = distForward > totalRouteDist * 0.9 && distToPickup > 200f
                    }

                    if (isPastPickup) {
                        _error.value = "You missed Jeepney ${jeep.id}. Please select another one."
                        currentNavigationOption?.let { option ->
                            showJeepSelection(option, currentNav.origin, currentNav.destination)
                        } ?: run {
                            _navigationState.value = NavigationState.Idle
                        }
                        return@launch
                    }

                    var nextState = currentNav.copy(
                        isJeepNear = isJeepNear, 
                        selectedJeep = jeep,
                        jeepToPickupPath = jeepToPickup
                    )

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
                            val remainingPath = if (jeepIdxOnRide < currentNav.ridePath.size) {
                                currentNav.ridePath.subList(jeepIdxOnRide, (dropoffIdxOnRide + 1).coerceAtMost(currentNav.ridePath.size))
                            } else emptyList()
                            
                            val distToDropoff = if (remainingPath.isNotEmpty()) calculatePathDistance(remainingPath) else 0f
                            if (distToDropoff < 100f) {
                                nextState = nextState.copy(journeyState = JourneyState.ApproachingDropoff)
                                viewModelScope.launch {
                                    if (settingsRepository.isNotificationsEnabled.first() && settingsRepository.notifyStopDistance.first()) {
                                        notificationHelper.showJourneyNotification(
                                            "Almost There!",
                                            "You are approaching your drop-off point."
                                        )
                                    }
                                }
                            }
                        }
                        else -> {}
                    }
                    _navigationState.value = nextState
                }
                delay(1500)
            }
        }
    }

    fun confirmBoarded() {
        (_navigationState.value as? NavigationState.Navigating)?.let {
            _navigationState.value = it.copy(journeyState = JourneyState.Onboard, jeepToPickupPath = null)
        }
    }

    fun selectRoute(option: NavigationOption, origin: LatLng, destination: LatLng, allOptions: List<NavigationOption> = emptyList()) {
        showJeepSelection(option, origin, destination, allOptions)
    }

    fun clearError() {
        _error.value = null
    }

    private fun List<Double>.toLatLng(): LatLng {
        return if (this.size >= 2) {
            // Standard GeoJSON is [lng, lat], but some files might be [lat, lng]
            if (this[0] > 90.0 || this[0] < -90.0) LatLng(this[1], this[0]) else LatLng(this[0], this[1])
        } else LatLng(0.0, 0.0)
    }

    private fun findFirstPointOnRoute(walkPath: List<LatLng>, routePath: List<LatLng>): Pair<ProjectedPoint, List<LatLng>>? {
        val limit = (walkPath.size * 0.95).toInt().coerceAtLeast(1)
        for (i in 0 until limit) {
            val p = walkPath[i]
            for (j in 0 until routePath.size - 1) {
                val nearest = getNearestPointOnSegment(p, routePath[j], routePath[j+1])
                // Check if distance is small AND it follows forward direction
                if (calculateDistance(p, nearest) < 20.0) {
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

    private fun constructForwardPath(
        fullPath: List<LatLng>,
        startLoc: LatLng,
        endLoc: LatLng
    ): List<LatLng> {
        val startIdx = findNearestPointIndex(startLoc, fullPath)
        val endIdx = findNearestPointIndex(endLoc, fullPath)
        val result = mutableListOf<LatLng>()
        
        result.add(startLoc)
        if (startIdx <= endIdx) {
            // Normal forward
            for (i in startIdx + 1..endIdx) {
                result.add(fullPath[i])
            }
        } else {
            // Loop around the route
            for (i in startIdx + 1 until fullPath.size) {
                result.add(fullPath[i])
            }
            for (i in 0..endIdx) {
                result.add(fullPath[i])
            }
        }
        result.add(endLoc)
        return result.distinct()
    }

    private fun constructRidePath(
        fullPath: List<LatLng>,
        pickup: ProjectedPoint,
        dropoff: ProjectedPoint
    ): List<LatLng> {
        return constructForwardPath(fullPath, pickup.point, dropoff.point)
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
                
                // Simulate all routes
                fetchedRoutes.forEach { route ->
                    val path = route.pathCoordinates.map { it.toLatLng() }
                    if (path.isNotEmpty()) {
                        startMultiJeepSimulation(route.code, path)
                    }
                }
            } catch (e: Exception) {
                _error.value = "Failed to load routes: ${e.message}"
                e.printStackTrace()
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
            Pair("${routeCode}_J1", 0),
            Pair("${routeCode}_J2", (path.size * 0.25).toInt()),
            Pair("${routeCode}_J3", (path.size * 0.5).toInt()),
            Pair("${routeCode}_J4", (path.size * 0.75).toInt())
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
