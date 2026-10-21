package com.unishuttle.app.data.remote.dto

data class FirebaseShuttleLocationDto(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val heading: Double? = null,
    val updatedAtMillis: Long? = null,
)
