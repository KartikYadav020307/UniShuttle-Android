package com.unishuttle.app

import android.app.Application
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.platform.app.InstrumentationRegistry
import com.unishuttle.app.data.local.database.UniShuttleDatabase
import com.unishuttle.app.data.local.entity.CachedRouteGeometryEntity
import com.unishuttle.app.data.local.entity.NotificationEntity
import com.unishuttle.app.data.local.entity.NotificationPreferenceEntity
import com.unishuttle.app.data.local.entity.StopAlertEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class RoomMigrationTest {

    @Test
    fun migrationFromCommittedVersion1PreservesRecordsAndSupportsV2Operations() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val driver = AndroidSQLiteDriver()
        val databaseFile = instrumentation.targetContext.getDatabasePath(TEST_DATABASE_NAME)
        databaseFile.parentFile?.mkdirs()
        val databaseFactory = {
            Room.databaseBuilder(
                instrumentation.targetContext,
                UniShuttleDatabase::class.java,
                databaseFile.absolutePath,
            )
                .setDriver(driver)
                .addMigrations(UniShuttleDatabase.MIGRATION_1_2)
                .allowMainThreadQueries()
                .build()
        }
        val helper = MigrationTestHelper(
            instrumentation,
            databaseFile,
            driver,
            UniShuttleDatabase::class,
            databaseFactory,
            emptyList(),
        )

        try {
            val version1 = helper.createDatabase(1)
            try {
                version1.execute(
                    """
                    INSERT INTO notifications (id, title, body, receivedAtMillis, type)
                    VALUES (?, ?, ?, ?, ?)
                    """.trimIndent(),
                    NOTIFICATION_ID,
                    "Duty active",
                    "Loop A is in service",
                    NOTIFICATION_TIME,
                    "duty",
                )
                version1.execute(
                    """
                    INSERT INTO notifications (id, title, body, receivedAtMillis, type)
                    VALUES (?, ?, ?, ?, ?)
                    """.trimIndent(),
                    SECOND_NOTIFICATION_ID,
                    "Stop alert",
                    "Main Gate threshold reached",
                    NOTIFICATION_TIME - 1_000L,
                    "stop_alert",
                )
                version1.execute(
                    """
                    INSERT INTO stop_alerts (stopId, stopName, thresholdMinutes, subscribedAtMillis)
                    VALUES (?, ?, ?, ?)
                    """.trimIndent(),
                    STOP_ID,
                    "Main Gate",
                    5,
                    ALERT_TIME,
                )
                version1.execute(
                    """
                    INSERT INTO stop_alerts (stopId, stopName, thresholdMinutes, subscribedAtMillis)
                    VALUES (?, ?, ?, ?)
                    """.trimIndent(),
                    SECOND_STOP_ID,
                    "Silver Jubilee Tower",
                    10,
                    ALERT_TIME - 1_000L,
                )
                version1.execute(
                    """
                    INSERT INTO notification_preferences (topicId, label, enabled)
                    VALUES (?, ?, ?)
                    """.trimIndent(),
                    "route_updates",
                    "Route alerts",
                    1,
                )
                version1.execute(
                    """
                    INSERT INTO notification_preferences (topicId, label, enabled)
                    VALUES (?, ?, ?)
                    """.trimIndent(),
                    "duty_updates",
                    "Duty alerts",
                    0,
                )
            } finally {
                version1.close()
            }

            val migrated = helper.runMigrationsAndValidate(
                2,
                listOf(UniShuttleDatabase.MIGRATION_1_2),
            )
            try {
                assertNotificationRow(migrated)
                assertStopAlertRow(migrated)
                assertPreferenceRow(migrated)
                assertCachedGeometrySchema(migrated)
                assertCacheConstraints(migrated)
            } finally {
                migrated.close()
            }

            val database = databaseFactory()
            try {
                val notifications = database.notificationDao().observeNotifications().first()
                assertEquals(
                    listOf(
                        NotificationEntity(
                            NOTIFICATION_ID,
                            "Duty active",
                            "Loop A is in service",
                            NOTIFICATION_TIME,
                            "duty",
                        ),
                        NotificationEntity(
                            SECOND_NOTIFICATION_ID,
                            "Stop alert",
                            "Main Gate threshold reached",
                            NOTIFICATION_TIME - 1_000L,
                            "stop_alert",
                        ),
                    ),
                    notifications,
                )
                assertEquals(
                    StopAlertEntity(STOP_ID, "Main Gate", 5, ALERT_TIME),
                    database.notificationDao().getAlert(STOP_ID),
                )
                assertEquals(
                    StopAlertEntity(SECOND_STOP_ID, "Silver Jubilee Tower", 10, ALERT_TIME - 1_000L),
                    database.notificationDao().getAlert(SECOND_STOP_ID),
                )
                assertEquals(
                    NotificationPreferenceEntity("route_updates", "Route alerts", true),
                    database.notificationDao().getPreference("route_updates"),
                )
                assertEquals(
                    NotificationPreferenceEntity("duty_updates", "Duty alerts", false),
                    database.notificationDao().getPreference("duty_updates"),
                )

                val nextNotification = NotificationEntity(
                    id = "notification_after_migration",
                    title = "Route update",
                    body = "Loop A resumed",
                    receivedAtMillis = NOTIFICATION_TIME + 1,
                    type = "route",
                )
                database.notificationDao().insertNotification(nextNotification)
                assertEquals(nextNotification, database.notificationDao().observeNotifications().first().first())

                database.notificationDao().updatePreferenceEnabled("route_updates", false)
                assertFalse(database.notificationDao().getPreference("route_updates")!!.enabled)

                database.notificationDao().deleteAlert(STOP_ID)
                assertNull(database.notificationDao().getAlert(STOP_ID))
                assertEquals(
                    StopAlertEntity(SECOND_STOP_ID, "Silver Jubilee Tower", 10, ALERT_TIME - 1_000L),
                    database.notificationDao().getAlert(SECOND_STOP_ID),
                )

                val cachedGeometry = CachedRouteGeometryEntity(
                    routeId = "route_loop_a",
                    routeVersion = 2,
                    stopHash = "stop-hash",
                    geometryHash = "geometry-hash",
                    pointsJson = "[]",
                    pointCount = 0,
                    isFallback = true,
                    updatedAtMillis = 1_800_000_000_000L,
                )
                database.routeGeometryDao().insertRouteGeometry(cachedGeometry)
                assertEquals(cachedGeometry, database.routeGeometryDao().getRouteGeometry(cachedGeometry.routeId))
            } finally {
                database.close()
            }
        } finally {
            deleteDatabaseFiles(databaseFile)
        }
    }

    private fun assertNotificationRow(database: SQLiteConnection) {
        database.prepare(
            "SELECT id, title, body, receivedAtMillis, type FROM notifications ORDER BY receivedAtMillis DESC",
        ).use { statement ->
            assertTrue(statement.step())
            assertEquals(NOTIFICATION_ID, statement.getText(0))
            assertEquals("Duty active", statement.getText(1))
            assertEquals("Loop A is in service", statement.getText(2))
            assertEquals(NOTIFICATION_TIME, statement.getLong(3))
            assertEquals("duty", statement.getText(4))
            assertTrue(statement.step())
            assertEquals(SECOND_NOTIFICATION_ID, statement.getText(0))
            assertEquals("Stop alert", statement.getText(1))
            assertEquals("Main Gate threshold reached", statement.getText(2))
            assertEquals(NOTIFICATION_TIME - 1_000L, statement.getLong(3))
            assertEquals("stop_alert", statement.getText(4))
            assertFalse(statement.step())
        }
    }

    private fun assertStopAlertRow(database: SQLiteConnection) {
        database.prepare(
            "SELECT stopId, stopName, thresholdMinutes, subscribedAtMillis " +
                "FROM stop_alerts ORDER BY subscribedAtMillis DESC",
        )
            .use { statement ->
                assertTrue(statement.step())
                assertEquals(STOP_ID, statement.getText(0))
                assertEquals("Main Gate", statement.getText(1))
                assertEquals(5, statement.getInt(2))
                assertEquals(ALERT_TIME, statement.getLong(3))
                assertTrue(statement.step())
                assertEquals(SECOND_STOP_ID, statement.getText(0))
                assertEquals("Silver Jubilee Tower", statement.getText(1))
                assertEquals(10, statement.getInt(2))
                assertEquals(ALERT_TIME - 1_000L, statement.getLong(3))
                assertFalse(statement.step())
            }
    }

    private fun assertPreferenceRow(database: SQLiteConnection) {
        database.prepare(
            "SELECT topicId, label, enabled FROM notification_preferences ORDER BY topicId",
        ).use { statement ->
            assertTrue(statement.step())
            assertEquals("duty_updates", statement.getText(0))
            assertEquals("Duty alerts", statement.getText(1))
            assertEquals(0, statement.getInt(2))
            assertTrue(statement.step())
            assertEquals("route_updates", statement.getText(0))
            assertEquals("Route alerts", statement.getText(1))
            assertEquals(1, statement.getInt(2))
            assertFalse(statement.step())
        }
    }

    private fun assertCachedGeometrySchema(database: SQLiteConnection) {
        val expectedColumns = listOf(
            "routeId" to "TEXT",
            "routeVersion" to "INTEGER",
            "stopHash" to "TEXT",
            "geometryHash" to "TEXT",
            "pointsJson" to "TEXT",
            "pointCount" to "INTEGER",
            "isFallback" to "INTEGER",
            "updatedAtMillis" to "INTEGER",
        )
        val actualColumns = mutableListOf<Triple<String, String, Pair<Int, Int>>>()
        database.prepare("PRAGMA table_info(`cached_route_geometries`)").use { statement ->
            while (statement.step()) {
                actualColumns += Triple(
                    statement.getText(statement.getColumnNames().indexOf("name")),
                    statement.getText(statement.getColumnNames().indexOf("type")),
                    statement.getInt(statement.getColumnNames().indexOf("notnull")) to
                        statement.getInt(statement.getColumnNames().indexOf("pk")),
                )
            }
        }

        assertEquals(expectedColumns, actualColumns.map { it.first to it.second })
        assertTrue(actualColumns.all { it.third.first == 1 })
        assertEquals(1, actualColumns.single { it.first == "routeId" }.third.second)
        assertTrue(actualColumns.filterNot { it.first == "routeId" }.all { it.third.second == 0 })

        val indexes = mutableListOf<Pair<String, Boolean>>()
        database.prepare("PRAGMA index_list(`cached_route_geometries`)").use { statement ->
            while (statement.step()) {
                indexes += statement.getText(statement.getColumnNames().indexOf("name")) to
                    (statement.getInt(statement.getColumnNames().indexOf("unique")) == 1)
            }
        }
        assertEquals(1, indexes.size)
        assertTrue(indexes.single().first.startsWith("sqlite_autoindex_cached_route_geometries"))
        assertTrue(indexes.single().second)
    }

    private fun assertCacheConstraints(database: SQLiteConnection) {
        val insert = """
            INSERT INTO cached_route_geometries
            (routeId, routeVersion, stopHash, geometryHash, pointsJson, pointCount, isFallback, updatedAtMillis)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        database.execute(insert, "route_primary_key", 1, "hash", "geometry", "[]", 2, 0, 1L)

        val duplicatePrimaryKeyRejected = try {
            database.execute(insert, "route_primary_key", 1, "hash", "geometry", "[]", 2, 0, 1L)
            false
        } catch (_: SQLiteConstraintException) {
            true
        }
        assertTrue("routeId primary key must reject duplicate cached routes", duplicatePrimaryKeyRejected)

        val missingRequiredColumnRejected = try {
            database.execute("INSERT INTO cached_route_geometries (routeId) VALUES ('missing_required_columns')")
            false
        } catch (_: SQLiteConstraintException) {
            true
        }
        assertTrue("Cached geometry columns must remain NOT NULL", missingRequiredColumnRejected)
    }

    private fun SQLiteConnection.execute(sql: String, vararg values: Any) {
        prepare(sql).use { statement ->
            values.forEachIndexed { index, value ->
                when (value) {
                    is String -> statement.bindText(index + 1, value)
                    is Boolean -> statement.bindBoolean(index + 1, value)
                    is Number -> statement.bindLong(index + 1, value.toLong())
                    else -> error("Unsupported SQLite bind value: ${value::class.java.name}")
                }
            }
            statement.step()
        }
    }

    private fun deleteDatabaseFiles(databaseFile: File) {
        databaseFile.delete()
        File("${databaseFile.path}-shm").delete()
        File("${databaseFile.path}-wal").delete()
    }

    private companion object {
        const val TEST_DATABASE_NAME = "room-migration-v1-to-v2-test"
        const val NOTIFICATION_ID = "notification_legacy_1"
        const val SECOND_NOTIFICATION_ID = "notification_legacy_2"
        const val NOTIFICATION_TIME = 1_700_000_123_456L
        const val STOP_ID = "stop_main_gate"
        const val SECOND_STOP_ID = "stop_sjt"
        const val ALERT_TIME = 1_700_000_000_000L
    }
}
