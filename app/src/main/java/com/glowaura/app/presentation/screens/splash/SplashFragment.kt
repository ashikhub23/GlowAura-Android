package com.glowaura.app.presentation.screens.splash

import androidx.fragment.app.Fragment
import com.glowaura.app.R

/**
 * Brand screen shown while Firebase restores any cached session.
 *
 * Intentionally holds no logic: [com.glowaura.app.MainActivity] owns the session
 * flow and pops this destination as soon as the answer is known.
 */
class SplashFragment : Fragment(R.layout.fragment_splash)