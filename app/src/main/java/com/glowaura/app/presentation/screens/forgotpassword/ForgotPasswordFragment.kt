package com.glowaura.app.presentation.screens.forgotpassword

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
import com.glowaura.app.databinding.FragmentForgotPasswordBinding
import com.glowaura.app.presentation.auth.AuthEvent
import com.glowaura.app.presentation.auth.AuthViewModelFactory
import com.glowaura.app.presentation.auth.forgotpassword.ForgotPasswordUiState
import com.glowaura.app.presentation.auth.forgotpassword.ForgotPasswordViewModel
import com.glowaura.app.presentation.common.hideKeyboard
import com.glowaura.app.presentation.common.showFieldError
import com.glowaura.app.presentation.common.showMessage
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

/**
 * Password reset request screen.
 *
 * On success the ViewModel flips [ForgotPasswordUiState.isEmailSent], which swaps
 * the explanatory copy for a confirmation banner and reveals the resend action.
 */
class ForgotPasswordFragment : Fragment(R.layout.fragment_forgot_password) {

    private var _binding: FragmentForgotPasswordBinding? = null
    private val binding get() = requireNotNull(_binding)

    private val viewModel: ForgotPasswordViewModel by viewModels { AuthViewModelFactory.Factory }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentForgotPasswordBinding.bind(view)

        bindInputs()
        observeState()
        observeEvents()
    }

    private fun bindInputs() = with(binding) {
        emailInput.doAfterTextChanged { viewModel.onEmailChange(it?.toString().orEmpty()) }

        sendButton.setOnClickListener {
            root.hideKeyboard()
            viewModel.onSendResetLinkClicked()
        }
        resendButton.setOnClickListener {
            root.hideKeyboard()
            viewModel.onSendResetLinkClicked()
        }
        backToLoginButton.setOnClickListener {
            root.hideKeyboard()
            viewModel.onBackToLoginClicked()
        }
        backButton.setOnClickListener {
            root.hideKeyboard()
            viewModel.onBackToLoginClicked()
        }

        emailInput.setOnEditorActionListener { _, _, _ ->
            root.hideKeyboard()
            viewModel.onSendResetLinkClicked()
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
                        AuthEvent.NavigateToLogin -> findNavController().popBackStack()

                        is AuthEvent.ShowMessage ->
                            Snackbar.make(binding.root, event.message, Snackbar.LENGTH_LONG).show()

                        AuthEvent.NavigateToHome -> Unit
                        AuthEvent.NavigateToRegister -> Unit
                        AuthEvent.NavigateToForgotPassword -> Unit
                    }
                }
            }
        }
    }

    private fun render(state: ForgotPasswordUiState) = with(binding) {
        if (emailInput.text?.toString() != state.email) emailInput.setText(state.email)

        emailError.showFieldError(state.emailError)

        // The success banner replaces the failure banner so the two never overlap.
        errorBanner.showMessage(state.formError)
        resendButton.isVisible = state.isEmailSent
        sendButton.text = getString(
            when {
                state.isSubmitting -> R.string.auth_sending
                state.isEmailSent -> R.string.auth_resend_link
                else -> R.string.auth_send_reset_link
            }
        )

        sendProgress.isVisible = state.isSubmitting
        sendButton.alpha = if (state.isSubmitting) DISABLED_ALPHA else 1f
        sendButton.isEnabled = state.isFormValid

        if (state.isEmailSent) {
            subtitle.text = getString(R.string.auth_reset_link_sent_body, state.email.trim())
        } else {
            subtitle.setText(R.string.auth_forgot_password_subtitle)
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val DISABLED_ALPHA = 0.4f
    }
}