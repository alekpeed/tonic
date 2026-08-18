package com.tonic.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tonic.feature.diagnostic.ui.DiagnosticScreen
import com.tonic.feature.practice.ui.PracticeScreen

/**
 * Routes for the four top-level destinations. Real screens are wired in as
 * each feature module is built (docs/09-BUILD-PLAN.md Stages 7–9);
 * cross-feature navigation goes through this graph in :app, never
 * feature-to-feature directly (docs/04-ARCHITECTURE.md §2).
 */
sealed interface TonicRoute {
    val route: String

    data object Home : TonicRoute {
        override val route = "home"
    }

    data object Diagnostic : TonicRoute {
        override val route = "diagnostic"
    }

    data object Practice : TonicRoute {
        override val route = "practice"
    }

    data object Progress : TonicRoute {
        override val route = "progress"
    }

    data object Settings : TonicRoute {
        override val route = "settings"
    }
}

@Composable
fun TonicNavGraph(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = TonicRoute.Home.route) {
        composable(TonicRoute.Home.route) { PlaceholderScreen("Home") }
        composable(TonicRoute.Diagnostic.route) {
            DiagnosticScreen(
                onContinue = {
                    // Both placement outcomes land on Home for now: M1 remediation's actual training
                    // content is out of Phase 1 scope (CLAUDE.md §2), and Home itself is Stage 9, not
                    // yet built - there is nowhere else to route either outcome to yet. Revisit once
                    // both exist; this screen's own placement decision doesn't need to change.
                    navController.navigate(TonicRoute.Home.route) {
                        popUpTo(TonicRoute.Diagnostic.route) { inclusive = true }
                    }
                },
            )
        }
        composable(TonicRoute.Practice.route) { PracticeScreen() }
        composable(TonicRoute.Progress.route) { PlaceholderScreen("Progress") }
        composable(TonicRoute.Settings.route) { PlaceholderScreen("Settings") }
    }
}

/** Stage 0 stand-in. Replaced route by route as each feature is built. */
@Composable
private fun PlaceholderScreen(name: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = name)
    }
}
