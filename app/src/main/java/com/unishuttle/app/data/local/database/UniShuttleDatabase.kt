package com.unishuttle.app.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.unishuttle.app.data.local.dao.NotificationDao
import com.unishuttle.app.data.local.entity.NotificationEntity
import com.unishuttle.app.data.local.entity.NotificationPreferenceEntity
import com.unishuttle.app.data.local.entity.StopAlertEntity

@Database(
    entities = [
        NotificationEntity::class,
        StopAlertEntity::class,
        NotificationPreferenceEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class UniShuttleDatabase : RoomDatabase() {
    abstract fun notificationDao(): NotificationDao
}
