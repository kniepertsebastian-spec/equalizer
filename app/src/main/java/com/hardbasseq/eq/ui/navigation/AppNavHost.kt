package com.hardbasseq.eq.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
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
    NavHost(navController = navController, startDestination = ROUTE_HOME) {
        composable(ROUTE_HOME) {
            val viewModel: MainViewModel = hiltViewModel()
            MainScreen(
                viewModel = viewModel,
                spikeController = spikeController,
                onNavigateToDiagnostics = { navController.navigate(ROUTE_DIAGNOSTICS) }
            )
        }
        composable(ROUTE_DIAGNOSTICS) {
            val viewModel: MainViewModel = hiltViewModel()
            val state by viewModel.engineState.collectAsStateWithLifecycle()
            val capabilities by viewModel.capabilities.collectAsStateWithLifecycle()
            val route by viewModel.currentRoute.collectAsStateWithLifecycle()

            DiagnosticsScreen(
                state = state,
                capabilities = capabilities,
                route = route,
                onBackClicked = { navController.popBackStack() }
            )
        }
    }
}
