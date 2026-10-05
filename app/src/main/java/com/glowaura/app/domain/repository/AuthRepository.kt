package com.glowaura.app.domain.repository

import com.glowaura.app.domain.model.SessionState
import kotlinx.coroutines.flow.StateFlow

/**
 * Contract for every authentication capability the app needs.
 *
 * Implemented by [com.glowaura.app.data.repository.FirebaseAuthRepository].
 * All methods are `suspend` and never throw: they return [Result] with a typed
 * [AuthError] so callers handle failures explicitly.
 */
interface AuthRepository {

    /** Emits the current session. Backs the top level auth/app graph switch. */
    val session: StateFlow<SessionState>

    /** Restores the persisted session. Call once, from Application startup. */
    suspend fun restoreSession()

    suspend fun login(email: String, password: String): Result<Unit>

    suspend fun register(
        fullName: String,
        email: String,
        password: String
    ): Result<Unit>

    /** Sends Firebase's password reset email. Does not reveal whether the account exists. */
    suspend fun sendPasswordResetEmail(email: String): Result<Unit>

    /** Completes Google sign-in using the OIDC id token from Credential Manager. */
    suspend fun signInWithGoogle(idToken: String): Result<Unit>

    suspend fun sendEmailVerification(): Result<Unit>

    suspend fun signOut(): Result<Unit>

    /** Fetches the Firestore profile, creating it from the FirebaseAuth record when absent. */
    suspend fun refreshProfile(): Result<Unit>
}