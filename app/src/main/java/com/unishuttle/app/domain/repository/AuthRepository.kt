package com.unishuttle.app.domain.repository

import com.unishuttle.app.domain.model.AppUser
import com.unishuttle.app.domain.model.UserRole
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    /** Emits the currently signed-in user, or null when signed out. */
    val currentUser: Flow<AppUser?>

    /** One-shot read of the current user (returns null if not signed in). */
    suspend fun getCurrentUser(): AppUser?

    /**
     * Sign in with a Google ID token obtained from Credential Manager.
     * Applies domain restrictions for [UserRole.STUDENT] accounts.
     * Writes / merges the user document in Firestore on success.
     */
    suspend fun signInWithGoogle(
        idToken: String,
        role: UserRole,
    ): Result<AppUser>

    /**
     * Sign out from Firebase Auth.
     * Credential Manager session is cleared by the caller (Activity context needed).
     */
    suspend fun logout()
}
