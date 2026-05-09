package com.example.byahero.core.data.repository

import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val currentUser: Flow<UserInfo?>
    
    suspend fun signUp(email: String, password: String, fullName: String, role: String)
    suspend fun signIn(usernameOrEmail: String, password: String, rememberMe: Boolean = true)
    suspend fun signOut()
    suspend fun isUserLoggedIn(): Boolean
    fun canAutoLogin(): Boolean
    suspend fun getUserRole(userId: String): String?
}
