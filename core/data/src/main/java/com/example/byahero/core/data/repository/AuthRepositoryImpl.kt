package com.example.byahero.core.data.repository

import com.example.byahero.core.data.SupabaseConfig
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AuthRepositoryImpl : AuthRepository {
    
    private val auth = SupabaseConfig.client.auth
    private val db = SupabaseConfig.client.postgrest

    override val currentUser: Flow<UserInfo?> = auth.sessionStatus.map { 
        auth.currentUserOrNull()
    }

    override suspend fun signUp(email: String, password: String, fullName: String, role: String) {
        auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
        
        // After signup, create the profile entry
        val user = auth.currentUserOrNull() ?: throw Exception("Signup failed")
        
        db["profiles"].insert(
            mapOf(
                "id" to user.id,
                "full_name" to fullName,
                "role" to role
            )
        )
    }

    override suspend fun signIn(email: String, password: String) {
        auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    override suspend fun signOut() {
        auth.signOut()
    }

    override suspend fun isUserLoggedIn(): Boolean {
        return auth.currentUserOrNull() != null
    }
}
