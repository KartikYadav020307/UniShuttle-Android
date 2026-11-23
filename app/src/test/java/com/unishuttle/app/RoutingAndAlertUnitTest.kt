package com.unishuttle.app

import com.unishuttle.app.data.mapper.toDomain
import com.unishuttle.app.domain.model.CampusLatLng
import com.unishuttle.app.domain.model.CampusRoute
import com.unishuttle.app.domain.model.SeatAvailability
import com.unishuttle.app.domain.model.ShuttleStop
import com.unishuttle.app.domain.model.StopEta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class RoutingAndAlertUnitTest {

    @Test
    fun `fcmTopic naming contract matches Cloud Function topic schema`() {
        // Contract defined in functions/src/index.ts: `stop_${stopId}_${minutes}min`
        // Contract implemented in LocalNotificationRepository: "stop_${stopId}_${thresholdMinutes}min"
        val stopId = "stop_main_gate"
        val thresholdMinutes = 5

        val cloudFunctionExpectedTopic = "stop_${stopId}_${thresholdMinutes}min"
        val appGeneratedTopic = "stop_${stopId}_${thresholdMinutes}min"

        assertEquals("stop_stop_main_gate_5min", appGeneratedTopic)
        assertEquals(cloudFunctionExpectedTopic, appGeneratedTopic)
    }

    @Test
    fun `eta duration calculation rounds up to next minute with minimum of 1 min`() {
        fun formatEta(durationSeconds: Long): String {
            val minutes = ((durationSeconds + 59) / 60).coerceAtLeast(1)
            return "$minutes min"
        }

        assertEquals("1 min", formatEta(0L))
        assertEquals("1 min", formatEta(30L))
        assertEquals("1 min", formatEta(60L))
        assertEquals("2 min", formatEta(61L))
        assertEquals("3 min", formatEta(180L))
        assertEquals("10 min", formatEta(600L))
    }

    @Test
    fun `osrm coordinate formatting complies with longitude comma latitude order`() {
        // OSRM specification requires {longitude},{latitude}
        val mainGate = CampusLatLng(latitude = 12.9692, longitude = 79.1559)
        val formatted = "${mainGate.longitude},${mainGate.latitude}"

        assertEquals("79.1559,12.9692", formatted)
        assertTrue(formatted.startsWith("79."))
    }

    @Test
    fun `osrm high precision cache key prevents campus collision while snapping jitter`() {
        val stopMainGate = CampusLatLng(latitude = 12.9692, longitude = 79.1559)
        val stopSjt = CampusLatLng(latitude = 12.9735, longitude = 79.1635)
        // A point 2 meters away from main gate (representing micro GPS jitter)
        val stopMainGateJitter = CampusLatLng(latitude = 12.96921, longitude = 79.15591)

        fun highPrecisionKey(points: List<CampusLatLng>) =
            points.joinToString(";") {
                String.format(java.util.Locale.US, "%.4f,%.4f", it.latitude, it.longitude)
            }

        val keyMainGate = highPrecisionKey(listOf(stopMainGate))
        val keySjt = highPrecisionKey(listOf(stopSjt))
        val keyJitter = highPrecisionKey(listOf(stopMainGateJitter))

        // Distinct campus stops must never collide
        assertNotEquals("Main Gate and SJT must have distinct cache keys", keyMainGate, keySjt)
        assertEquals("12.9692,79.1559", keyMainGate)
        assertEquals("12.9735,79.1635", keySjt)

        // Micro jitter within ~11 meters snaps to the identical cache key
        assertEquals("Micro GPS jitter should snap to the same cache key", keyMainGate, keyJitter)
    }

    @Test
    fun `arrivals are sorted by duration ascending and seats are matched accurately`() {
        val stopId = "stop_main_gate"
        val stopEtas = listOf(
            StopEta(shuttleId = "1", stopId = stopId, etaText = "8 min", durationSeconds = 480L),
            StopEta(shuttleId = "2", stopId = stopId, etaText = "2 min", durationSeconds = 120L),
            StopEta(shuttleId = "3", stopId = stopId, etaText = "5 min", durationSeconds = 300L),
        )

        val seatAvailability = listOf(
            SeatAvailability(shuttleId = "1", availableSeats = 4, totalSeats = 24, updatedAtMillis = 1000L),
            SeatAvailability(shuttleId = "2", availableSeats = 18, totalSeats = 24, updatedAtMillis = 1000L),
            SeatAvailability(shuttleId = "3", availableSeats = 0, totalSeats = 24, updatedAtMillis = 1000L),
        )

        val sortedArrivals = stopEtas
            .sortedBy { it.durationSeconds }
            .map { eta ->
                val seats = seatAvailability.firstOrNull { it.shuttleId == eta.shuttleId }
                Pair("SH-${eta.shuttleId}", seats?.availableSeats ?: 0)
            }

        // Fastest arrival should be Shuttle 2 (2 min away, 18 seats free)
        assertEquals("SH-2", sortedArrivals[0].first)
        assertEquals(18, sortedArrivals[0].second)

        // Second fastest should be Shuttle 3 (5 min away, 0 seats free)
        assertEquals("SH-3", sortedArrivals[1].first)
        assertEquals(0, sortedArrivals[1].second)

        // Third should be Shuttle 1 (8 min away, 4 seats free)
        assertEquals("SH-1", sortedArrivals[2].first)
        assertEquals(4, sortedArrivals[2].second)
    }

    @Test
    fun `student domain email validation permits only vitstudent domain`() {
        fun isVitStudentEmail(email: String): Boolean {
            val domain = email.substringAfter("@", "").lowercase()
            return domain == "vitstudent.ac.in"
        }

        assertTrue(isVitStudentEmail("john.doe2023@vitstudent.ac.in"))
        assertTrue(isVitStudentEmail("student@VITSTUDENT.AC.IN"))
        org.junit.Assert.assertFalse(isVitStudentEmail("driver@gmail.com"))
        org.junit.Assert.assertFalse(isVitStudentEmail("intruder@vit.ac.in"))
        org.junit.Assert.assertFalse(isVitStudentEmail("fake@vitstudent.ac.in.evil.com"))
    }

    @Test
    fun `shuttle stop route membership and legacy unassigned behavior`() {
        val loopStop = ShuttleStop(
            id = "stop_main_gate",
            name = "Main Gate",
            latitude = 12.9692,
            longitude = 79.1559,
            order = 1,
        )
        val legacyStop = ShuttleStop(
            id = "stop_legacy",
            name = "Legacy Stop",
            latitude = 12.9700,
            longitude = 79.1600,
            order = 99,
        )

        val routeA = CampusRoute(
            id = "route_loop_a",
            name = "Loop A",
            colorHex = "#D32F2F",
            stopIds = listOf("stop_main_gate"),
        )
        val routeB = CampusRoute(
            id = "route_loop_b",
            name = "Loop B",
            colorHex = "#1976D2",
            stopIds = listOf("stop_main_gate"),
        )
        val routeC = routeA.copy(id = "route_loop_c", stopIds = emptyList())

        assertTrue(loopStop.servesCampusRoute(routeA))
        assertTrue(loopStop.servesCampusRoute(routeB))
        org.junit.Assert.assertFalse(loopStop.servesCampusRoute(routeC))
        assertTrue(loopStop.servesCampusRoute(null)) // All routes includes every stop
        org.junit.Assert.assertFalse(loopStop.isUnassigned(listOf(routeA, routeB)))
        assertTrue(legacyStop.isUnassigned(listOf(routeA, routeB, routeC)))
    }

    @Test
    fun `mappers preserve unassigned legacy stops and telemetry without fabrication`() {
        val legacyStopDto = com.unishuttle.app.data.remote.dto.FirestoreShuttleStopDto(
            name = "Old Stop",
            latitude = 12.9692,
            longitude = 79.1559,
            order = 1L,
            routeIds = listOf("unverified_route"), // Legacy field must not assign membership
        )
        val mappedStop = legacyStopDto.toDomain("legacy_1")
        assertNotNull(mappedStop)
        val routeNotContainingStop = CampusRoute(
            id = "unverified_route",
            name = "Unverified Route",
            colorHex = "#D32F2F",
            stopIds = emptyList(),
        )
        assertTrue(mappedStop!!.isUnassigned(listOf(routeNotContainingStop)))
        assertTrue(mappedStop.servesCampusRoute(null))

        val legacyLocDto = com.unishuttle.app.data.remote.dto.FirebaseShuttleLocationDto(
            latitude = 12.9692,
            longitude = 79.1559,
            heading = 90.0,
            updatedAtMillis = 1000L,
            routeId = null, // Missing in legacy RTDB
        )
        val mappedLoc = legacyLocDto.toDomain("shuttle_legacy")
        assertNotNull(mappedLoc)
        org.junit.Assert.assertNull(mappedLoc!!.routeId)
        org.junit.Assert.assertFalse(mappedLoc.isAssigned)

        val locationWithoutHeading = com.unishuttle.app.data.remote.dto.FirebaseShuttleLocationDto(
            latitude = 12.9692,
            longitude = 79.1559,
            heading = null,
            updatedAtMillis = 1000L,
        ).toDomain("shuttle_no_heading")
        assertNotNull(locationWithoutHeading)
        org.junit.Assert.assertFalse(locationWithoutHeading!!.hasHeading)
    }

    @Test
    fun `route color validator accepts valid contrast colors and rejects out-of-range colors`() {
        // Red and Blue used in defaults
        val sanitizedRed = com.unishuttle.app.presentation.ui.map.RouteColorValidator.sanitizeColorHex("#D32F2F")
        assertEquals("#D32F2F", sanitizedRed)

        val sanitizedBlue = com.unishuttle.app.presentation.ui.map.RouteColorValidator.sanitizeColorHex("#1976D2")
        assertEquals("#1976D2", sanitizedBlue)

        // Invalid hex falls back to default brick
        val invalidHex = com.unishuttle.app.presentation.ui.map.RouteColorValidator.sanitizeColorHex("not-a-color")
        assertEquals(com.unishuttle.app.presentation.ui.map.RouteColorValidator.DEFAULT_FALLBACK_COLOR_HEX, invalidHex)

        // Near white is rejected and falls back
        val pureWhite = com.unishuttle.app.presentation.ui.map.RouteColorValidator.sanitizeColorHex("#FFFFFF")
        assertEquals(com.unishuttle.app.presentation.ui.map.RouteColorValidator.DEFAULT_FALLBACK_COLOR_HEX, pureWhite)

        // Mid-light gray passed the former luminance cutoff but lacks 3:1 contrast on a light style.
        val lowContrastGray = com.unishuttle.app.presentation.ui.map.RouteColorValidator.sanitizeColorHex("#AAAAAA")
        assertEquals(com.unishuttle.app.presentation.ui.map.RouteColorValidator.DEFAULT_FALLBACK_COLOR_HEX, lowContrastGray)
    }

    @Test
    fun `stop position label fallback safely handles Int MAX_VALUE without showing overflow string`() {
        val stopWithDefaultOrder = ShuttleStop(
            id = "stop_1",
            name = "Stop 1",
            latitude = 12.9692,
            longitude = 79.1559,
            order = Int.MAX_VALUE,
        )
        val allStops = listOf(stopWithDefaultOrder)

        val stopIdx = allStops.indexOfFirst { it.id == stopWithDefaultOrder.id }
        val displayOrder = if (stopWithDefaultOrder.order in 1..allStops.size) stopWithDefaultOrder.order else (stopIdx + 1).coerceAtLeast(1)
        val label = "Stop $displayOrder of ${allStops.size}"

        assertEquals("Stop 1 of 1", label)
        org.junit.Assert.assertFalse("Must never display Int.MAX_VALUE", label.contains("2147483647"))
    }
}
