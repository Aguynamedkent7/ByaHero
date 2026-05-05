package com.example.byahero.core.data.repository

import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val currentUser: Flow<UserInfo?>
    
    suspend fun signUp(email: String, password: String, fullName: String, role: String)
    suspend fun signIn(email: String, password: String)
    suspend fun signOut()
    suspend fun isUserLoggedIn(): Boolean
}
