package com.glowaura.app.presentation.screens.register

import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.glowaura.app.R
import com.glowaura.app.databinding.FragmentRegisterBinding
import com.glowaura.app.presentation.auth.AuthEvent
import com.glowaura.app.presentation.auth.AuthViewModelFactory
import com.glowaura.app.presentation.auth.register.PasswordStrength
import com.glowaura.app.presentation.auth.register.RegisterUiState
import com.glowaura.app.presentation.auth.register.RegisterViewModel
import com.glowaura.app.presentation.common.hideKeyboard
import com.glowaura.app.presentation.common.passwordToggle
import com.glowaura.app.presentation.common.PasswordInput
import com.glowaura.app.presentation.common.showFieldError
import com.glowaura.app.presentation.common.showMessage
import kotlinx.coroutines.launch

/**
 * Account creation screen.
 *
 * Also renders the password strength meter: the ViewModel produces a coarse
 * [PasswordStrength], and this maps it to a progress value, a label and a colour.
 * No scoring logic lives here.
 */
class RegisterFragment : Fragment(R.layout.fragment_register) {

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = requireNotNull(_binding)

    private val viewModel: RegisterViewModel by viewModels { AuthViewModelFactory.Factory }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentRegisterBinding.bind(view)

        bindInputs()
        observeState()
        observeEvents()
    }

    private fun bindInputs() = with(binding) {
        fullNameInput.doAfterTextChanged { viewModel.onFullNameChange(it?.toString().orEmpty()) }
        emailInput.doAfterTextChanged { viewModel.onEmailChange(it?.toString().orEmpty()) }
        passwordInput.doAfterTextChanged { viewModel.onPasswordChange(it?.toString().orEmpty()) }
        confirmPasswordInput.doAfterTextChanged {
            viewModel.onConfirmPasswordChange(it?.toString().orEmpty())
        }

        passwordLayout.passwordToggle(viewModel::onPasswordVisibilityToggle)
        confirmPasswordLayout.passwordToggle(viewModel::onConfirmPasswordVisibilityToggle)

        registerButton.setOnClickListener {
            root.hideKeyboard()
            viewModel.onRegisterClicked()
        }
        backToLoginButton.setOnClickListener {
            root.hideKeyboard()
            viewModel.onBackToLoginClicked()
        }
        backButton.setOnClickListener {
            root.hideKeyboard()
            viewModel.onBackToLoginClicked()
        }

        emailInput.setOnEditorActionListener { _, _, _ -> passwordInput.requestFocus(); true }
        passwordInput.setOnEditorActionListener { _, _, _ ->
            confirmPasswordInput.requestFocus(); true
        }
        confirmPasswordInput.setOnEditorActionListener { _, _, _ ->
            root.hideKeyboard()
            viewModel.onRegisterClicked()
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

                        // MainActivity owns the jump to the signed-in app.
                        AuthEvent.NavigateToHome -> Unit

                        is AuthEvent.ShowMessage -> Unit
                        AuthEvent.NavigateToRegister -> Unit
                        AuthEvent.NavigateToForgotPassword -> Unit
                    }
                }
            }
        }
    }

    private fun render(state: RegisterUiState) = with(binding) {
        if (fullNameInput.text?.toString() != state.fullName) fullNameInput.setText(state.fullName)
        if (emailInput.text?.toString() != state.email) emailInput.setText(state.email)
        if (passwordInput.text?.toString() != state.password) passwordInput.setText(state.password)
        if (confirmPasswordInput.text?.toString() != state.confirmPassword) {
            confirmPasswordInput.setText(state.confirmPassword)
        }

        fullNameError.showFieldError(state.fullNameError)
        emailError.showFieldError(state.emailError)
        passwordError.showFieldError(state.passwordError)
        confirmPasswordError.showFieldError(state.confirmPasswordError)
        errorBanner.showMessage(state.formError)

        PasswordInput.render(passwordLayout, state.passwordVisible)
        PasswordInput.render(confirmPasswordLayout, state.confirmPasswordVisible)

        renderPasswordStrength(state.passwordStrength)

        registerProgress.isVisible = state.isSubmitting
        registerButton.alpha = if (state.isSubmitting) DISABLED_ALPHA else 1f
        registerButton.isEnabled = state.isFormValid
    }

    private fun renderPasswordStrength(strength: PasswordStrength) = with(binding) {
        val visible = strength != PasswordStrength.Empty
        strengthBar.isVisible = visible
        strengthLabel.isVisible = visible
        if (!visible) return@with

        strengthBar.max = STRENGTH_SEGMENTS
        strengthBar.setProgressCompat(strength.segments, true)
        strengthBar.setIndicatorColor(ContextCompat.getColor(requireContext(), strength.colorRes))
        strengthLabel.setText(strength.labelRes)
        strengthLabel.setTextColor(ContextCompat.getColor(requireContext(), strength.colorRes))
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val DISABLED_ALPHA = 0.4f
    }
}

private val PasswordStrength.segments: Int
    get() = when (this) {
        PasswordStrength.Empty -> 0
        PasswordStrength.Weak -> 1
        PasswordStrength.Fair -> 2
        PasswordStrength.Strong -> STRENGTH_SEGMENTS
    }

private val PasswordStrength.colorRes: Int
    get() = when (this) {
        PasswordStrength.Empty -> R.color.light_outline
        PasswordStrength.Weak -> R.color.status_error
        PasswordStrength.Fair -> R.color.status_warning
        PasswordStrength.Strong -> R.color.status_success
    }

private val PasswordStrength.labelRes: Int
    get() = when (this) {
        PasswordStrength.Empty -> R.string.auth_strength_none
        PasswordStrength.Weak -> R.string.auth_strength_weak
        PasswordStrength.Fair -> R.string.auth_strength_fair
        PasswordStrength.Strong -> R.string.auth_strength_strong
    }

private const val STRENGTH_SEGMENTS = 3