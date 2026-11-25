package com.auracurve.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.auracurve.app.R
import com.auracurve.app.ui.MainScreen
import com.auracurve.app.ui.components.cyber.CyberBottomBar
import com.auracurve.app.ui.components.cyber.CyberNavItem
import com.auracurve.app.ui.diagnostics.DiagnosticsScreen
import com.auracurve.app.ui.eq.EqStatus
import com.auracurve.app.ui.main.MainViewModel
import com.auracurve.app.ui.main.SystemDiagnosticsSnapshot
import com.auracurve.app.ui.player.DiscoveryViewModel
import com.auracurve.app.ui.player.LinkedServicesScreen
import com.auracurve.app.ui.player.PlayerScreen
import com.auracurve.app.ui.player.PlayerViewModel
import com.auracurve.app.ui.player.shell.NowPlayingLayer
import com.auracurve.app.ui.player.shell.ShellMiniPlayer
import com.auracurve.app.ui.player.shell.ShellSound
import com.auracurve.app.ui.player.shell.rememberPlayerSheetState
import com.auracurve.app.ui.theme.cyberBackdrop

const val ROUTE_HOME = "home"
const val ROUTE_DIAGNOSTICS = "diagnostics"
const val ROUTE_PLAYER = "player"
const val ROUTE_SERVICES = "services"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppNavHost(playerViewModel: PlayerViewModel) {
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
    val sheet = rememberPlayerSheetState()
    val engineState by viewModel.engineState.collectAsStateWithLifecycle()
    val processingSettings by viewModel.processingSettings.collectAsStateWithLifecycle()
    val activeContext by viewModel.activeContext.collectAsStateWithLifecycle()
    val displayRoute by viewModel.displayRoute.collectAsStateWithLifecycle()
    val nowPlaying by playerViewModel.nowPlaying.collectAsStateWithLifecycle()
    val eqStatus = EqStatus.kind(engineState, processingSettings)
    val tabsShown = currentRoute == ROUTE_HOME || currentRoute == ROUTE_PLAYER
    // Above the shell: "Now playing" covers tabs and navigation bar while it is pulled up.
    // The Cyber backdrop is drawn once here, below everything; the screens on it stay transparent.
    BoxWithConstraints(modifier = Modifier.fillMaxSize().cyberBackdrop()) {
        val dragRangePx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        Scaffold(
            // The screens inside handle the status bar themselves; only the bar below needs the navigation bar inset.
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = Color.Transparent,
            bottomBar = {
                if (tabsShown) {
                    Column {
                        // The mini player stays in both tabs; it makes room for the keyboard.
                        if (!WindowInsets.isImeVisible) {
                            ShellMiniPlayer(viewModel = playerViewModel, sheet = sheet, eqStatus = eqStatus, dragRangePx = dragRangePx)
                        }
                        AppBottomBar(currentRoute = currentRoute, onNavigate = navigateTo)
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
                        onNavigateToDiagnostics = { navController.navigate(ROUTE_DIAGNOSTICS) },
                        playerLoaded = nowPlaying != null,
                        playerPlaying = nowPlaying?.let { it.isPlaying && !it.isLoading } == true,
                        onOpenServices = { navController.navigate(ROUTE_SERVICES) { launchSingleTop = true } },
                    )
                }
                composable(ROUTE_PLAYER) {
                    val discoveryViewModel: DiscoveryViewModel = hiltViewModel()
                    Box(modifier = Modifier.fillMaxSize()) {
                        PlayerScreen(
                            viewModel = playerViewModel,
                            discovery = discoveryViewModel,
                            eqState = engineState,
                            processingSettings = processingSettings,
                            onOpenEqualizer = { navigateTo(ROUTE_HOME) },
                            onOpenServices = { navController.navigate(ROUTE_SERVICES) { launchSingleTop = true } },
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

                    val systemDiagnostics by produceState(SystemDiagnosticsSnapshot()) {
                        value = viewModel.loadSystemDiagnostics()
                    }

                    DiagnosticsScreen(
                        state = state,
                        capabilities = capabilities,
                        route = route,
                        processingSettings = processingSettings,
                        recentEvents = diagnosticsEvents,
                        playerSource = lastPlayerSource?.name,
                        androidAutoConnected = androidAutoConnected,
                        processExits = systemDiagnostics.processExits,
                        outputAudio = systemDiagnostics.outputAudio,
                        extendedAppDetection = systemDiagnostics.extendedAppDetection,
                        onBackClicked = { navController.popBackStack() },
                    )
                }
            }
        }
        NowPlayingLayer(
            viewModel = playerViewModel,
            sheet = sheet,
            sound =
                ShellSound(
                    eqStatus = eqStatus,
                    soundName = activePreset.name,
                    soundCurve = activePreset.targetCurve,
                    activeContext = activeContext,
                    route = displayRoute,
                ),
            dragRangePx = dragRangePx,
            onOpenEqualizer = { navigateTo(ROUTE_HOME) },
            onGoToSearch = { navigateTo(ROUTE_PLAYER) },
            onContextModeChanged = { viewModel.setContextMode(it) },
        )
    }
}

// The two main tabs. Its own composable so the screenshot tests can draw it below a screen.
@Composable
internal fun AppBottomBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
) {
    // Selected: a cyan icon under a glowing mark on the bar's edge and a bold paper label; the
    // mark and the weight, not only the color, show where the user is.
    val player = stringResource(R.string.nav_player)
    val equalizer = stringResource(R.string.nav_equalizer)
    val items =
        remember(player, equalizer) {
            listOf(
                CyberNavItem(ROUTE_PLAYER, player, Icons.Default.LibraryMusic),
                CyberNavItem(ROUTE_HOME, equalizer, Icons.Default.GraphicEq),
            )
        }
    // The equalizer wears the hardcore look (decision H1), its bar too; the player stays Cyber.
    CyberBottomBar(items = items, selectedRoute = currentRoute, onSelect = onNavigate, hardcore = currentRoute == ROUTE_HOME)
}
