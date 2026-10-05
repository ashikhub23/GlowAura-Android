package com.glowaura.app.presentation.auth

/**
 * Pure form-validation rules shared by every auth screen.
 *
 * Kept free of Android and Firebase types so it is trivially unit-testable.
 */
object AuthValidators {

    const val MIN_PASSWORD_LENGTH = 8
    const val MAX_PASSWORD_LENGTH = 128
    private const val MIN_NAME_LENGTH = 3
    private const val MAX_NAME_LENGTH = 50

    /** Pragmatic email shape check; Firebase remains the source of truth. */
    private val EMAIL_REGEX = Regex(
        "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    )

    fun validateEmail(email: String): String? = when {
        email.isBlank() -> "Email is required"
        !EMAIL_REGEX.matches(email.trim()) -> "Enter a valid email address"
        email.trim().length > MAX_EMAIL_LENGTH -> "Email is too long"
        else -> null
    }

    fun validatePassword(password: String): String? = when {
        password.isEmpty() -> "Password is required"
        password.length < MIN_PASSWORD_LENGTH ->
            "Password must be at least $MIN_PASSWORD_LENGTH characters"
        password.length > MAX_PASSWORD_LENGTH -> "Password is too long"
        !password.any(Char::isLetter) -> "Password must contain a letter"
        !password.any(Char::isDigit) -> "Password must contain a number"
        else -> null
    }

    fun validateConfirmPassword(password: String, confirmPassword: String): String? = when {
        confirmPassword.isEmpty() -> "Confirm your password"
        confirmPassword != password -> "Passwords do not match"
        else -> null
    }

    fun validateFullName(fullName: String): String? = when {
        fullName.isBlank() -> "Name is required"
        fullName.trim().length < MIN_NAME_LENGTH ->
            "Name must be at least $MIN_NAME_LENGTH characters"
        fullName.trim().length > MAX_NAME_LENGTH -> "Name is too long"
        !fullName.trim().all { it.isLetter() || it == ' ' || it == '-' || it == '\'' } ->
            "Name can only contain letters, spaces, hyphens and apostrophes"
        else -> null
    }

    private const val MAX_EMAIL_LENGTH = 254
}