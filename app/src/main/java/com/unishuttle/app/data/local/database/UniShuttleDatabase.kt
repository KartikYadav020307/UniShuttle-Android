package com.unishuttle.app.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.unishuttle.app.data.local.dao.NotificationDao
import com.unishuttle.app.data.local.dao.RouteGeometryDao
import com.unishuttle.app.data.local.entity.CachedRouteGeometryEntity
import com.unishuttle.app.data.local.entity.NotificationEntity
import com.unishuttle.app.data.local.entity.NotificationPreferenceEntity
import com.unishuttle.app.data.local.entity.StopAlertEntity

@Database(
    entities = [
        NotificationEntity::class,
        StopAlertEntity::class,
        NotificationPreferenceEntity::class,
        CachedRouteGeometryEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class UniShuttleDatabase : RoomDatabase() {
    abstract fun notificationDao(): NotificationDao
    abstract fun routeGeometryDao(): RouteGeometryDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `cached_route_geometries` (
                        `routeId` TEXT NOT NULL,
                        `routeVersion` INTEGER NOT NULL,
                        `stopHash` TEXT NOT NULL,
                        `geometryHash` TEXT NOT NULL,
                        `pointsJson` TEXT NOT NULL,
                        `pointCount` INTEGER NOT NULL,
                        `isFallback` INTEGER NOT NULL,
                        `updatedAtMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`routeId`)
                    )
                    """.trimIndent(),
                )
            }
        }
    }
}
