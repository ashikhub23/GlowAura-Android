package com.glowaura.app.presentation.auth.forgotpassword

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glowaura.app.domain.repository.AuthRepository
import com.glowaura.app.presentation.auth.AuthEvent
import com.glowaura.app.presentation.auth.AuthValidators
import com.glowaura.app.presentation.auth.login.toAuthErrorMessage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ForgotPasswordUiState(
    val email: String = "",
    val isSubmitting: Boolean = false,
    val emailError: String? = null,
    val formError: String? = null,
    val isEmailSent: Boolean = false
) {
    val isFormValid: Boolean
        get() = !isSubmitting && AuthValidators.validateEmail(email) == null
}

class ForgotPasswordViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ForgotPasswordUiState())
    val uiState: StateFlow<ForgotPasswordUiState> = _uiState.asStateFlow()

    private val _events = Channel<AuthEvent>(Channel.BUFFERED)
    val events: Flow<AuthEvent> = _events.receiveAsFlow()

    fun onEmailChange(value: String) {
        _uiState.update {
            it.copy(email = value, emailError = null, formError = null, isEmailSent = false)
        }
    }

    fun onBackToLoginClicked() = emit(AuthEvent.NavigateToLogin)

    fun onSendResetLinkClicked() {
        val state = _uiState.value
        val emailError = AuthValidators.validateEmail(state.email)

        if (emailError != null) {
            _uiState.update { it.copy(emailError = emailError, formError = null) }
            return
        }

        if (_uiState.value.isSubmitting) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, formError = null) }

            authRepository.sendPasswordResetEmail(state.email).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(isSubmitting = false, isEmailSent = true, formError = null)
                    }
                    _events.send(
                        AuthEvent.ShowMessage("Reset link sent to ${state.email.trim()}")
                    )
                },
                onFailure = { throwable ->
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            formError = throwable.toAuthErrorMessage()
                        )
                    }
                }
            )
        }
    }

    private fun emit(event: AuthEvent) {
        viewModelScope.launch { _events.send(event) }
    }
}