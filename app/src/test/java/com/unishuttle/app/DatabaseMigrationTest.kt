package com.unishuttle.app

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.unishuttle.app.data.local.entity.CachedRouteGeometryEntity
import com.unishuttle.app.domain.model.CampusLatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CachedRouteGeometrySerializationTest {

    @Test
    fun `cached route geometry serialization and deserialization integrity`() {
        val gson = Gson()
        val originalPoints = listOf(
            CampusLatLng(latitude = 12.9692, longitude = 79.1559),
            CampusLatLng(latitude = 12.9712, longitude = 79.1594),
            CampusLatLng(latitude = 12.9735, longitude = 79.1635),
        )

        val pointsJson = gson.toJson(originalPoints)
        val entity = CachedRouteGeometryEntity(
            routeId = "route_loop_a",
            routeVersion = 1,
            stopHash = "sha256_mock_hash",
            geometryHash = "sha256_mock_geometry_hash",
            pointsJson = pointsJson,
            pointCount = originalPoints.size,
            isFallback = false,
            updatedAtMillis = System.currentTimeMillis(),
        )

        assertEquals("route_loop_a", entity.routeId)
        assertEquals(1, entity.routeVersion)
        assertEquals(3, entity.pointCount)
        assertFalse(entity.isFallback)

        val type = object : TypeToken<List<CampusLatLng>>() {}.type
        val deserialized: List<CampusLatLng> = gson.fromJson(entity.pointsJson, type)

        assertEquals(originalPoints.size, deserialized.size)
        assertEquals(originalPoints[0].latitude, deserialized[0].latitude, 0.00001)
        assertEquals(originalPoints[0].longitude, deserialized[0].longitude, 0.00001)
        assertEquals(originalPoints[2].latitude, deserialized[2].latitude, 0.00001)
        assertEquals(originalPoints[2].longitude, deserialized[2].longitude, 0.00001)
    }
}
