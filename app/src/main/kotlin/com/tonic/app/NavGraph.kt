package com.tonic.app

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tonic.app.home.HomeScreen
import com.tonic.app.onboarding.OnboardingScreen
import com.tonic.app.summary.SummaryScreen
import com.tonic.app.summary.SummaryViewModel
import com.tonic.feature.diagnostic.ui.DiagnosticScreen
import com.tonic.feature.practice.ui.PracticeScreen
import com.tonic.feature.progress.ui.ProgressScreen
import com.tonic.feature.settings.ui.SettingsScreen

/**
 * Routes for every top-level destination — docs/08-UI-SPEC.md §2's screen inventory. Cross-feature
 * navigation goes through this graph in `:app`, never feature-to-feature directly
 * (docs/04-ARCHITECTURE.md §2). No bottom navigation bar: Home is the hub, Progress and Settings are
 * reachable from it (docs/08-UI-SPEC.md §2).
 */
sealed interface TonicRoute {
    val route: String

    data object Home : TonicRoute {
        override val route = "home"
    }

    /** Shown once, before Diagnostic - see [com.tonic.app.onboarding.OnboardingScreen]. */
    data object Onboarding : TonicRoute {
        override val route = "onboarding"
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

    /** `summary/{sessionId}` — docs/08-UI-SPEC.md §2. */
    data object Summary : TonicRoute {
        override val route = "summary/{${SummaryViewModel.SESSION_ID_ARG}}"

        fun routeFor(sessionId: Long) = "summary/$sessionId"
    }
}

@Composable
fun TonicNavGraph(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = TonicRoute.Home.route) {
        composable(TonicRoute.Home.route) {
            HomeScreen(
                onNeedsOnboarding = {
                    navController.navigate(TonicRoute.Onboarding.route) {
                        popUpTo(TonicRoute.Home.route) { inclusive = true }
                    }
                },
                onNeedsDiagnostic = {
                    navController.navigate(TonicRoute.Diagnostic.route) {
                        popUpTo(TonicRoute.Home.route) { inclusive = true }
                    }
                },
                onStartPractice = { navController.navigate(TonicRoute.Practice.route) },
                onOpenProgress = { navController.navigate(TonicRoute.Progress.route) },
                onOpenSettings = { navController.navigate(TonicRoute.Settings.route) },
            )
        }
        composable(TonicRoute.Onboarding.route) {
            OnboardingScreen(
                onDone = {
                    // Back to Home rather than straight to Diagnostic: Home re-evaluates needsDiagnostic
                    // itself, so this route doesn't need to know or duplicate that decision.
                    navController.navigate(TonicRoute.Home.route) {
                        popUpTo(TonicRoute.Onboarding.route) { inclusive = true }
                    }
                },
            )
        }
        composable(TonicRoute.Diagnostic.route) {
            DiagnosticScreen(
                onContinue = {
                    // Both placement outcomes land on Home: M1 remediation's actual training content is
                    // out of Phase 1 scope (CLAUDE.md §2). DiagnosticLoopEngine already unlocked
                    // M2_DEG_SET_1 either way, so Home has something practiceable regardless of outcome.
                    navController.navigate(TonicRoute.Home.route) {
                        popUpTo(TonicRoute.Diagnostic.route) { inclusive = true }
                    }
                },
            )
        }
        composable(TonicRoute.Practice.route) {
            PracticeScreen(
                onSessionComplete = { sessionId ->
                    navController.navigate(TonicRoute.Summary.routeFor(sessionId)) {
                        popUpTo(TonicRoute.Practice.route) { inclusive = true }
                    }
                },
                // docs/08-UI-SPEC.md §2a. The session was already persisted for resume by the time this
                // fires - PracticeViewModel.onExitSession sequences the write before navigation.
                onExitToHome = {
                    navController.navigate(TonicRoute.Home.route) {
                        popUpTo(TonicRoute.Home.route) { inclusive = true }
                    }
                },
            )
        }
        composable(
            route = TonicRoute.Summary.route,
            arguments = listOf(navArgument(SummaryViewModel.SESSION_ID_ARG) { type = NavType.LongType }),
        ) {
            SummaryScreen(
                onDone = {
                    navController.navigate(TonicRoute.Home.route) {
                        popUpTo(TonicRoute.Home.route) { inclusive = true }
                    }
                },
            )
        }
        composable(TonicRoute.Progress.route) { ProgressScreen() }
        composable(TonicRoute.Settings.route) {
            SettingsScreen(
                // Debug-only, and a no-op in a release build because the section that fires it is
                // compiled out. Straight into practice on the seeded node - that is what a "jump to
                // node" button means, and what the first version conspicuously did not do.
                onDebugJumpFinished = {
                    navController.navigate(TonicRoute.Practice.route) {
                        popUpTo(TonicRoute.Home.route)
                    }
                },
            )
        }
    }
}
