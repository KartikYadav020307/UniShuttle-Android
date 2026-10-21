package com.unishuttle.app.domain.repository

import com.unishuttle.app.domain.model.SeatAvailability
import kotlinx.coroutines.flow.Flow

interface SeatAvailabilityRepository {
    fun observeSeatAvailability(): Flow<List<SeatAvailability>>

    suspend fun updateSeatAvailability(
        shuttleId: String,
        availableSeats: Int,
        totalSeats: Int,
        updatedAtMillis: Long,
    )
}
