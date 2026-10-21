package com.unishuttle.app.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.unishuttle.app.data.mapper.toDomain
import com.unishuttle.app.data.remote.dto.FirestoreSeatAvailabilityDto
import com.unishuttle.app.domain.model.SeatAvailability
import com.unishuttle.app.domain.repository.SeatAvailabilityRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseSeatAvailabilityRepository @Inject constructor(
    firestore: FirebaseFirestore,
) : SeatAvailabilityRepository {
    private val seatsRef = firestore.collection("seatAvailability")

    override fun observeSeatAvailability(): Flow<List<SeatAvailability>> = callbackFlow {
        val registration = seatsRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }

            val seats = snapshot
                ?.documents
                ?.mapNotNull { document ->
                    document.toObject(FirestoreSeatAvailabilityDto::class.java)
                        ?.toDomain(document.id)
                }
                .orEmpty()

            trySend(seats)
        }

        awaitClose { registration.remove() }
    }

    override suspend fun updateSeatAvailability(
        shuttleId: String,
        availableSeats: Int,
        totalSeats: Int,
        updatedAtMillis: Long,
    ) {
        val safeTotal = totalSeats.coerceAtLeast(0)
        val data = mapOf(
            "availableSeats" to availableSeats.coerceIn(0, safeTotal),
            "totalSeats" to safeTotal,
            "updatedAtMillis" to updatedAtMillis,
        )

        seatsRef.document(shuttleId).set(data).await()
    }
}
