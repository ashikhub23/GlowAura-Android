package com.glowaura.app.presentation.auth.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glowaura.app.domain.model.AuthError
import com.glowaura.app.domain.repository.AuthRepository
import com.glowaura.app.presentation.auth.AuthEvent
import com.glowaura.app.presentation.auth.AuthValidators
import com.glowaura.app.presentation.auth.google.GoogleIdTokenProvider
import com.glowaura.app.presentation.auth.google.GoogleIdTokenResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val passwordVisible: Boolean = false,
    val isSubmitting: Boolean = false,
    val emailError: String? = null,
    val passwordError: String? = null,
    val formError: String? = null
) {
    /** Submits stay disabled until the form is locally valid. */
    val isFormValid: Boolean
        get() = !isSubmitting &&
            AuthValidators.validateEmail(email) == null &&
            AuthValidators.validatePassword(password) == null
}

class LoginViewModel(
    private val authRepository: AuthRepository,
    private val googleIdTokenProvider: GoogleIdTokenProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _events = Channel<AuthEvent>(Channel.BUFFERED)
    val events: Flow<AuthEvent> = _events.receiveAsFlow()

    fun onEmailChange(value: String) {
        // Clear the field error as soon as the user starts correcting it.
        _uiState.update { it.copy(email = value, emailError = null, formError = null) }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value, passwordError = null, formError = null) }
    }

    fun onPasswordVisibilityToggle() {
        _uiState.update { it.copy(passwordVisible = !it.passwordVisible) }
    }

    fun onForgotPasswordClicked() = emit(AuthEvent.NavigateToForgotPassword)

    fun onCreateAccountClicked() = emit(AuthEvent.NavigateToRegister)

    fun onLoginClicked() {
        val state = _uiState.value
        val emailError = AuthValidators.validateEmail(state.email)
        val passwordError = AuthValidators.validatePassword(state.password)

        if (emailError != null || passwordError != null) {
            _uiState.update {
                it.copy(emailError = emailError, passwordError = passwordError, formError = null)
            }
            return
        }

        if (state.isSubmitting) return

        launchAuthRequest {
            authRepository.login(state.email, state.password)
        }
    }

    /**
     * Runs the full Google flow: account chooser, then Firebase credential exchange.
     *
     * [GoogleIdTokenProvider] drives system UI so it must stay on the main thread,
     * which is exactly where `viewModelScope` runs.
     */
    fun onGoogleSignInClicked() {
        if (_uiState.value.isSubmitting) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, formError = null) }

            when (val tokenResult = googleIdTokenProvider.requestIdToken()) {
                is GoogleIdTokenResult.Cancelled ->
                    _uiState.update { it.copy(isSubmitting = false) }

                is GoogleIdTokenResult.Failure ->
                    _uiState.update {
                        it.copy(isSubmitting = false, formError = tokenResult.error.message)
                    }

                is GoogleIdTokenResult.Success ->
                    handleAuthResult(authRepository.signInWithGoogle(tokenResult.idToken))
            }
        }
    }

    /**
     * Shared loading/error/navigation handling so every entry point behaves
     * identically: one request in flight, typed error surfaced in [LoginUiState.formError],
     * success routed away from the screen.
     */
    private fun launchAuthRequest(block: suspend () -> Result<Unit>) {
        if (_uiState.value.isSubmitting) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, formError = null) }
            handleAuthResult(block())
        }
    }

    private suspend fun handleAuthResult(result: Result<Unit>) {
        result.fold(
            onSuccess = {
                _uiState.update { state -> state.copy(isSubmitting = false) }
                _events.send(AuthEvent.NavigateToHome)
            },
            onFailure = { throwable ->
                _uiState.update { state ->
                    state.copy(
                        isSubmitting = false,
                        formError = throwable.toAuthErrorMessage()
                    )
                }
            }
        )
    }

    private fun emit(event: AuthEvent) {
        viewModelScope.launch { _events.send(event) }
    }
}

/** Shared failure-message mapping for all auth ViewModels. */
internal fun Throwable.toAuthErrorMessage(): String =
    (this as? AuthError)?.message ?: "Something went wrong. Please try again."