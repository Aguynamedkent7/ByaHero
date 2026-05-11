package com.example.byahero.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.byahero.core.data.model.UserProfile
import com.example.byahero.core.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val userProfile: UserProfile? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val updateSuccess: Boolean = false
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        loadProfile()
    }

    fun loadProfile() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                if (authRepository.isUserLoggedIn()) {
                    // Get current session/user once
                    authRepository.currentUser.collect { user ->
                        if (user != null) {
                            val profile = authRepository.getUserProfile(user.id)
                            _uiState.value = _uiState.value.copy(
                                userProfile = profile,
                                isLoading = false
                            )
                        } else {
                            _uiState.value = _uiState.value.copy(
                                userProfile = null,
                                isLoading = false,
                                error = "User session not found"
                            )
                        }
                    }
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "User not logged in"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "An unexpected error occurred"
                )
            }
        }
    }

    fun updateUsername(newUsername: String) {
        viewModelScope.launch {
            val userId = _uiState.value.userProfile?.id ?: return@launch
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                authRepository.updateUsername(userId, newUsername)
                val updatedProfile = _uiState.value.userProfile?.copy(username = newUsername)
                _uiState.value = _uiState.value.copy(
                    userProfile = updatedProfile,
                    isLoading = false,
                    updateSuccess = true
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to update username"
                )
            }
        }
    }

    fun updatePassword(newPassword: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                authRepository.updatePassword(newPassword)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    updateSuccess = true
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to update password"
                )
            }
        }
    }

    fun resetUpdateSuccess() {
        _uiState.value = _uiState.value.copy(updateSuccess = false)
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
        }
    }
}
