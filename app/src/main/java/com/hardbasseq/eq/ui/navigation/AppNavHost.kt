package com.hardbasseq.eq.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.hardbasseq.eq.audio.spike.SessionAttachSpikeController
import com.hardbasseq.eq.integration.PlayerSource
import com.hardbasseq.eq.ui.MainScreen
import com.hardbasseq.eq.ui.diagnostics.DiagnosticsScreen
import com.hardbasseq.eq.ui.main.MainViewModel
import com.hardbasseq.eq.ui.player.PlayerScreen
import com.hardbasseq.eq.ui.player.PlayerViewModel

const val ROUTE_HOME = "home"
const val ROUTE_DIAGNOSTICS = "diagnostics"
const val ROUTE_PLAYER = "player"

@Composable
fun AppNavHost(
    spikeController: SessionAttachSpikeController,
    playerViewModel: PlayerViewModel,
) {
    val navController = rememberNavController()
    // Hoisted above NavHost so Home and Diagnostics share one MainViewModel
    // instance instead of each route creating its own via hiltViewModel():
    // MainViewModel owns AudioSessionRepository/AudioRouteRepository listening
    // in init{}/onCleared(), so a second instance would both reset processing
    // settings to their defaults and stop those (singleton) listeners the
    // moment the second instance's onCleared() ran.
    val viewModel: MainViewModel = hiltViewModel()
    // A link shared into the app from elsewhere brings the player screen forward.
    val showPlayerRequest by playerViewModel.showPlayerRequest.collectAsStateWithLifecycle()
    LaunchedEffect(showPlayerRequest) {
        if (showPlayerRequest) {
            navController.navigate(ROUTE_PLAYER) { launchSingleTop = true }
            playerViewModel.consumeShowPlayerRequest()
        }
    }
    NavHost(navController = navController, startDestination = ROUTE_HOME) {
        composable(ROUTE_HOME) {
            MainScreen(
                viewModel = viewModel,
                spikeController = spikeController,
                onNavigateToDiagnostics = { navController.navigate(ROUTE_DIAGNOSTICS) },
                onOpenFullPlayer = { navController.navigate(ROUTE_PLAYER) { launchSingleTop = true } },
            )
        }
        composable(ROUTE_PLAYER) {
            PlayerScreen(
                viewModel = playerViewModel,
                onBack = { navController.popBackStack() },
                onOpenSearch = { viewModel.choosePlayerSource(PlayerSource.SOUNDCLOUD) },
            )
        }
        composable(ROUTE_DIAGNOSTICS) {
            val state by viewModel.engineState.collectAsStateWithLifecycle()
            val capabilities by viewModel.capabilities.collectAsStateWithLifecycle()
            val route by viewModel.currentRoute.collectAsStateWithLifecycle()
            val processingSettings by viewModel.processingSettings.collectAsStateWithLifecycle()
            val diagnosticsEvents by viewModel.diagnosticsEvents.collectAsStateWithLifecycle()

            DiagnosticsScreen(
                state = state,
                capabilities = capabilities,
                route = route,
                processingSettings = processingSettings,
                recentEvents = diagnosticsEvents,
                onBackClicked = { navController.popBackStack() },
            )
        }
    }
}
