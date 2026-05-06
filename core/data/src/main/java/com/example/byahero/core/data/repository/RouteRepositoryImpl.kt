package com.example.byahero.core.data.repository

import com.example.byahero.core.data.SupabaseConfig
import com.example.byahero.core.data.model.Route
import io.github.jan.supabase.postgrest.postgrest

class RouteRepositoryImpl : RouteRepository {
    
    private val db = SupabaseConfig.client.postgrest

    override suspend fun getRoutes(): List<Route> {
        return db["routes"].select().decodeList<Route>()
    }

    override suspend fun getRouteByCode(code: String): Route? {
        return db["routes"].select {
            filter {
                eq("code", code)
            }
        }.decodeSingleOrNull<Route>()
    }
}
