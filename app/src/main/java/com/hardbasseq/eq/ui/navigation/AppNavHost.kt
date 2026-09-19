package com.hardbasseq.eq.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.hardbasseq.eq.audio.spike.SessionAttachSpikeController
import com.hardbasseq.eq.ui.MainScreen
import com.hardbasseq.eq.ui.diagnostics.DiagnosticsScreen
import com.hardbasseq.eq.ui.main.MainViewModel

const val ROUTE_HOME = "home"
const val ROUTE_DIAGNOSTICS = "diagnostics"

@Composable
fun AppNavHost(spikeController: SessionAttachSpikeController) {
    val navController = rememberNavController()
    // Hoisted above NavHost so Home and Diagnostics share one MainViewModel
    // instance instead of each route creating its own via hiltViewModel():
    // MainViewModel owns AudioSessionRepository/AudioRouteRepository listening
    // in init{}/onCleared(), so a second instance would both reset processing
    // settings to their defaults and stop those (singleton) listeners the
    // moment the second instance's onCleared() ran.
    val viewModel: MainViewModel = hiltViewModel()
    NavHost(navController = navController, startDestination = ROUTE_HOME) {
        composable(ROUTE_HOME) {
            MainScreen(
                viewModel = viewModel,
                spikeController = spikeController,
                onNavigateToDiagnostics = { navController.navigate(ROUTE_DIAGNOSTICS) },
            )
        }
        composable(ROUTE_DIAGNOSTICS) {
            val state by viewModel.engineState.collectAsStateWithLifecycle()
            val capabilities by viewModel.capabilities.collectAsStateWithLifecycle()
            val route by viewModel.currentRoute.collectAsStateWithLifecycle()

            DiagnosticsScreen(
                state = state,
                capabilities = capabilities,
                route = route,
                onBackClicked = { navController.popBackStack() },
            )
        }
    }
}
