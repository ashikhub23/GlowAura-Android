package com.glowaura.app

import android.app.Application
import android.util.Log
import com.glowaura.app.di.AppContainer
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class GlowAuraApplication : Application() {

    lateinit var container: AppContainer
        private set

    private val applicationScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, throwable ->
            Log.e(TAG, "Unhandled error in application scope", throwable)
        }
    )

    override fun onCreate() {
        super.onCreate()

        container = AppContainer(this)

        // Restore the cached session as early as possible so the start destination
        // can be resolved without flashing the sign-in screen.
        applicationScope.launch {
            container.authRepository.restoreSession()
        }
    }

    private companion object {
        const val TAG = "GlowAuraApp"
    }
}