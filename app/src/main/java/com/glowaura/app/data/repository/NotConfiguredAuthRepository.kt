package com.glowaura.app.data.repository

import com.glowaura.app.domain.model.AuthError
import com.glowaura.app.domain.model.SessionState
import com.glowaura.app.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Used only while Firebase has not been configured (no `google-services.json`).
 *
 * It reports a signed-out session and fails every operation with
 * [AuthError.FirebaseNotConfigured], so the UI can show an actionable message
 * instead of crashing on `FirebaseAuth.getInstance()`.
 */
class NotConfiguredAuthRepository : AuthRepository {

    private val _session = MutableStateFlow<SessionState>(SessionState.SignedOut)
    override val session: StateFlow<SessionState> = _session.asStateFlow()

    private fun failure() = Result.failure<Unit>(AuthError.FirebaseNotConfigured)

    override suspend fun restoreSession() = Unit

    override suspend fun login(email: String, password: String) = failure()

    override suspend fun register(fullName: String, email: String, password: String) = failure()

    override suspend fun sendPasswordResetEmail(email: String) = failure()

    override suspend fun signInWithGoogle(idToken: String) = failure()

    override suspend fun sendEmailVerification() = failure()

    override suspend fun signOut(): Result<Unit> = Result.success(Unit)

    override suspend fun refreshProfile() = failure()
}