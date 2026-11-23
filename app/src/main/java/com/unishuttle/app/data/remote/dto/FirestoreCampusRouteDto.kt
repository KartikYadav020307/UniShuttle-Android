package com.unishuttle.app.data.remote.dto

data class FirestoreCampusRouteDto(
    val name: String? = null,
    val description: String? = null,
    val colorHex: String? = null,
    val stopIds: List<String>? = null,
    val version: Long? = null,
    val isActive: Boolean? = null,
)
