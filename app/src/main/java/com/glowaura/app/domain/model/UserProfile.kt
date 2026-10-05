package com.glowaura.app.domain.model

/**
 * Domain representation of an authenticated GlowAura user.
 * Mirrors the document stored in the Firestore `users` collection.
 */
data class UserProfile(
    val uid: String,
    val email: String,
    val fullName: String,
    val photoUrl: String? = null,
    val phoneNumber: String? = null,
    val provider: AuthProvider,
    val emailVerified: Boolean = false,
    val createdAt: Long = 0L,
    val lastLoginAt: Long = 0L
)

/**
 * Which identity provider backed the account. Stored as the string value of [name].
 */
enum class AuthProvider {
    EMAIL,
    GOOGLE;

    companion object {
        fun fromStorage(value: String?): AuthProvider =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: EMAIL
    }
}

/**
 * Lightweight session state derived from FirebaseAuth, used by the UI to decide
 * whether to show the auth graph or the app graph.
 */
sealed interface SessionState {
    data object Unknown : SessionState
    data object SignedOut : SessionState
    data class SignedIn(val profile: UserProfile?) : SessionState
}