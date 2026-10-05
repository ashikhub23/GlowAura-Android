package com.glowaura.app.presentation.screens.cart

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.glowaura.app.R
import com.glowaura.app.databinding.FragmentCartBinding

/** Cart placeholder. Back navigation is handled by the shared toolbar affordance. */
class CartFragment : Fragment(R.layout.fragment_cart) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        FragmentCartBinding.bind(view).toolbar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }
    }
}