package com.unishuttle.app.domain.model

data class ShuttleLocation(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val heading: Float,
    val updatedAtMillis: Long,
    val routeId: String? = null,
    val hasHeading: Boolean = true,
) {
    val isAssigned: Boolean get() = !routeId.isNullOrBlank()

    val displayLabel: String
        get() = if (isAssigned) "SH-$id" else "SH-$id · Unassigned"
}
