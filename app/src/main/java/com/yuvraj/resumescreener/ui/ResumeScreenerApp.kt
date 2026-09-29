package com.yuvraj.resumescreener.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.yuvraj.resumescreener.ui.candidates.CandidatesScreen
import com.yuvraj.resumescreener.ui.dashboard.DashboardScreen
import com.yuvraj.resumescreener.ui.detail.CandidateDetailScreen
import com.yuvraj.resumescreener.ui.navigation.Routes
import com.yuvraj.resumescreener.ui.navigation.Section
import com.yuvraj.resumescreener.ui.screen.ScreenScreen
import com.yuvraj.resumescreener.ui.settings.SettingsScreen

@Composable
fun ResumeScreenerApp() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentDestination = backStack?.destination

    // The detail screen is pushed, not a tab, so the bar is hidden there.
    val showBottomBar = Section.entries.any { section ->
        currentDestination?.hierarchy?.any { it.route == section.route } == true
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer) {
                    Section.entries.forEach { section ->
                        val selected = currentDestination?.hierarchy?.any { it.route == section.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.navigateToTab(section.route) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) section.filled else section.outlined,
                                    contentDescription = null,
                                )
                            },
                            label = { Text(section.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary,
                                selectedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                                indicatorColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Section.DASHBOARD.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Section.DASHBOARD.route) {
                DashboardScreen(
                    onStartScreening = { navController.navigateToTab(Section.SCREEN.route) },
                    onOpenCandidate = { navController.navigate(Routes.candidateDetail(it)) },
                )
            }
            composable(Section.SCREEN.route) {
                ScreenScreen(
                    onOpenSettings = { navController.navigateToTab(Section.SETTINGS.route) },
                    onOpenCandidate = { navController.navigate(Routes.candidateDetail(it)) },
                )
            }
            composable(Section.CANDIDATES.route) {
                CandidatesScreen(
                    onOpenCandidate = { navController.navigate(Routes.candidateDetail(it)) },
                )
            }
            composable(Section.SETTINGS.route) {
                SettingsScreen()
            }
            composable(
                route = Routes.CANDIDATE_DETAIL,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) {
                CandidateDetailScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

/** Standard tab behaviour: single instance, state preserved across switches. */
private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
