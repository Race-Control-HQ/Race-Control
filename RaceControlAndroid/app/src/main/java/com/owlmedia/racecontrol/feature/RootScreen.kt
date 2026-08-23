package com.owlmedia.racecontrol.feature

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.SportsScore
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.SportsScore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.owlmedia.racecontrol.R
import kotlin.reflect.KClass

/**
 * The five top-level destinations.
 *
 * Material allows 3-5 bottom-navigation items and all five of these are
 * genuinely peer-level, so the iOS tab count carries over unchanged. Labels are
 * always visible rather than icon-only, which both TalkBack users and anyone
 * unfamiliar with the icons need.
 */
private data class TopLevelDestination(
    val graph: Any,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val labelRes: Int,
    val routeClass: KClass<*>,
)

private val topLevelDestinations = listOf(
    TopLevelDestination(
        Routes.RacesGraph, Icons.Filled.SportsScore, Icons.Outlined.SportsScore,
        R.string.tab_races, Routes.RacesGraph::class,
    ),
    TopLevelDestination(
        Routes.DriversGraph, Icons.Filled.Groups, Icons.Outlined.Groups,
        R.string.tab_drivers, Routes.DriversGraph::class,
    ),
    TopLevelDestination(
        Routes.TeamsGraph, Icons.Filled.DirectionsCar, Icons.Outlined.DirectionsCar,
        R.string.tab_teams, Routes.TeamsGraph::class,
    ),
    TopLevelDestination(
        Routes.StandingsGraph, Icons.Filled.EmojiEvents, Icons.Outlined.EmojiEvents,
        R.string.tab_standings, Routes.StandingsGraph::class,
    ),
    TopLevelDestination(
        Routes.CircuitsGraph, Icons.Filled.Map, Icons.Outlined.Map,
        R.string.tab_circuits, Routes.CircuitsGraph::class,
    ),
)

@Composable
fun RootScreen() {
    val navController = rememberNavController()
    val appStateViewModel: AppStateViewModel = hiltViewModel()
    val appState by appStateViewModel.state.collectAsStateWithLifecycle()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    // Race detail and the analysis screens are shared across tabs, so they live
    // at the root of the graph and are not inside any tab's hierarchy. Tracking
    // the last tab the user was in keeps the bottom bar highlighted while they
    // are down inside one of those shared screens, instead of showing nothing
    // selected.
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(currentDestination) {
        val matched = topLevelDestinations.indexOfFirst { destination ->
            currentDestination?.hierarchy?.any { it.hasRoute(destination.routeClass) } == true
        }
        if (matched >= 0) selectedTab = matched
    }

    // A navigation rail is the Material answer for wider windows; below 600dp
    // the bottom bar stays.
    val configuration = LocalConfiguration.current
    val useRail = configuration.screenWidthDp >= 600

    val onTabSelected: (Int) -> Unit = { index ->
        selectedTab = index
        navController.switchTab(topLevelDestinations[index].graph)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        if (useRail) {
            Row(Modifier.fillMaxSize()) {
                RcNavigationRail(selectedTab, onTabSelected)
                Box(Modifier.fillMaxSize()) {
                    RaceControlNavHost(navController, appState, appStateViewModel::selectYear)
                }
            }
        } else {
            Scaffold(
                bottomBar = { RcNavigationBar(selectedTab, onTabSelected) },
                contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
            ) { padding ->
                Box(Modifier.padding(padding)) {
                    RaceControlNavHost(navController, appState, appStateViewModel::selectYear)
                }
            }
        }
    }
}

@Composable
private fun RcNavigationBar(selectedTab: Int, onSelect: (Int) -> Unit) {
    NavigationBar {
        topLevelDestinations.forEachIndexed { index, destination ->
            val selected = index == selectedTab
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(index) },
                icon = {
                    Icon(
                        imageVector = if (selected) destination.selectedIcon
                        else destination.unselectedIcon,
                        // The always-visible label below is the accessible name;
                        // describing the icon too would make TalkBack say it twice.
                        contentDescription = null,
                    )
                },
                label = { Text(stringResource(destination.labelRes)) },
                alwaysShowLabel = true,
            )
        }
    }
}

@Composable
private fun RcNavigationRail(selectedTab: Int, onSelect: (Int) -> Unit) {
    NavigationRail(
        modifier = Modifier.windowInsetsPadding(
            WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Vertical)
        )
    ) {
        topLevelDestinations.forEachIndexed { index, destination ->
            val selected = index == selectedTab
            NavigationRailItem(
                selected = selected,
                onClick = { onSelect(index) },
                icon = {
                    Icon(
                        imageVector = if (selected) destination.selectedIcon
                        else destination.unselectedIcon,
                        contentDescription = null,
                    )
                },
                label = { Text(stringResource(destination.labelRes)) },
                alwaysShowLabel = true,
            )
        }
    }
}

/**
 * Tab switching that behaves the way Android users expect: each tab keeps its
 * own back stack, re-selecting a tab returns to its root, and the system back
 * button walks back to the start destination rather than exiting immediately.
 */
private fun NavHostController.switchTab(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
