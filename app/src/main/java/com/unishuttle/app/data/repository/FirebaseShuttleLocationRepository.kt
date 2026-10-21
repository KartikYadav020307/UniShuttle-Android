package com.unishuttle.app.data.repository

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.unishuttle.app.data.mapper.toDomain
import com.unishuttle.app.data.remote.dto.FirebaseShuttleLocationDto
import com.unishuttle.app.domain.model.ShuttleLocation
import com.unishuttle.app.domain.repository.ShuttleLocationRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseShuttleLocationRepository @Inject constructor(
    firebaseDatabase: FirebaseDatabase,
) : ShuttleLocationRepository {
    private val shuttlesRef = firebaseDatabase.reference.child("shuttles")

    override fun observeShuttleLocations(): Flow<List<ShuttleLocation>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val shuttles = snapshot.children.mapNotNull { shuttleSnapshot ->
                    val id = shuttleSnapshot.key ?: return@mapNotNull null
                    shuttleSnapshot.getValue(FirebaseShuttleLocationDto::class.java)?.toDomain(id)
                }
                trySend(shuttles)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        shuttlesRef.addValueEventListener(listener)
        awaitClose { shuttlesRef.removeEventListener(listener) }
    }

    override suspend fun updateDriverLocation(
        shuttleId: String,
        latitude: Double,
        longitude: Double,
        heading: Float,
        updatedAtMillis: Long,
    ) {
        val dto = FirebaseShuttleLocationDto(
            latitude = latitude,
            longitude = longitude,
            heading = heading.toDouble(),
            updatedAtMillis = updatedAtMillis,
        )
        shuttlesRef.child(shuttleId).setValue(dto).await()
    }
}
