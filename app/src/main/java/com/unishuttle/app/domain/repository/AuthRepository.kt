package com.unishuttle.app.domain.repository

import com.unishuttle.app.domain.model.AppUser
import com.unishuttle.app.domain.model.UserRole
import kotlinx.coroutines.flow.Flow

/**
 * Auth session state emitted by [AuthRepository.authState].
 *
 * [ProfileUnavailable] means a Firebase session exists but the Firestore profile
 * could not be loaded — the user must not be routed into the app in that state.
 */
sealed interface AuthState {
    data object SignedOut : AuthState
    data class SignedIn(val user: AppUser) : AuthState
    data class ProfileUnavailable(val message: String) : AuthState
}

interface AuthRepository {
    /** Emits on every auth change; loads the Firestore profile for active sessions. */
    val authState: Flow<AuthState>

    /** One-shot read of the current user (null if not signed in or profile missing). */
    suspend fun getCurrentUser(): AppUser?

    /**
     * Sign in with a Google ID token obtained from Credential Manager.
     * Applies the campus-domain and email-verified checks, then loads or creates
     * the Firestore profile. The stored document's role always wins over [role].
     */
    suspend fun signInWithGoogle(idToken: String, role: UserRole): Result<AppUser>

    /** Re-read the Firestore profile for the current session (retry path). */
    suspend fun reloadProfile(): Result<AppUser>

    /**
     * Sign out of Firebase Auth and clear the Credential Manager session.
     * Only auth state is cleared; caches (map tiles, database) are untouched.
     */
    suspend fun logout()
}
