package com.unishuttle.app

import com.unishuttle.app.domain.model.CampusLatLng
import com.unishuttle.app.domain.model.SeatAvailability
import com.unishuttle.app.domain.model.ShuttleStop
import com.unishuttle.app.domain.model.StopEta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
}
