package com.unishuttle.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_route_geometries")
data class CachedRouteGeometryEntity(
    @PrimaryKey
    val routeId: String,
    val routeVersion: Int,
    val stopHash: String,
    val geometryHash: String,
    val pointsJson: String,
    val pointCount: Int,
    val isFallback: Boolean,
    val updatedAtMillis: Long,
)
