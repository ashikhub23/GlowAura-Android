package com.glowaura.app.presentation.screens.login

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.glowaura.app.R
import com.glowaura.app.databinding.FragmentLoginBinding
import com.glowaura.app.presentation.auth.AuthEvent
import com.glowaura.app.presentation.auth.AuthViewModelFactory
import com.glowaura.app.presentation.auth.login.LoginUiState
import com.glowaura.app.presentation.auth.login.LoginViewModel
import com.glowaura.app.presentation.common.hideKeyboard
import com.glowaura.app.presentation.common.passwordToggle
import com.glowaura.app.presentation.common.PasswordInput
import com.glowaura.app.presentation.common.showFieldError
import com.glowaura.app.presentation.common.showMessage
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

/**
 * Sign-in screen.
 *
 * Owns no business rules: every input is forwarded to [LoginViewModel] and the
 * screen simply renders the resulting [LoginUiState]. Navigation between auth
 * screens uses nav actions; leaving for the signed-in app is handled centrally by
 * MainActivity when the session flips, so a successful sign-in never races a
 * manual navigation call.
 */
class LoginFragment : Fragment(R.layout.fragment_login) {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = requireNotNull(_binding)

    private val viewModel: LoginViewModel by viewModels { AuthViewModelFactory.Factory }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentLoginBinding.bind(view)

        bindInputs()
        observeState()
        observeEvents()
    }

    private fun bindInputs() = with(binding) {
        emailInput.doAfterTextChanged { viewModel.onEmailChange(it?.toString().orEmpty()) }
        passwordInput.doAfterTextChanged { viewModel.onPasswordChange(it?.toString().orEmpty()) }

        passwordLayout.passwordToggle(viewModel::onPasswordVisibilityToggle)

        signInButton.setOnClickListener {
            root.hideKeyboard()
            viewModel.onLoginClicked()
        }
        googleSignInButton.setOnClickListener {
            root.hideKeyboard()
            viewModel.onGoogleSignInClicked()
        }
        forgotPasswordButton.setOnClickListener {
            root.hideKeyboard()
            viewModel.onForgotPasswordClicked()
        }
        createAccountButton.setOnClickListener {
            root.hideKeyboard()
            viewModel.onCreateAccountClicked()
        }

        emailInput.setOnEditorActionListener { _, _, _ ->
            passwordInput.requestFocus()
            true
        }
        passwordInput.setOnEditorActionListener { _, _, _ ->
            root.hideKeyboard()
            viewModel.onLoginClicked()
            true
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    private fun observeEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        AuthEvent.NavigateToRegister ->
                            findNavController().navigate(R.id.action_login_to_register)

                        AuthEvent.NavigateToForgotPassword ->
                            findNavController().navigate(R.id.action_login_to_forgot_password)

                        // MainActivity reacts to the session change, so nothing to do.
                        AuthEvent.NavigateToHome -> Unit

                        is AuthEvent.ShowMessage ->
                            Snackbar.make(binding.root, event.message, Snackbar.LENGTH_LONG).show()

                        AuthEvent.NavigateToLogin -> Unit
                    }
                }
            }
        }
    }

    private fun render(state: LoginUiState) = with(binding) {
        // Guard the write so restoring state does not bounce back through the watchers.
        if (emailInput.text?.toString() != state.email) emailInput.setText(state.email)
        if (passwordInput.text?.toString() != state.password) passwordInput.setText(state.password)

        emailError.showFieldError(state.emailError)
        passwordError.showFieldError(state.passwordError)
        errorBanner.showMessage(state.formError)

        PasswordInput.render(passwordLayout, state.passwordVisible)

        signInProgress.isVisible = state.isSubmitting
        signInButton.alpha = if (state.isSubmitting) DISABLED_ALPHA else 1f
        signInButton.isEnabled = state.isFormValid
        googleSignInButton.isEnabled = !state.isSubmitting
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val DISABLED_ALPHA = 0.4f
    }
}