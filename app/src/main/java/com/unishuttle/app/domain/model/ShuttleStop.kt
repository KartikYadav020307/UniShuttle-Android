package com.unishuttle.app.domain.model

data class ShuttleStop(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val order: Int,
)
