package com.glowaura.app.domain.model

/**
 * Typed failure reasons produced by the auth layer.
 *
 * Firebase exceptions are mapped into this sealed hierarchy at the repository
 * boundary, so neither the ViewModels nor the UI ever handle
 * `FirebaseAuthException` or its error codes.
 *
 * Extends [Exception] so instances can be carried inside `Result.failure`.
 */
sealed class AuthError(
    override val message: String,
    cause: Throwable? = null
) : Exception(message, cause) {

    /** No network connectivity, or the request timed out. */
    data object Network : AuthError("No internet connection. Check your network and try again.")

    data object InvalidEmail : AuthError("That email address doesn't look right.")

    data object WeakPassword : AuthError("Password should be at least 6 characters.")

    data object EmailAlreadyInUse : AuthError(
        "An account already exists with this email. Try signing in."
    )

    data object UserNotFound : AuthError("We couldn't find an account with that email and password.")

    data object WrongPassword : AuthError("Incorrect email or password.")

    data object UserDisabled : AuthError("This account has been disabled. Contact support for help.")

    data object TooManyRequests : AuthError("Too many attempts. Please wait a moment and try again.")

    data object OperationNotAllowed : AuthError(
        "This sign-in method is disabled. Enable it in the Firebase console."
    )

    data object InvalidCredential : AuthError("Google sign-in failed. Please try again.")

    /** Firebase has not been configured (missing google-services.json). */
    data object FirebaseNotConfigured : AuthError(
        "Firebase is not configured yet. Add google-services.json to the app module."
    )

    data object FirestoreUnavailable : AuthError("We couldn't reach the database. Please try again.")

    data object PermissionDenied : AuthError(
        "Firestore security rules rejected this request."
    )

    /** Catch-all. Keeps the underlying message for diagnostics. */
    data class Unknown(override val message: String) :
        AuthError(message.ifBlank { "Something went wrong. Please try again." })
}