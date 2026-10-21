package com.unishuttle.app.domain.repository

import com.unishuttle.app.domain.model.ShuttleStop
import kotlinx.coroutines.flow.Flow

interface ShuttleStopRepository {
    fun observeStops(): Flow<List<ShuttleStop>>
}
