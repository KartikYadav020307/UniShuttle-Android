package com.unishuttle.app.domain.model

data class StopEta(
    val shuttleId: String,
    val stopId: String,
    val etaText: String,
    val durationSeconds: Long,
)
