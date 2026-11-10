package com.unishuttle.app.presentation.viewmodel

data class DriverSeatUiState(
    val shuttleId: String = "",
    val availableSeats: Int = 0,
    val totalSeats: Int = 0,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
)
