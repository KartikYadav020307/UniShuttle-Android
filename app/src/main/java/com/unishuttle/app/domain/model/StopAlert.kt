package com.unishuttle.app.domain.model

data class StopAlert(
    val stopId: String,
    val stopName: String,
    val thresholdMinutes: Int,
    val subscribedAtMillis: Long,
)
