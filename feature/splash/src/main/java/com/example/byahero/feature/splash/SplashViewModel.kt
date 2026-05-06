package com.example.byahero.feature.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.byahero.core.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _navigationTarget = MutableStateFlow<SplashNavTarget?>(null)
    val navigationTarget: StateFlow<SplashNavTarget?> = _navigationTarget.asStateFlow()

    init {
        checkAutoLogin()
    }

    private fun checkAutoLogin() {
        viewModelScope.launch {
            coroutineScope {
                // Keep splash on screen for at least 1.5 seconds for branding
                val minSplashTime = async { delay(1500) }
                
                // Check auth status
                val isAutoLoginEnabled = authRepository.canAutoLogin()

                minSplashTime.await()

                if (isAutoLoginEnabled) {
                    _navigationTarget.value = SplashNavTarget.Home
                } else {
                    _navigationTarget.value = SplashNavTarget.Login
                }
            }
        }
    }
}

sealed class SplashNavTarget {
    object Login : SplashNavTarget()
    object Home : SplashNavTarget()
}
