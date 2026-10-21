package com.unishuttle.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.unishuttle.app.domain.model.AppUser
import com.unishuttle.app.domain.model.UserRole
import com.unishuttle.app.domain.repository.AuthRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : AuthRepository {

    private val usersRef = firestore.collection("users")

    // ── Reactive auth state ──────────────────────────────────────────────────

    override val currentUser: Flow<AppUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val firebaseUser = firebaseAuth.currentUser
            if (firebaseUser == null) {
                trySend(null)
            } else {
                usersRef.document(firebaseUser.uid).get()
                    .addOnSuccessListener { doc -> trySend(doc.toAppUser(firebaseUser.uid)) }
                    .addOnFailureListener {
                        // Firestore read failed — emit a minimal user to avoid being stuck
                        trySend(
                            AppUser(
                                uid = firebaseUser.uid,
                                email = firebaseUser.email.orEmpty(),
                                displayName = firebaseUser.displayName.orEmpty(),
                                role = UserRole.STUDENT,
                            ),
                        )
                    }
            }
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override suspend fun getCurrentUser(): AppUser? {
        val firebaseUser = auth.currentUser ?: return null
        return runCatching {
            usersRef.document(firebaseUser.uid).get().await().toAppUser(firebaseUser.uid)
        }.getOrNull()
    }

    // ── Google Sign-In ───────────────────────────────────────────────────────

    override suspend fun signInWithGoogle(
        idToken: String,
        role: UserRole,
    ): Result<AppUser> = runCatching {

        // 1. Exchange the Google ID token for a Firebase credential
        val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
        val authResult = auth.signInWithCredential(firebaseCredential).await()
        val firebaseUser = authResult.user
            ?: error("Firebase sign-in returned a null user.")

        val email = firebaseUser.email.orEmpty()

        // 2. Domain check — students must use the VIT email domain
        if (role == UserRole.STUDENT && !email.endsWith(STUDENT_DOMAIN)) {
            auth.signOut()
            // Throw a typed exception so the ViewModel can show the right message
            throw DomainRestrictionException(
                "Please use your VIT student email ($STUDENT_DOMAIN) to sign in as a Student.",
            )
        }

        // 3. Auto-assign a shuttleId for drivers on first sign-in
        val shuttleId = if (role == UserRole.DRIVER) {
            "shuttle-${firebaseUser.uid.take(6)}"
        } else {
            ""
        }

        // 4. Write / merge user document in Firestore
        //    merge:true preserves existing fields (e.g. shuttleId set by an admin)
        usersRef.document(firebaseUser.uid).set(
            mapOf(
                "uid" to firebaseUser.uid,
                "email" to email,
                "displayName" to (firebaseUser.displayName.orEmpty()),
                "role" to role.name,
                "shuttleId" to shuttleId,
            ),
        ).await()

        AppUser(
            uid = firebaseUser.uid,
            email = email,
            displayName = firebaseUser.displayName.orEmpty(),
            role = role,
            shuttleId = shuttleId,
        )
    }

    // ── Sign Out ─────────────────────────────────────────────────────────────

    override suspend fun logout() {
        auth.signOut()
        // Credential Manager clearCredentialState() requires an Activity context,
        // so it is called from the ViewModel using the application context.
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    private fun com.google.firebase.firestore.DocumentSnapshot.toAppUser(uid: String): AppUser? {
        if (!exists()) return null
        return AppUser(
            uid = uid,
            email = getString("email").orEmpty(),
            displayName = getString("displayName").orEmpty(),
            role = UserRole.fromString(getString("role").orEmpty()),
            shuttleId = getString("shuttleId").orEmpty(),
        )
    }

    companion object {
        const val STUDENT_DOMAIN = "@vitstudent.ac.in"
    }
}

/** Thrown when an email fails the per-role domain restriction. */
class DomainRestrictionException(message: String) : Exception(message)
