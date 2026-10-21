package com.unishuttle.app.di

import com.unishuttle.app.data.repository.FirebaseAuthRepository
import com.unishuttle.app.data.repository.FirebaseSeatAvailabilityRepository
import com.unishuttle.app.data.repository.FirebaseShuttleLocationRepository
import com.unishuttle.app.data.repository.FirebaseShuttleStopRepository
import com.unishuttle.app.data.repository.GoogleDirectionsRouteRepository
import com.unishuttle.app.data.repository.LocalNotificationRepository
import com.unishuttle.app.domain.repository.AuthRepository
import com.unishuttle.app.domain.repository.NotificationRepository
import com.unishuttle.app.domain.repository.RouteRepository
import com.unishuttle.app.domain.repository.SeatAvailabilityRepository
import com.unishuttle.app.domain.repository.ShuttleLocationRepository
import com.unishuttle.app.domain.repository.ShuttleStopRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindShuttleLocationRepository(
        repository: FirebaseShuttleLocationRepository,
    ): ShuttleLocationRepository

    @Binds
    @Singleton
    abstract fun bindShuttleStopRepository(
        repository: FirebaseShuttleStopRepository,
    ): ShuttleStopRepository

    @Binds
    @Singleton
    abstract fun bindRouteRepository(
        repository: GoogleDirectionsRouteRepository,
    ): RouteRepository

    @Binds
    @Singleton
    abstract fun bindSeatAvailabilityRepository(
        repository: FirebaseSeatAvailabilityRepository,
    ): SeatAvailabilityRepository

    @Binds
    @Singleton
    abstract fun bindNotificationRepository(
        repository: LocalNotificationRepository,
    ): NotificationRepository

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        repository: FirebaseAuthRepository,
    ): AuthRepository
}
