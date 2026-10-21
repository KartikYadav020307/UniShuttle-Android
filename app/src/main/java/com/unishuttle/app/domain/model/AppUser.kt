package com.unishuttle.app.domain.model

data class AppUser(
    val uid: String,
    val email: String,
    val displayName: String,
    val role: UserRole,
    val shuttleId: String = "",   // populated for DRIVER role
)
