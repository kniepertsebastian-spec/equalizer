package com.hardbasseq.eq.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.hardbasseq.eq.audio.spike.SessionAttachSpikeController
import com.hardbasseq.eq.ui.MainScreen
import com.hardbasseq.eq.ui.main.MainViewModel

private const val ROUTE_HOME = "home"

/**
 * Root navigation graph (roadmap M1: "Navigation ... erstellen"). Only one
 * destination exists today; this establishes the graph shape the planned
 * feature modules (onboarding, presets, profiles, diagnostics - roadmap §7)
 * will add routes to later, rather than routing being bolted on afterwards.
 */
@Composable
fun AppNavHost(
    viewModel: MainViewModel,
    spikeController: SessionAttachSpikeController,
) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = ROUTE_HOME) {
        composable(ROUTE_HOME) {
            MainScreen(viewModel = viewModel, spikeController = spikeController)
        }
    }
}
