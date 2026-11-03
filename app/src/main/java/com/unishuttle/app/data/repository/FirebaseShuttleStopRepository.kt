package com.unishuttle.app.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.unishuttle.app.data.mapper.toDomain
import com.unishuttle.app.data.remote.dto.FirestoreShuttleStopDto
import com.unishuttle.app.domain.model.ShuttleStop
import com.unishuttle.app.domain.repository.ShuttleStopRepository
import com.unishuttle.app.presentation.ui.screen.map.CampusMapDefaults
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
        // Emit static fallback stops immediately so the UI is immediately interactive
        trySend(CampusMapDefaults.DefaultStops)

        val registration = stopsRef
            .orderBy("order", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Firestore stops error: ${error.message}; keeping static fallback stops")
                    trySend(CampusMapDefaults.DefaultStops)
                    return@addSnapshotListener
                }

                val remoteStops = snapshot
                    ?.documents
                    ?.mapNotNull { document ->
                        document.toObject(FirestoreShuttleStopDto::class.java)
                            ?.toDomain(document.id)
                    }
                    .orEmpty()

                if (remoteStops.isNotEmpty()) {
                    Log.i(TAG, "Loaded ${remoteStops.size} remote stops from Firestore; replacing fallback")
                    trySend(remoteStops)
                } else {
                    Log.i(TAG, "Firestore stops collection is empty; keeping static fallback stops")
                    trySend(CampusMapDefaults.DefaultStops)
                }
            }

        awaitClose { registration.remove() }
    }

    companion object {
        private const val TAG = "FirebaseStopRepo"
    }
}
