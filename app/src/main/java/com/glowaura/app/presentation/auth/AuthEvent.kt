package com.glowaura.app.presentation.auth

/**
 * One-shot effects that must not be replayed on configuration change.
 *
 * Kept in a `Channel` (rather than a StateFlow field) so a rotation cannot replay
 * a navigation or a snackbar the user has already seen.
 */
sealed interface AuthEvent {
    /** Navigate to the authenticated part of the app. */
    data object NavigateToHome : AuthEvent

    /** Navigate from Login to Register. */
    data object NavigateToRegister : AuthEvent

    /** Navigate from Register back to Login. */
    data object NavigateToLogin : AuthEvent

    /** Navigate from Login (or Reset) to the forgot-password screen. */
    data object NavigateToForgotPassword : AuthEvent

    /** Transient confirmation message, e.g. "Reset link sent to your inbox". */
    data class ShowMessage(val message: String) : AuthEvent
}