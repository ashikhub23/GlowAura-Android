package com.glowaura.app.presentation.auth.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glowaura.app.domain.model.SessionState
import com.glowaura.app.domain.repository.AuthRepository
import com.glowaura.app.presentation.auth.login.toAuthErrorMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Owns the session for the whole app: exposes the current [SessionState] and
 * performs sign-out. The root NavHost observes [session] to switch between the
 * auth graph and the app graph, so logout needs no manual navigation call.
 */
class SessionViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    val session: StateFlow<SessionState> = authRepository.session
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = SessionState.Unknown
        )

    private val _signOutError = MutableStateFlow<String?>(null)
    val signOutError: StateFlow<String?> = _signOutError.asStateFlow()

    fun onSignOut() {
        if (_signOutError.value != null) return

        viewModelScope.launch {
            authRepository.signOut().onFailure { throwable ->
                _signOutError.value = throwable.toAuthErrorMessage()
            }
        }
    }

    fun onSignOutErrorShown() {
        _signOutError.value = null
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}