package com.unishuttle.app.data.remote.dto

data class FirestoreSeatAvailabilityDto(
    val availableSeats: Long? = null,
    val totalSeats: Long? = null,
    val updatedAtMillis: Long? = null,
)
