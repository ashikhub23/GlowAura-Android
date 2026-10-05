package com.glowaura.app.data.repository

import android.util.Log
import com.glowaura.app.data.remote.UserProfileDataSource
import com.glowaura.app.domain.model.AuthError
import com.glowaura.app.domain.model.AuthProvider
import com.glowaura.app.domain.model.SessionState
import com.glowaura.app.domain.model.UserProfile
import com.glowaura.app.domain.repository.AuthRepository
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Firebase-backed implementation of [AuthRepository].
 *
 * Every public method:
 *  1. hops to [ioDispatcher] because the Firebase SDK exposes blocking Tasks,
 *  2. maps the SDK exception hierarchy onto [AuthError],
 *  3. keeps [session] in sync via an auth-state listener.
 *
 * No Firebase type ever escapes this class.
 */
class FirebaseAuthRepository(
    private val firebaseAuth: FirebaseAuth,
    private val profileDataSource: UserProfileDataSource,
    private val ioDispatcher: CoroutineDispatcher
) : AuthRepository {

    private val _session = MutableStateFlow<SessionState>(SessionState.Unknown)
    override val session: StateFlow<SessionState> = _session.asStateFlow()

    init {
        // FirebaseAuth caches the session across process death, so mirror its
        // callback stream into our own StateFlow instead of re-querying on every read.
        // Note: AuthStateListener hands back the FirebaseAuth instance, so the current
        // user has to be read from it rather than taken from the callback argument.
        firebaseAuth.addAuthStateListener { auth ->
            val user: FirebaseUser? = auth.currentUser
            _session.value = when (user) {
                null -> SessionState.SignedOut
                else -> SessionState.SignedIn(
                    profileDataSource.cachedProfile(user.uid) ?: profileFromAuth(user)
                )
            }
        }
    }

    override suspend fun restoreSession() = withContext(ioDispatcher) {
        _session.value = firebaseAuth.currentUser
            ?.let { SessionState.SignedIn(profileDataSource.cachedProfile(it.uid) ?: profileFromAuth(it)) }
            ?: SessionState.SignedOut
    }

    override suspend fun login(email: String, password: String): Result<Unit> =
        withContext(ioDispatcher) {
            firebaseCall {
                firebaseAuth.signInWithEmailAndPassword(email.trim(), password).await()
            }.map { }.onSuccess { syncProfileForCurrentUser() }
        }

    override suspend fun register(
        fullName: String,
        email: String,
        password: String
    ): Result<Unit> = withContext(ioDispatcher) {
        firebaseCall {
            firebaseAuth.createUserWithEmailAndPassword(email.trim(), password).await()
        }.map { }.onSuccess {
            val user = firebaseAuth.currentUser ?: return@onSuccess

            // Rename the account immediately so the FirebaseAuth displayName and the
            // Firestore document can never diverge.
            val displayName = fullName.trim()
            if (displayName.isNotEmpty()) {
                val request = UserProfileChangeRequest.Builder()
                    .setDisplayName(displayName)
                    .build()
                runCatching { user.updateProfile(request).await() }
            }
            profileDataSource.createProfile(user, fullName)
            // Verification nudges the user without blocking sign-in.
            runCatching { user.sendEmailVerification().await() }
        }
    }

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> =
        withContext(ioDispatcher) {
            firebaseCall { firebaseAuth.sendPasswordResetEmail(email.trim()).await() }.map { }
        }

    override suspend fun signInWithGoogle(idToken: String): Result<Unit> =
        withContext(ioDispatcher) {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            firebaseCall {
                firebaseAuth.signInWithCredential(credential).await()
            }.map { }.onSuccess { syncProfileForCurrentUser() }
        }

    override suspend fun sendEmailVerification(): Result<Unit> = withContext(ioDispatcher) {
        val user = firebaseAuth.currentUser
            ?: return@withContext Result.failure(AuthError.UserNotFound)
        firebaseCall { user.sendEmailVerification().await() }.map { }
    }

    override suspend fun signOut(): Result<Unit> = withContext(ioDispatcher) {
        firebaseCall { firebaseAuth.signOut() }.map { }
    }

    override suspend fun refreshProfile(): Result<Unit> = withContext(ioDispatcher) {
        val user = firebaseAuth.currentUser
            ?: return@withContext Result.failure(AuthError.UserNotFound)

        val stored = profileDataSource.getProfile(user.uid)
        val outcome = if (stored == null) {
            profileDataSource.createProfile(user, user.displayName)
        } else {
            Result.success(Unit)
        }

        outcome.onSuccess {
            _session.value = SessionState.SignedIn(
                profileDataSource.cachedProfile(user.uid) ?: profileFromAuth(user)
            )
        }
    }

    /**
     * Guarantees a Firestore document exists for the signed-in user without ever
     * failing the sign-in that triggered it: a Firestore outage must not block
     * access to the app.
     */
    private suspend fun syncProfileForCurrentUser() {
        val user = firebaseAuth.currentUser ?: return
        if (profileDataSource.cachedProfile(user.uid) != null) {
            profileDataSource.touchLastLogin(user.uid)
            return
        }
        if (profileDataSource.getProfile(user.uid) != null) {
            profileDataSource.touchLastLogin(user.uid)
            return
        }
        runCatching { profileDataSource.createProfile(user, user.displayName) }
            .onFailure { Log.w(TAG, "Could not create profile for ${user.uid}", it) }
    }

    /** Minimal profile derived straight from FirebaseAuth, used before Firestore responds. */
    private fun profileFromAuth(user: FirebaseUser) = UserProfile(
        uid = user.uid,
        email = user.email.orEmpty(),
        fullName = user.displayName.orEmpty(),
        photoUrl = user.photoUrl?.toString(),
        phoneNumber = user.phoneNumber,
        provider = if (user.providerId == GOOGLE_PROVIDER_ID) {
            AuthProvider.GOOGLE
        } else {
            AuthProvider.EMAIL
        },
        emailVerified = user.isEmailVerified
    )

    /**
     * Runs a Firebase Task-based call, translating the SDK exception hierarchy into
     * [AuthError] so callers only ever see typed domain failures.
     */
    private suspend fun <T> firebaseCall(operation: suspend () -> T): Result<T> = try {
        Result.success(operation())
    } catch (cancellation: kotlinx.coroutines.CancellationException) {
        throw cancellation
    } catch (throwable: Throwable) {
        Result.failure(throwable.toAuthError())
    }

    private fun Throwable.toAuthError(): AuthError = when (this) {
        is FirebaseAuthInvalidUserException -> when (errorCode) {
            ERROR_USER_NOT_FOUND -> AuthError.UserNotFound
            ERROR_WRONG_PASSWORD -> AuthError.WrongPassword
            ERROR_USER_DISABLED -> AuthError.UserDisabled
            ERROR_INVALID_EMAIL -> AuthError.InvalidEmail
            else -> AuthError.WrongPassword
        }

        is FirebaseAuthInvalidCredentialsException -> AuthError.WrongPassword

        is FirebaseAuthUserCollisionException -> AuthError.EmailAlreadyInUse

        is FirebaseNetworkException -> AuthError.Network

        is FirebaseFirestoreException -> when (code) {
            FirebaseFirestoreException.Code.UNAVAILABLE,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> AuthError.FirestoreUnavailable

            else -> AuthError.Unknown(message ?: "Firebase error.")
        }

        else -> {
            // Firebase surfaces some failures only as message text, so fall back to
            // matching the well-known error codes.
            val text = message.orEmpty()
            when {
                text.contains(ERROR_INVALID_EMAIL, ignoreCase = true) -> AuthError.InvalidEmail
                text.contains(ERROR_WEAK_PASSWORD, ignoreCase = true) -> AuthError.WeakPassword
                text.contains(ERROR_USER_NOT_FOUND, ignoreCase = true) -> AuthError.UserNotFound
                text.contains(ERROR_WRONG_PASSWORD, ignoreCase = true) -> AuthError.WrongPassword
                text.contains(ERROR_USER_DISABLED, ignoreCase = true) -> AuthError.UserDisabled
                text.contains(ERROR_OPERATION_NOT_ALLOWED, ignoreCase = true) -> AuthError.OperationNotAllowed
                text.contains(ERROR_TOO_MANY_REQUESTS, ignoreCase = true) -> AuthError.TooManyRequests
                text.contains(ERROR_INVALID_CREDENTIAL, ignoreCase = true) -> AuthError.InvalidCredential
                text.contains(ERROR_INVALID_LOGIN_CREDENTIALS, ignoreCase = true) -> AuthError.WrongPassword
                text.contains("network", ignoreCase = true) -> AuthError.Network
                else -> AuthError.Unknown(text)
            }
        }
    }

    private companion object {
        const val TAG = "FirebaseAuthRepo"
        const val GOOGLE_PROVIDER_ID = "google.com"

        const val ERROR_INVALID_EMAIL = "ERROR_INVALID_EMAIL"
        const val ERROR_WEAK_PASSWORD = "ERROR_WEAK_PASSWORD"
        const val ERROR_USER_NOT_FOUND = "ERROR_USER_NOT_FOUND"
        const val ERROR_WRONG_PASSWORD = "ERROR_WRONG_PASSWORD"
        const val ERROR_USER_DISABLED = "ERROR_USER_DISABLED"
        const val ERROR_OPERATION_NOT_ALLOWED = "ERROR_OPERATION_NOT_ALLOWED"
        const val ERROR_TOO_MANY_REQUESTS = "ERROR_TOO_MANY_REQUESTS"
        const val ERROR_INVALID_CREDENTIAL = "ERROR_INVALID_CREDENTIAL"
        const val ERROR_INVALID_LOGIN_CREDENTIALS = "ERROR_INVALID_LOGIN_CREDENTIALS"
    }
}