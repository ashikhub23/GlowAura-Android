package com.glowaura.app

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import com.glowaura.app.databinding.ActivityMainBinding
import com.glowaura.app.domain.model.SessionState
import com.glowaura.app.presentation.auth.AuthViewModelFactory
import com.glowaura.app.presentation.auth.session.SessionViewModel
import kotlinx.coroutines.launch

/**
 * Single-Activity host for the whole app.
 *
 * The Activity owns the cross-cutting session decision that used to live in the
 * Compose `GlowAuraNavHost`: auth screens and app screens live in two separate
 * nested graphs, and signing in or out swaps between them. Fragments only ever
 * navigate *within* their own graph, which keeps this the single place that knows
 * about authentication.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    private val sessionViewModel: SessionViewModel by lazy {
        ViewModelProvider(this, AuthViewModelFactory.Factory)[SessionViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // The host fragment fills the window; each screen pads itself for the
        // system bars so scrolling content can still reach the edges.
        ViewCompat.setOnApplyWindowInsetsListener(binding.navHostFragment) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        val host = supportFragmentManager
            .findFragmentById(R.id.navHostFragment) as NavHostFragment
        navController = host.navController

        observeSession()
    }

    /**
     * Routes to the correct root destination whenever the session resolves or changes.
     *
     * `SessionState.Unknown` is deliberately ignored so the splash screen stays up
     * until Firebase has actually told us whether a cached session exists.
     */
    private fun observeSession() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                sessionViewModel.session.collect(::routeForSession)
            }
        }
    }

    private fun routeForSession(session: SessionState) {
        val destination = navController.currentDestination ?: return
        val inAppGraph = destination.isInGraph(R.id.app_graph)
        val inAuthGraph = destination.isInGraph(R.id.auth_graph)

        when (session) {
            // Firebase is still restoring; leave the splash screen alone.
            SessionState.Unknown -> return

            is SessionState.SignedIn ->
                if (destination.id == R.id.splashFragment || inAuthGraph) {
                    navigateToRoot(R.id.homeFragment)
                }

            SessionState.SignedOut ->
                if (destination.id == R.id.splashFragment || inAppGraph) {
                    navigateToRoot(R.id.loginFragment)
                }
        }
    }

    /**
     * Replaces the whole back stack with [rootId].
     *
     * Popping `nav_graph`'s start destination inclusively guarantees that neither
     * signing out nor switching graphs leaves a stale screen on the back stack.
     */
    private fun navigateToRoot(rootId: Int) {
        if (navController.currentDestination?.id == rootId) return

        val options = NavOptions.Builder()
            .setPopUpTo(R.id.nav_graph, true)
            .setLaunchSingleTop(true)
            .build()

        navController.navigate(rootId, null, options)
    }
}

/** True when this destination sits inside the nested graph identified by [graphId]. */
private fun NavDestination.isInGraph(graphId: Int): Boolean =
    hierarchy.any { it.id == graphId }