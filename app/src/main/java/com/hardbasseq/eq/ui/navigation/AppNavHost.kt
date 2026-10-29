package com.hardbasseq.eq.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
import com.hardbasseq.eq.ui.player.LinkedServicesScreen
import com.hardbasseq.eq.ui.player.PlayerScreen
import com.hardbasseq.eq.ui.player.PlayerViewModel

const val ROUTE_HOME = "home"
const val ROUTE_DIAGNOSTICS = "diagnostics"
const val ROUTE_PLAYER = "player"
const val ROUTE_SERVICES = "services"

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
    val activePreset by viewModel.activePreset.collectAsStateWithLifecycle()
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
    // Selected: amber icon on a dark key and a paper label; the shape of the indicator,
    // not only its color, marks where the user is.
    val navItemColors =
        NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.onSurface,
            indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    Scaffold(
        // The screens inside handle the status bar themselves; only the bar below needs the navigation bar inset.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (currentRoute == ROUTE_HOME || currentRoute == ROUTE_PLAYER) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface) {
                    NavigationBarItem(
                        selected = currentRoute == ROUTE_PLAYER,
                        onClick = { navigateTo(ROUTE_PLAYER) },
                        icon = { Icon(Icons.Default.LibraryMusic, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_player)) },
                        colors = navItemColors,
                    )
                    NavigationBarItem(
                        selected = currentRoute == ROUTE_HOME,
                        onClick = { navigateTo(ROUTE_HOME) },
                        icon = { Icon(Icons.Default.GraphicEq, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_equalizer)) },
                        colors = navItemColors,
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
                    onOpenServices = { navController.navigate(ROUTE_SERVICES) { launchSingleTop = true } },
                )
            }
            composable(ROUTE_PLAYER) {
                val activeContext by viewModel.activeContext.collectAsStateWithLifecycle()
                val discoveryViewModel: DiscoveryViewModel = hiltViewModel()
                val engineState by viewModel.engineState.collectAsStateWithLifecycle()
                val processingSettings by viewModel.processingSettings.collectAsStateWithLifecycle()
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    PlayerScreen(
                        viewModel = playerViewModel,
                        discovery = discoveryViewModel,
                        activeContext = activeContext,
                        onContextModeChanged = { viewModel.setContextMode(it) },
                        eqState = engineState,
                        processingSettings = processingSettings,
                        onOpenEqualizer = { navigateTo(ROUTE_HOME) },
                        onOpenServices = { navController.navigate(ROUTE_SERVICES) { launchSingleTop = true } },
                        onSelectSource = { viewModel.choosePlayerSource(it) },
                        soundName = activePreset.name,
                        soundCurve = activePreset.targetCurve,
                    )
                }
            }
            composable(ROUTE_SERVICES) {
                LinkedServicesScreen(
                    viewModel = playerViewModel,
                    onBack = { navController.popBackStack() },
                    onOpenSoundProfiles = { navigateTo(ROUTE_HOME) },
                )
            }
            composable(ROUTE_DIAGNOSTICS) {
                val state by viewModel.engineState.collectAsStateWithLifecycle()
                val capabilities by viewModel.capabilities.collectAsStateWithLifecycle()
                val route by viewModel.currentRoute.collectAsStateWithLifecycle()
                val processingSettings by viewModel.processingSettings.collectAsStateWithLifecycle()
                val diagnosticsEvents by viewModel.diagnosticsEvents.collectAsStateWithLifecycle()
                val lastPlayerSource by viewModel.lastPlayerSource.collectAsStateWithLifecycle()
                val androidAutoConnected by viewModel.androidAutoConnected.collectAsStateWithLifecycle()

                DiagnosticsScreen(
                    state = state,
                    capabilities = capabilities,
                    route = route,
                    processingSettings = processingSettings,
                    recentEvents = diagnosticsEvents,
                    playerSource = lastPlayerSource?.name,
                    androidAutoConnected = androidAutoConnected,
                    onBackClicked = { navController.popBackStack() },
                )
            }
        }
    }
}
