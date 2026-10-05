package com.glowaura.app.data.remote

import com.glowaura.app.domain.model.AuthError
import com.glowaura.app.domain.model.AuthProvider
import com.glowaura.app.domain.model.UserProfile
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentHashMap

/**
 * Owns every Firestore interaction for user documents.
 *
 * Documents live in the `users` collection keyed by the FirebaseAuth uid, so the
 * same document is reachable without extra queries once the user is signed in.
 */
class UserProfileDataSource(private val firestore: FirebaseFirestore) {

    private val usersCollection = firestore.collection(COLLECTION_USERS)

    /**
     * Last known profile per uid.
     *
     * FirebaseAuth's auth-state listener runs on the main thread and cannot
     * suspend, so this cache lets it publish an immediate session value instead of
     * waiting on a Firestore round trip.
     */
    private val profileCache = ConcurrentHashMap<String, UserProfile>()

    /** Last profile successfully read or written for [uid], without touching the network. */
    fun cachedProfile(uid: String): UserProfile? = profileCache[uid]

    /** Returns the stored profile, or `null` when the user has no document yet. */
    suspend fun getProfile(uid: String): UserProfile? = try {
        usersCollection.document(uid).get().await().toUserProfile()?.also { profileCache[uid] = it }
    } catch (throwable: Throwable) {
        if (throwable is kotlinx.coroutines.CancellationException) throw throwable
        null
    }

    /**
     * Creates the profile document for a newly registered user.
     *
     * Uses [DocumentReference.set] with `merge = true`, so retrying after a partial
     * failure can never wipe fields that were already written.
     */
    suspend fun createProfile(user: FirebaseUser, fullName: String?): Result<Unit> = firestoreCall {
        val reference = usersCollection.document(user.uid)

        if (reference.get().await().exists()) {
            // A previous attempt already created it; only refresh login metadata.
            reference.update(mutableMapOf<String, Any>(FIELD_LAST_LOGIN_AT to now())).await()
            return@firestoreCall
        }

        reference.set(
            buildProfileDocument(user = user, fullName = fullName)
        ).await()

        // Prime the cache so the auth-state listener can render immediately.
        getProfile(user.uid)
        Unit
    }

    /** Stamps the most recent successful sign-in. Never fails the sign-in flow. */
    suspend fun touchLastLogin(uid: String) {
        try {
            val timestamp = now()
            usersCollection.document(uid)
                .update(mutableMapOf<String, Any>(FIELD_LAST_LOGIN_AT to timestamp))
                .await()
            profileCache.computeIfPresent(uid) { _, profile -> profile.copy(lastLoginAt = timestamp) }
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            // Metadata only: never fail a successful sign-in because of it.
        }
    }

    /** Applies a display-name change made from the profile screen. */
    suspend fun updateFullName(uid: String, fullName: String): Result<Unit> = firestoreCall {
        usersCollection.document(uid)
            .update(mutableMapOf<String, Any>(FIELD_FULL_NAME to fullName))
            .await()
        profileCache.computeIfPresent(uid) { _, profile -> profile.copy(fullName = fullName) }
        Unit
    }

    private fun buildProfileDocument(
        user: FirebaseUser,
        fullName: String?
    ): Map<String, Any?> = mapOf(
        FIELD_UID to user.uid,
        FIELD_EMAIL to (user.email ?: ""),
        FIELD_FULL_NAME to resolveDisplayName(user, fullName),
        FIELD_PHOTO_URL to user.photoUrl?.toString(),
        FIELD_PHONE_NUMBER to user.phoneNumber,
        FIELD_PROVIDER to resolveProvider(user),
        FIELD_EMAIL_VERIFIED to user.isEmailVerified,
        FIELD_CREATED_AT to now(),
        FIELD_LAST_LOGIN_AT to now()
    )

    private fun resolveDisplayName(user: FirebaseUser, fullName: String?): String =
        fullName?.trim()?.takeIf(String::isNotEmpty)
            ?: user.displayName?.trim()?.takeIf(String::isNotEmpty)
            ?: user.email?.substringBefore('@').orEmpty()

    private fun resolveProvider(user: FirebaseUser): AuthProvider =
        if (user.providerId == GOOGLE_PROVIDER_ID) AuthProvider.GOOGLE else AuthProvider.EMAIL

    private fun DocumentSnapshot.toUserProfile(): UserProfile? {
        val uid = get(FIELD_UID) as? String ?: return null
        return UserProfile(
            uid = uid,
            email = get(FIELD_EMAIL) as? String ?: "",
            fullName = get(FIELD_FULL_NAME) as? String ?: "",
            photoUrl = get(FIELD_PHOTO_URL) as? String,
            phoneNumber = get(FIELD_PHONE_NUMBER) as? String,
            provider = AuthProvider.fromStorage(get(FIELD_PROVIDER) as? String),
            emailVerified = get(FIELD_EMAIL_VERIFIED) as? Boolean ?: false,
            createdAt = (get(FIELD_CREATED_AT) as? Number)?.toLong() ?: 0L,
            lastLoginAt = (get(FIELD_LAST_LOGIN_AT) as? Number)?.toLong() ?: 0L
        )
    }

    /**
     * Runs a Firestore call and folds any failure into a typed [AuthError] instead of
     * leaking `FirebaseFirestoreException` into the layers above.
     */
    private suspend fun <T> firestoreCall(operation: suspend () -> T): Result<T> = try {
        Result.success(operation())
    } catch (cancellation: kotlinx.coroutines.CancellationException) {
        throw cancellation
    } catch (throwable: Throwable) {
        Result.failure(throwable.toAuthError())
    }

    private fun Throwable.toAuthError(): AuthError = when (this) {
        is FirebaseFirestoreException -> when (code) {
            FirebaseFirestoreException.Code.UNAVAILABLE,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> AuthError.FirestoreUnavailable

            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            FirebaseFirestoreException.Code.UNAUTHENTICATED -> AuthError.PermissionDenied

            else -> AuthError.Unknown(message ?: "Database error.")
        }

        else -> AuthError.Network
    }

    private companion object {
        const val COLLECTION_USERS = "users"

        const val FIELD_UID = "uid"
        const val FIELD_EMAIL = "email"
        const val FIELD_FULL_NAME = "fullName"
        const val FIELD_PHOTO_URL = "photoUrl"
        const val FIELD_PHONE_NUMBER = "phoneNumber"
        const val FIELD_PROVIDER = "provider"
        const val FIELD_EMAIL_VERIFIED = "emailVerified"
        const val FIELD_CREATED_AT = "createdAt"
        const val FIELD_LAST_LOGIN_AT = "lastLoginAt"

        const val GOOGLE_PROVIDER_ID = "google.com"

        fun now(): Long = System.currentTimeMillis()
    }
}