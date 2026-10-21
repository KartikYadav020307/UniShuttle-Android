package com.unishuttle.app.domain.model

data class SeatAvailability(
    val shuttleId: String,
    val availableSeats: Int,
    val totalSeats: Int,
    val updatedAtMillis: Long,
) {
    val occupiedSeats: Int = (totalSeats - availableSeats).coerceIn(0, totalSeats.coerceAtLeast(0))
    val availableRatio: Float = if (totalSeats > 0) {
        (availableSeats.toFloat() / totalSeats.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val occupiedRatio: Float = if (totalSeats > 0) {
        (occupiedSeats.toFloat() / totalSeats.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val isFull: Boolean = totalSeats > 0 && availableSeats <= 0
}
