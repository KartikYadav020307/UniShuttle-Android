package com.unishuttle.app.domain.repository

import com.unishuttle.app.domain.model.ShuttleLocation
import kotlinx.coroutines.flow.Flow

interface ShuttleLocationRepository {
    fun observeShuttleLocations(): Flow<List<ShuttleLocation>>

    suspend fun updateDriverLocation(
        shuttleId: String,
        latitude: Double,
        longitude: Double,
        heading: Float,
        updatedAtMillis: Long,
    )
}
