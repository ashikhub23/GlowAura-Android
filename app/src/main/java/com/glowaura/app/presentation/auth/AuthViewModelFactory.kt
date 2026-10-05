package com.glowaura.app.presentation.auth

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.glowaura.app.GlowAuraApplication
import com.glowaura.app.presentation.auth.forgotpassword.ForgotPasswordViewModel
import com.glowaura.app.presentation.auth.login.LoginViewModel
import com.glowaura.app.presentation.auth.register.RegisterViewModel
import com.glowaura.app.presentation.auth.session.SessionViewModel

/**
 * Bridges [com.glowaura.app.di.AppContainer] to `viewModel()`.
 *
 * This is the manual equivalent of Hilt's `@HiltViewModel`: every ViewModel is
 * still constructor-injected, so switching to Hilt later only touches this file
 * and the `@HiltViewModel` annotations.
 */
object AuthViewModelFactory {

    val Factory: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            LoginViewModel(
                authRepository = app().container.authRepository,
                googleIdTokenProvider = app().container.googleIdTokenProvider
            )
        }
        initializer { RegisterViewModel(authRepository = app().container.authRepository) }
        initializer { ForgotPasswordViewModel(authRepository = app().container.authRepository) }
        initializer { SessionViewModel(authRepository = app().container.authRepository) }
    }

    private fun CreationExtras.app(): GlowAuraApplication =
        this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as GlowAuraApplication
}