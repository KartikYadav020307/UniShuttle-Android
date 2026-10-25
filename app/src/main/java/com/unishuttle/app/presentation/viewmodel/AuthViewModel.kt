package com.unishuttle.app.presentation.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
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
import com.unishuttle.app.domain.model.UserRole
import com.unishuttle.app.domain.repository.AuthRepository
import com.unishuttle.app.domain.repository.AuthState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AuthUiState {
    /** Firebase session is being resolved on cold launch. */
    data object Loading : AuthUiState

    /** No session — the login screen is shown. */
    data class SignedOut(
        val error: String? = null,
        val isBusy: Boolean = false,
        val showRetry: Boolean = false,
    ) : AuthUiState

    /** Session active. The role always comes from the Firestore profile. */
    data class SignedIn(val role: UserRole, val user: com.unishuttle.app.domain.model.AppUser? = null) : AuthUiState
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    application: Application,
    private val authRepository: AuthRepository,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Loading)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _selectedRole = MutableStateFlow(UserRole.STUDENT)
    val selectedRole: StateFlow<UserRole> = _selectedRole.asStateFlow()

    /** True when GOOGLE_WEB_CLIENT_ID is absent from local.properties. */
    val isConfigMissing: Boolean = BuildConfig.GOOGLE_WEB_CLIENT_ID.isBlank()

    // The account picker (getCredential) needs an Activity context, so Credential
    // Manager stays here; clearCredentialState lives in the repository.
    private val credentialManager = CredentialManager.create(application)

    // Error to attach to the next SignedOut emission (repo-initiated sign-outs).
    private var pendingError: String? = null

    init {
        observeAuthState()
    }

    // ── Role selection ───────────────────────────────────────────────────────

    fun selectRole(role: UserRole) {
        _selectedRole.value = role
        pendingError = null
        _uiState.update { state ->
            if (state is AuthUiState.SignedOut) state.copy(error = null) else state
        }
    }

    // ── Google Sign-In ───────────────────────────────────────────────────────

    /**
     * Called by [com.unishuttle.app.presentation.ui.screen.auth.LoginScreen] when
     * the user taps "Sign in with Google". [activityContext] must be an Activity
     * context because Credential Manager anchors its sheet to the Activity window.
     */
    fun signInWithGoogle(activityContext: Context, role: UserRole) {
        if (isConfigMissing) {
            setError(MISSING_CONFIG_MESSAGE)
            return
        }
        viewModelScope.launch {
            pendingError = null
            setBusy(true)
            try {
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                    .setAutoSelectEnabled(false)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val credentialResponse: GetCredentialResponse =
                    credentialManager.getCredential(activityContext, request)

                val idToken = extractIdToken(credentialResponse)
                    ?: error("No Google ID token in credential response.")

                authRepository.signInWithGoogle(idToken, role)
                    .onSuccess {
                        setBusy(false)
                        // authState emits SignedIn → the nav guard routes by role.
                    }
                    .onFailure { err ->
                        setBusy(false)
                        showError(err.message ?: GENERIC_SIGN_IN_ERROR)
                    }
            } catch (e: GetCredentialCancellationException) {
                // User dismissed the picker — return to login with no error.
                setBusy(false)
            } catch (e: Exception) {
                Log.e(TAG, "Credential Manager error", e)
                setBusy(false)
                showError(e.message ?: GENERIC_SIGN_IN_ERROR)
            }
        }
    }

    // ── Profile retry ────────────────────────────────────────────────────────

    /** Re-reads the Firestore profile after a ProfileUnavailable state. */
    fun retryLoadProfile() {
        viewModelScope.launch {
            authRepository.reloadProfile()
                .onSuccess { user ->
                    _uiState.value = AuthUiState.SignedIn(user.role, user)
                }
                .onFailure { err ->
                    _uiState.update { state ->
                        if (state is AuthUiState.SignedOut) {
                            state.copy(error = err.message ?: GENERIC_SIGN_IN_ERROR, showRetry = true)
                        } else {
                            state
                        }
                    }
                }
        }
    }

    // ── Sign Out ─────────────────────────────────────────────────────────────

    /** Signs out; the authState flow then emits SignedOut and the guard redirects. */
    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
        }
    }

    /**
     * Signs out and pins an error to the login screen. Used by the guard for
     * accounts whose role has no destination in this step (e.g. ADMIN).
     */
    fun signOutWithError(message: String) {
        pendingError = message
        viewModelScope.launch {
            authRepository.logout()
        }
    }

    // ── Errors ───────────────────────────────────────────────────────────────

    fun setError(message: String?) {
        showError(message)
    }

    private fun showError(message: String?) {
        // Keep it for the SignedOut emission that a repo-initiated sign-out
        // triggers, and apply it immediately for failures that change nothing.
        pendingError = message
        _uiState.update { state ->
            if (state is AuthUiState.SignedOut) state.copy(error = message) else state
        }
    }

    private fun setBusy(busy: Boolean) {
        _uiState.update { state ->
            if (state is AuthUiState.SignedOut) state.copy(isBusy = busy) else state
        }
    }

    // ── Auth state observation ───────────────────────────────────────────────

    private fun observeAuthState() {
        viewModelScope.launch {
            authRepository.authState.collect { state ->
                _uiState.value = when (state) {
                    AuthState.SignedOut -> AuthUiState.SignedOut(error = pendingError)
                    is AuthState.SignedIn -> AuthUiState.SignedIn(state.user.role, state.user)
                    is AuthState.ProfileUnavailable -> AuthUiState.SignedOut(
                        error = state.message,
                        showRetry = true,
                    )
                }
                pendingError = null
            }
        }
    }

    /**
     * Pulls the raw ID token string out of a [GetCredentialResponse].
     * We only handle [GoogleIdTokenCredential] here.
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
        const val MISSING_CONFIG_MESSAGE = "Missing GOOGLE_WEB_CLIENT_ID in local.properties"
        const val GENERIC_SIGN_IN_ERROR = "Sign-in failed. Please try again."
    }
}
