package com.example.byahero.core.data.repository

import com.example.byahero.core.data.model.Route

interface RouteRepository {
    suspend fun getRoutes(): List<Route>
    suspend fun getRouteByCode(code: String): Route?
}
