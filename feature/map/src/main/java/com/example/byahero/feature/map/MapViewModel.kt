package com.example.byahero.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MapViewModel @Inject constructor() : ViewModel() {

    // Dummy Lapasan Jeepney Route
    val lapasanRoute = listOf(
        LatLng(8.4815, 124.6465), // Near Ayala Centrio
        LatLng(8.4810, 124.6515), // Limketkai area
        LatLng(8.4845, 124.6565), // Passing USTP
        LatLng(8.4860, 124.6590), // Puregold Lapasan
        LatLng(8.4900, 124.6620)  // Agora Terminal
    )

    private val _simulatedJeepneyLocation = MutableStateFlow(lapasanRoute.first())
    val simulatedJeepneyLocation: StateFlow<LatLng> = _simulatedJeepneyLocation.asStateFlow()

    init {
        startSmoothSimulation()
    }

    private fun startSmoothSimulation() {
        viewModelScope.launch {
            var currentIndex = 0
            
            while (true) {
                val startPoint = lapasanRoute[currentIndex]
                val nextIndex = (currentIndex + 1) % lapasanRoute.size
                val endPoint = lapasanRoute[nextIndex]

                // If it loops back to start, don't animate the jump across the map
                if (nextIndex == 0) {
                    _simulatedJeepneyLocation.value = endPoint
                    currentIndex = nextIndex
                    delay(1000) // brief pause at the terminal
                    continue
                }

                // Smoothly interpolate between startPoint and endPoint over ~3 seconds
                val animationDurationMs = 3000f
                val frameRateMs = 16f // ~60 FPS
                var elapsedTime = 0f

                while (elapsedTime < animationDurationMs) {
                    val fraction = elapsedTime / animationDurationMs
                    _simulatedJeepneyLocation.value = interpolate(fraction, startPoint, endPoint)
                    
                    delay(frameRateMs.toLong())
                    elapsedTime += frameRateMs
                }

                // Ensure we end exactly on the target point
                _simulatedJeepneyLocation.value = endPoint
                
                // Move to the next segment
                currentIndex = nextIndex
                
                // Brief pause at each "stop"
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