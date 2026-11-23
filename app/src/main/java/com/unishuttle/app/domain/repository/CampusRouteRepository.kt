package com.unishuttle.app.domain.repository

import com.unishuttle.app.domain.model.CampusRoute
import kotlinx.coroutines.flow.Flow

interface CampusRouteRepository {
    fun observeRoutes(): Flow<List<CampusRoute>>
}
