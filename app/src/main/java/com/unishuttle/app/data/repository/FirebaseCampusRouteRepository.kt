package com.unishuttle.app.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.unishuttle.app.data.mapper.toDomain
import com.unishuttle.app.data.remote.dto.FirestoreCampusRouteDto
import com.unishuttle.app.domain.model.CampusRoute
import com.unishuttle.app.domain.repository.CampusRouteRepository
import com.unishuttle.app.presentation.ui.screen.map.CampusMapDefaults
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Singleton
class FirebaseCampusRouteRepository @Inject constructor(
    firestore: FirebaseFirestore,
) : CampusRouteRepository {

    private val routesRef = firestore.collection("routes")

    override fun observeRoutes(): Flow<List<CampusRoute>> = callbackFlow {
        // Emit static fallback routes immediately so the UI is immediately interactive
        trySend(CampusMapDefaults.DefaultRoutes)

        val registration = routesRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Timber.tag(TAG).w("Firestore routes error: ${error.message}; keeping default fallback routes")
                trySend(CampusMapDefaults.DefaultRoutes)
                return@addSnapshotListener
            }

            val remoteRoutes = snapshot
                ?.documents
                ?.mapNotNull { document ->
                    document.toObject(FirestoreCampusRouteDto::class.java)
                        ?.toDomain(document.id)
                }
                ?.filter { it.isActive }
                .orEmpty()

            if (remoteRoutes.isNotEmpty()) {
                Timber.tag(TAG).i("Loaded ${remoteRoutes.size} remote routes from Firestore; replacing defaults")
                trySend(remoteRoutes)
            } else {
                Timber.tag(TAG).i("Firestore routes collection is empty; keeping default fallback routes")
                trySend(CampusMapDefaults.DefaultRoutes)
            }
        }

        awaitClose { registration.remove() }
    }

    companion object {
        private const val TAG = "FirebaseRouteRepo"
    }
}
