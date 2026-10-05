package com.glowaura.app.presentation.screens.profile

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.glowaura.app.R
import com.glowaura.app.databinding.FragmentProfileBinding

/** Profile placeholder. Back navigation is handled by the shared toolbar affordance. */
class ProfileFragment : Fragment(R.layout.fragment_profile) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        FragmentProfileBinding.bind(view).toolbar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }
    }
}