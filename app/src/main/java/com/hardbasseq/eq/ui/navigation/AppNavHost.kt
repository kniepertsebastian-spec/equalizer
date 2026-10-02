package com.hardbasseq.eq.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.hardbasseq.eq.R
import com.hardbasseq.eq.audio.spike.SessionAttachSpikeController
import com.hardbasseq.eq.ui.MainScreen
import com.hardbasseq.eq.ui.diagnostics.DiagnosticsScreen
import com.hardbasseq.eq.ui.main.MainViewModel
import com.hardbasseq.eq.ui.player.DiscoveryViewModel
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
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val navigateTo: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(ROUTE_PLAYER) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    // A link shared into the app from elsewhere brings the player screen forward.
    val showPlayerRequest by playerViewModel.showPlayerRequest.collectAsStateWithLifecycle()
    LaunchedEffect(showPlayerRequest) {
        if (showPlayerRequest) {
            navigateTo(ROUTE_PLAYER)
            playerViewModel.consumeShowPlayerRequest()
        }
    }
    Scaffold(
        // The screens inside handle the status bar themselves; only the bar below needs the navigation bar inset.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (currentRoute == ROUTE_HOME || currentRoute == ROUTE_PLAYER) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentRoute == ROUTE_PLAYER,
                        onClick = { navigateTo(ROUTE_PLAYER) },
                        icon = { Icon(Icons.Default.LibraryMusic, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_player)) },
                    )
                    NavigationBarItem(
                        selected = currentRoute == ROUTE_HOME,
                        onClick = { navigateTo(ROUTE_HOME) },
                        icon = { Icon(Icons.Default.GraphicEq, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_equalizer)) },
                    )
                }
            }
        },
    ) { shellPadding ->
        NavHost(
            navController = navController,
            startDestination = ROUTE_PLAYER,
            modifier = Modifier.padding(shellPadding),
        ) {
            composable(ROUTE_HOME) {
                MainScreen(
                    viewModel = viewModel,
                    spikeController = spikeController,
                    onNavigateToDiagnostics = { navController.navigate(ROUTE_DIAGNOSTICS) },
                    onOpenFullPlayer = { navigateTo(ROUTE_PLAYER) },
                )
            }
            composable(ROUTE_PLAYER) {
                val activeContext by viewModel.activeContext.collectAsStateWithLifecycle()
                val discoveryViewModel: DiscoveryViewModel = hiltViewModel()
                val engineState by viewModel.engineState.collectAsStateWithLifecycle()
                val processingSettings by viewModel.processingSettings.collectAsStateWithLifecycle()
                PlayerScreen(
                    viewModel = playerViewModel,
                    discovery = discoveryViewModel,
                    activeContext = activeContext,
                    onContextModeChanged = { viewModel.setContextMode(it) },
                    eqState = engineState,
                    processingSettings = processingSettings,
                    onOpenEqualizer = { navigateTo(ROUTE_HOME) },
                    onSelectSource = { viewModel.choosePlayerSource(it) },
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
}
