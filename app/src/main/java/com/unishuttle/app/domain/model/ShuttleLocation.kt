package com.unishuttle.app.domain.model

data class ShuttleLocation(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val heading: Float,
    val updatedAtMillis: Long,
)
