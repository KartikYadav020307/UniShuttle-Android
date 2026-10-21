package com.unishuttle.app.di

import android.content.Context
import androidx.room.Room
import com.unishuttle.app.data.local.dao.NotificationDao
import com.unishuttle.app.data.local.database.UniShuttleDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): UniShuttleDatabase =
        Room.databaseBuilder(
            context,
            UniShuttleDatabase::class.java,
            "unishuttle.db",
        ).build()

    @Provides
    @Singleton
    fun provideNotificationDao(database: UniShuttleDatabase): NotificationDao =
        database.notificationDao()
}
