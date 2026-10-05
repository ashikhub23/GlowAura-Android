package com.glowaura.app.presentation.screens.home

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.glowaura.app.R
import com.glowaura.app.databinding.FragmentHomeBinding
import com.glowaura.app.domain.model.SessionState
import com.glowaura.app.presentation.auth.AuthViewModelFactory
import com.glowaura.app.presentation.auth.session.SessionViewModel
import com.glowaura.app.presentation.common.hideKeyboard
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

/**
 * Signed-in landing screen.
 *
 * Reads the profile straight from [SessionViewModel] instead of owning its own
 * state: the session ViewModel is activity-scoped, so sign-out and profile
 * refreshes propagate here for free.
 */
class HomeFragment : Fragment(R.layout.fragment_home) {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = requireNotNull(_binding)

    private val sessionViewModel: SessionViewModel by activityViewModels {
        AuthViewModelFactory.Factory
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentHomeBinding.bind(view)

        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_sign_out -> {
                    binding.root.hideKeyboard()
                    sessionViewModel.onSignOut()
                    true
                }
                // The toolbar entry is a shortcut to the same destination the body button uses.
                R.id.action_profile -> {
                    findNavController().navigate(R.id.action_home_to_profile)
                    true
                }
                else -> false
            }
        }

        binding.cartButton.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_cart)
        }
        binding.profileButton.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_profile)
        }

        observeState()
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sessionViewModel.session.collect(::render)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sessionViewModel.signOutError.collect { error ->
                    if (error != null) {
                        Snackbar.make(binding.root, error, Snackbar.LENGTH_LONG).show()
                        sessionViewModel.onSignOutErrorShown()
                    }
                }
            }
        }
    }

    private fun render(session: SessionState) = with(binding) {
        val profile = (session as? SessionState.SignedIn)?.profile
        val name = profile?.fullName?.takeIf(String::isNotBlank)

        greeting.text = name
            ?.let { getString(R.string.home_greeting, it) }
            ?: getString(R.string.home_greeting_generic)

        email.text = profile?.email.orEmpty()
        uid.text = profile?.uid?.let { getString(R.string.home_debug_uid, it) }.orEmpty()
        uid.isVisible = profile != null
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}