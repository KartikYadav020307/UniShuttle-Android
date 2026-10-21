package com.unishuttle.app.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.unishuttle.app.data.mapper.toDomain
import com.unishuttle.app.data.remote.dto.FirestoreShuttleStopDto
import com.unishuttle.app.domain.model.ShuttleStop
import com.unishuttle.app.domain.repository.ShuttleStopRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseShuttleStopRepository @Inject constructor(
    firestore: FirebaseFirestore,
) : ShuttleStopRepository {
    private val stopsRef = firestore.collection("stops")

    override fun observeStops(): Flow<List<ShuttleStop>> = callbackFlow {
        val registration = stopsRef
            .orderBy("order", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                val stops = snapshot
                    ?.documents
                    ?.mapNotNull { document ->
                        document.toObject(FirestoreShuttleStopDto::class.java)
                            ?.toDomain(document.id)
                    }
                    .orEmpty()

                trySend(stops)
            }

        awaitClose { registration.remove() }
    }
}
