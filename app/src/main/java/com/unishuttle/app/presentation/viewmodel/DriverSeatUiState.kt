package com.unishuttle.app.presentation.viewmodel

data class DriverSeatUiState(
    val shuttleId: String = "demo-shuttle",
    val availableSeats: Int = 24,
    val totalSeats: Int = 24,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
)
