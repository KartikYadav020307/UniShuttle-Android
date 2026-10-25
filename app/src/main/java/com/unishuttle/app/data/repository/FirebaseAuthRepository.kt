package com.unishuttle.app.data.repository

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.unishuttle.app.domain.model.AppUser
import com.unishuttle.app.domain.model.UserRole
import com.unishuttle.app.domain.repository.AuthRepository
import com.unishuttle.app.domain.repository.AuthState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAuthRepository @Inject constructor(
    @ApplicationContext appContext: Context,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : AuthRepository {

    private val usersRef = firestore.collection("users")

    // clearCredentialState() works with the application context; only the account
    // picker (getCredential) needs an Activity, so that call stays in the ViewModel.
    private val credentialManager = CredentialManager.create(appContext)

    // ── Reactive auth state ──────────────────────────────────────────────────

    override val authState: Flow<AuthState> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val firebaseUser = firebaseAuth.currentUser
            if (firebaseUser == null) {
                trySend(AuthState.SignedOut)
            } else {
                usersRef.document(firebaseUser.uid).get()
                    .addOnSuccessListener { doc ->
                        val user = doc.toAppUser(firebaseUser.uid)
                        if (user == null) {
                            trySend(AuthState.ProfileUnavailable(PROFILE_LOAD_MESSAGE))
                        } else {
                            trySend(AuthState.SignedIn(user))
                        }
                    }
                    .addOnFailureListener {
                        // Do not fake a role or route the user when the profile
                        // cannot be loaded — surface it as a retryable error.
                        trySend(AuthState.ProfileUnavailable(PROFILE_LOAD_MESSAGE))
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
        // 1. Exchange the Google ID token for a Firebase session
        val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
        val firebaseUser = auth.signInWithCredential(firebaseCredential).await().user
            ?: error("Firebase sign-in returned a null user.")

        val email = firebaseUser.email.orEmpty()

        // 2. Campus domain lock for students (case-insensitive, exact domain).
        // TODO(Blaze): enforce the domain server-side with a Firebase blocking
        //   function once the project is on the Blaze plan, so a modified client
        //   cannot bypass this check.
        if (role == UserRole.STUDENT && !email.endsWith(STUDENT_DOMAIN, ignoreCase = true)) {
            abortSession()
            throw DomainRestrictionException(CAMPUS_ACCOUNT_MESSAGE)
        }

        // 3. The Google account email must be verified
        if (!firebaseUser.isEmailVerified) {
            abortSession()
            throw EmailNotVerifiedException(EMAIL_NOT_VERIFIED_MESSAGE)
        }

        // 4. The Firestore document is the source of truth for the role
        val docRef = usersRef.document(firebaseUser.uid)
        val snapshot = try {
            docRef.get().await()
        } catch (_: Exception) {
            throw ProfileUnavailableException(PROFILE_LOAD_MESSAGE)
        }

        if (snapshot.exists()) {
            // Existing profile: use its stored role, whatever the login toggle said.
            snapshot.toAppUser(firebaseUser.uid)
                ?: throw ProfileUnavailableException(PROFILE_LOAD_MESSAGE)
        } else if (role == UserRole.STUDENT) {
            val profile = mapOf(
                "uid" to firebaseUser.uid,
                "email" to email,
                "displayName" to firebaseUser.displayName.orEmpty(),
                "photoUrl" to (firebaseUser.photoUrl?.toString().orEmpty()),
                // The client must never write any role other than "student".
                "role" to ROLE_STUDENT,
                "createdAt" to FieldValue.serverTimestamp(),
            )
            try {
                docRef.set(profile).await()
            } catch (_: Exception) {
                throw ProfileUnavailableException(PROFILE_LOAD_MESSAGE)
            }
            AppUser(
                uid = firebaseUser.uid,
                email = email,
                displayName = firebaseUser.displayName.orEmpty(),
                role = UserRole.STUDENT,
            )
        } else {
            // TODO(Step 3): real driver sign-in and driver-only checks.
            abortSession()
            throw DriverNotEnabledException(DRIVER_NOT_ENABLED_MESSAGE)
        }
    }

    // ── Profile retry ────────────────────────────────────────────────────────

    override suspend fun reloadProfile(): Result<AppUser> = runCatching {
        val firebaseUser = auth.currentUser
            ?: throw ProfileUnavailableException(PROFILE_LOAD_MESSAGE)
        val snapshot = try {
            usersRef.document(firebaseUser.uid).get().await()
        } catch (_: Exception) {
            throw ProfileUnavailableException(PROFILE_LOAD_MESSAGE)
        }
        snapshot.toAppUser(firebaseUser.uid)
            ?: throw ProfileUnavailableException(PROFILE_LOAD_MESSAGE)
    }

    // ── Sign Out ─────────────────────────────────────────────────────────────

    override suspend fun logout() {
        auth.signOut()
        clearCredentialState()
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    /** Kills the just-created session and the Credential Manager state with it. */
    private suspend fun abortSession() {
        auth.signOut()
        clearCredentialState()
    }

    private suspend fun clearCredentialState() {
        try {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (_: Exception) {
            // Best-effort: the Firebase session is already gone.
        }
    }

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
        const val ROLE_STUDENT = "student"
        const val CAMPUS_ACCOUNT_MESSAGE = "Sign in with your campus account (@vitstudent.ac.in)."
        const val EMAIL_NOT_VERIFIED_MESSAGE = "Your Google account email isn't verified."
        const val DRIVER_NOT_ENABLED_MESSAGE = "Driver accounts aren't set up yet."
        const val PROFILE_LOAD_MESSAGE =
            "Couldn't load your account. Check your connection and try again."
    }
}

/** Thrown when an email fails the per-role domain restriction. */
class DomainRestrictionException(message: String) : Exception(message)

/** Thrown when the Google account email is not verified. */
class EmailNotVerifiedException(message: String) : Exception(message)

/** Thrown when driver sign-in is attempted before Step 3 ships. */
class DriverNotEnabledException(message: String) : Exception(message)

/** Thrown when the Firestore profile cannot be read or written. */
class ProfileUnavailableException(message: String) : Exception(message)
