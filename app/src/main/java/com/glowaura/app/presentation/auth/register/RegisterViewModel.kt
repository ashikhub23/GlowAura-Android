package com.glowaura.app.presentation.auth.register

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

data class RegisterUiState(
    val fullName: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val passwordVisible: Boolean = false,
    val confirmPasswordVisible: Boolean = false,
    val isSubmitting: Boolean = false,
    val fullNameError: String? = null,
    val emailError: String? = null,
    val passwordError: String? = null,
    val confirmPasswordError: String? = null,
    val formError: String? = null,
    val passwordStrength: PasswordStrength = PasswordStrength.Empty
) {
    val isFormValid: Boolean
        get() = !isSubmitting &&
            AuthValidators.validateFullName(fullName) == null &&
            AuthValidators.validateEmail(email) == null &&
            AuthValidators.validatePassword(password) == null &&
            AuthValidators.validateConfirmPassword(password, confirmPassword) == null
}

/** Drives the strength meter under the password field. */
enum class PasswordStrength { Empty, Weak, Fair, Strong }

class RegisterViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    private val _events = Channel<AuthEvent>(Channel.BUFFERED)
    val events: Flow<AuthEvent> = _events.receiveAsFlow()

    fun onFullNameChange(value: String) {
        _uiState.update {
            it.copy(fullName = value, fullNameError = null, formError = null)
        }
    }

    fun onEmailChange(value: String) {
        _uiState.update { it.copy(email = value, emailError = null, formError = null) }
    }

    fun onPasswordChange(value: String) {
        _uiState.update {
            it.copy(
                password = value,
                passwordError = null,
                confirmPasswordError = null,
                formError = null,
                passwordStrength = value.toStrength()
            )
        }
    }

    fun onConfirmPasswordChange(value: String) {
        _uiState.update {
            it.copy(confirmPassword = value, confirmPasswordError = null, formError = null)
        }
    }

    fun onPasswordVisibilityToggle() {
        _uiState.update { it.copy(passwordVisible = !it.passwordVisible) }
    }

    fun onConfirmPasswordVisibilityToggle() {
        _uiState.update { it.copy(confirmPasswordVisible = !it.confirmPasswordVisible) }
    }

    fun onBackToLoginClicked() = emit(AuthEvent.NavigateToLogin)

    fun onRegisterClicked() {
        val state = _uiState.value

        val nameError = AuthValidators.validateFullName(state.fullName)
        val emailError = AuthValidators.validateEmail(state.email)
        val passwordError = AuthValidators.validatePassword(state.password)
        val confirmError =
            AuthValidators.validateConfirmPassword(state.password, state.confirmPassword)

        if (nameError != null || emailError != null || passwordError != null || confirmError != null) {
            _uiState.update {
                it.copy(
                    fullNameError = nameError,
                    emailError = emailError,
                    passwordError = passwordError,
                    confirmPasswordError = confirmError,
                    formError = null
                )
            }
            return
        }

        if (_uiState.value.isSubmitting) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, formError = null) }

            val result = authRepository.register(
                fullName = state.fullName,
                email = state.email,
                password = state.password
            )

            result.fold(
                onSuccess = {
                    _uiState.update { current -> current.copy(isSubmitting = false) }
                    _events.send(AuthEvent.NavigateToHome)
                },
                onFailure = { throwable ->
                    _uiState.update { current ->
                        current.copy(
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

/**
 * Score-based strength: length plus character-class variety, capped at Strong.
 * This is presentation-only guidance; the server enforces the real rule.
 */
private fun String.toStrength(): PasswordStrength {
    if (isEmpty()) return PasswordStrength.Empty

    var score = 0
    if (length >= 8) score++
    if (length >= 12) score++
    if (any(Char::isLowerCase) && any(Char::isUpperCase)) score++
    if (any(Char::isDigit)) score++
    if (any { !it.isLetterOrDigit() }) score++

    return when {
        score <= 2 -> PasswordStrength.Weak
        score == 3 -> PasswordStrength.Fair
        else -> PasswordStrength.Strong
    }
}