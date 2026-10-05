package com.glowaura.app.di

import android.content.Context
import android.util.Log
import com.glowaura.app.data.remote.UserProfileDataSource
import com.glowaura.app.data.repository.FirebaseAuthRepository
import com.glowaura.app.data.repository.NotConfiguredAuthRepository
import com.glowaura.app.domain.repository.AuthRepository
import com.glowaura.app.presentation.auth.google.GoogleIdTokenProvider
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Manual dependency container (service locator).
 *
 * Hilt/KAPT is intentionally not used: AGP 9's built-in Kotlin support does not
 * support `kapt`, and the Hilt Gradle plugin has no AGP 9 release yet. Everything
 * is constructor-injected, so swapping this for Hilt later is a mechanical change.
 */
class AppContainer(private val context: Context) {

    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    /** False when no valid `google-services.json` is present in the app module. */
    private val isFirebaseConfigured: Boolean by lazy {
        runCatching { FirebaseApp.getApps(context).isNotEmpty() }
            .onFailure { Log.w(TAG, "Firebase lookup failed", it) }
            .getOrDefault(false)
    }

    val authRepository: AuthRepository by lazy {
        if (!isFirebaseConfigured) {
            Log.w(TAG, "Firebase not configured - auth actions will report a configuration error.")
            NotConfiguredAuthRepository()
        } else {
            FirebaseAuthRepository(
                firebaseAuth = FirebaseAuth.getInstance(),
                profileDataSource = UserProfileDataSource(FirebaseFirestore.getInstance()),
                ioDispatcher = ioDispatcher
            )
        }
    }

    /**
     * Uses the application context on purpose: Credential Manager outlives the
     * Activity that started the flow.
     */
    val googleIdTokenProvider: GoogleIdTokenProvider by lazy {
        GoogleIdTokenProvider(context.applicationContext)
    }

    private companion object {
        const val TAG = "AppContainer"
    }
}