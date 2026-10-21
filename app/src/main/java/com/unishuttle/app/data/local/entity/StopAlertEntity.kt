package com.unishuttle.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stop_alerts")
data class StopAlertEntity(
    @PrimaryKey
    val stopId: String,
    val stopName: String,
    val thresholdMinutes: Int,
    val subscribedAtMillis: Long,
)
