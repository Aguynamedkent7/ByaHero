package com.example.byahero.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.byahero.core.data.repository.RouteRepository
import com.google.android.gms.maps.model.LatLng
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MapViewModel @Inject constructor(
    private val routeRepository: RouteRepository
) : ViewModel() {

    private val _routes = MutableStateFlow<List<com.example.byahero.core.data.model.Route>>(emptyList())
    val routes: StateFlow<List<com.example.byahero.core.data.model.Route>> = _routes.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _simulatedJeepneyLocation = MutableStateFlow(LatLng(8.4847, 124.6566))
    val simulatedJeepneyLocation: StateFlow<LatLng> = _simulatedJeepneyLocation.asStateFlow()

    init {
        loadRoutes()
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