package com.unishuttle.app.presentation.viewmodel

import android.app.Application
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.unishuttle.app.BuildConfig
import com.unishuttle.app.domain.model.AppUser
import com.unishuttle.app.domain.model.UserRole
import com.unishuttle.app.domain.repository.AuthRepository
import com.unishuttle.app.data.repository.DomainRestrictionException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentUser: AppUser? = null,
    val isAuthResolved: Boolean = false,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    application: Application,
    private val authRepository: AuthRepository,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    // CredentialManager does not need injection — it's a system-level singleton
    private val credentialManager = CredentialManager.create(application)

    init {
        observeCurrentUser()
    }

    // ── Google Sign-In ───────────────────────────────────────────────────────

    /**
     * Called by [LoginScreen] when the user taps "Sign in with Google".
     *
     * [activityContext] must be an Activity context (not Application) because
     * Credential Manager shows a system bottom sheet anchored to the Activity window.
     * Pass it from the composable via [LocalContext.current].
     */
    fun signInWithGoogle(activityContext: android.content.Context, role: UserRole) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                // 1. Build the Credential Manager request
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false) // show all Google accounts on device
                    .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                    .setAutoSelectEnabled(false) // always show the account picker
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                // 2. Launch the account picker (suspends until the user picks or cancels)
                val credentialResponse: GetCredentialResponse =
                    credentialManager.getCredential(activityContext, request)

                // 3. Extract the Google ID token from the response
                val idToken = extractIdToken(credentialResponse)
                    ?: error("No Google ID token in credential response.")

                // 4. Hand off to the repository (Firebase auth + domain check + Firestore)
                authRepository.signInWithGoogle(idToken, role)
                    .onSuccess {
                        _uiState.update { it.copy(isLoading = false) }
                        // currentUser flow emits → UniShuttleNavGraph routes automatically
                    }
                    .onFailure { err ->
                        val message = if (err is DomainRestrictionException) {
                            err.message ?: "Invalid email domain."
                        } else {
                            err.message ?: "Sign-in failed. Please try again."
                        }
                        _uiState.update { it.copy(isLoading = false, error = message) }
                    }

            } catch (e: GetCredentialCancellationException) {
                // User dismissed the picker — not an error worth showing
                _uiState.update { it.copy(isLoading = false) }

            } catch (e: Exception) {
                Log.e(TAG, "Credential Manager error", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Google Sign-In failed. Please try again.",
                    )
                }
            }
        }
    }

    // ── Sign Out ─────────────────────────────────────────────────────────────

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            // Also clear the Credential Manager session so the account picker
            // doesn't silently auto-select next time
            runCatching {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            }
        }
    }

    // ── Utility ──────────────────────────────────────────────────────────────

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun observeCurrentUser() {
        viewModelScope.launch {
            authRepository.currentUser.collect { user ->
                _uiState.update { it.copy(currentUser = user, isAuthResolved = true) }
            }
        }
    }

    /**
     * Pulls the raw ID token string out of a [GetCredentialResponse].
     * Credential Manager can return different credential types; we only handle
     * [GoogleIdTokenCredential] here.
     */
    private fun extractIdToken(response: GetCredentialResponse): String? {
        val credential = response.credential
        return when {
            credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL -> {
                GoogleIdTokenCredential.createFrom(credential.data).idToken
            }
            else -> {
                Log.w(TAG, "Unexpected credential type: ${credential.type}")
                null
            }
        }
    }

    private companion object {
        const val TAG = "AuthViewModel"
    }
}
