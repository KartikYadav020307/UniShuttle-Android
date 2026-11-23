package com.unishuttle.app.domain.model

data class CampusRoute(
    val id: String,
    val name: String,
    val description: String = "",
    val colorHex: String,
    val stopIds: List<String>,
    val version: Int = 1,
    val isActive: Boolean = true,
)
