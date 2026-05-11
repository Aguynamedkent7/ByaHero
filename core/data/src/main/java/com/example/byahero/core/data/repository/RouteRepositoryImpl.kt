package com.example.byahero.core.data.repository

import android.util.Log
import com.example.byahero.core.data.SupabaseConfig
import com.example.byahero.core.data.model.Route
import io.github.jan.supabase.postgrest.postgrest

class RouteRepositoryImpl : RouteRepository {
    
    private val db = SupabaseConfig.client.postgrest

    override suspend fun getRoutes(): List<Route> {
        return try {
            db["routes"].select().decodeList<Route>()
        } catch (e: Exception) {
            Log.e("RouteRepository", "Error fetching routes", e)
            throw e
        }
    }

    override suspend fun getRouteByCode(code: String): Route? {
        return try {
            db["routes"].select {
                filter {
                    eq("code", code)
                }
            }.decodeSingleOrNull<Route>()
        } catch (e: Exception) {
            Log.e("RouteRepository", "Error fetching route by code: $code", e)
            throw e
        }
    }
}
