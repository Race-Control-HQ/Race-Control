package com.owlmedia.racecontrol.feature

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import androidx.navigation.navigation
import androidx.navigation.toRoute
import com.owlmedia.racecontrol.feature.analysis.BrakeThrottleScreen
import com.owlmedia.racecontrol.feature.analysis.FlagsScreen
import com.owlmedia.racecontrol.feature.analysis.LapTimesScreen
import com.owlmedia.racecontrol.feature.analysis.MiniSectorsScreen
import com.owlmedia.racecontrol.feature.analysis.PenaltiesScreen
import com.owlmedia.racecontrol.feature.analysis.PositionChartScreen
import com.owlmedia.racecontrol.feature.analysis.PitStopsScreen
import com.owlmedia.racecontrol.feature.analysis.PitSwimlaneScreen
import com.owlmedia.racecontrol.feature.analysis.QualifyingLadderScreen
import com.owlmedia.racecontrol.feature.analysis.QualifyingScreen
import com.owlmedia.racecontrol.feature.analysis.QualifyingSectorsScreen
import com.owlmedia.racecontrol.feature.analysis.RaceControlScreen
import com.owlmedia.racecontrol.feature.analysis.RaceTraceScreen
import com.owlmedia.racecontrol.feature.analysis.RetirementsScreen
import com.owlmedia.racecontrol.feature.analysis.SpeedTrapScreen
import com.owlmedia.racecontrol.feature.analysis.StrategyScreen
import com.owlmedia.racecontrol.feature.analysis.TelemetryScreen
import com.owlmedia.racecontrol.feature.analysis.TyrePerformanceScreen
import com.owlmedia.racecontrol.feature.analysis.WeatherScreen
import com.owlmedia.racecontrol.feature.circuits.CircuitDetailScreen
import com.owlmedia.racecontrol.feature.circuits.CircuitsScreen
import com.owlmedia.racecontrol.feature.circuits.CornerSpeedProfileScreen
import com.owlmedia.racecontrol.feature.circuits.TrackMap
import com.owlmedia.racecontrol.feature.circuits.TrackMapScreen
import com.owlmedia.racecontrol.feature.drivers.DriverDetailScreen
import com.owlmedia.racecontrol.feature.drivers.DriversScreen
import com.owlmedia.racecontrol.feature.drivers.HeadToHeadScreen
import com.owlmedia.racecontrol.feature.racedetail.RaceDetailScreen
import com.owlmedia.racecontrol.feature.replay.ReplayScreen
import com.owlmedia.racecontrol.feature.schedule.ScheduleScreen
import com.owlmedia.racecontrol.feature.settings.SettingsScreen
import com.owlmedia.racecontrol.feature.standings.StandingsScreen
import com.owlmedia.racecontrol.feature.standings.TitleDeciderScreen
import com.owlmedia.racecontrol.feature.teams.TeamDetailScreen
import com.owlmedia.racecontrol.feature.teams.TeamsScreen

/**
 * The whole navigation graph.
 *
 * Each tab is a nested graph, which gives every tab its own back stack: the
 * Android equivalent of iOS wrapping each tab in its own NavigationStack.
 *
 * Race detail, the eight analysis screens and Settings are reachable from more
 * than one tab (a race opens from Races *and* from Circuits). Routes must be
 * unique within a NavHost, so those are declared **once** at the root of the
 * graph rather than duplicated into each tab. The bottom bar keeps its
 * highlight across them by remembering the tab the user came from: see
 * RootScreen.
 */
@Composable
fun RaceControlNavHost(
    navController: NavHostController,
    appState: AppState,
    onSelectYear: (Int) -> Unit,
) {
    NavHost(
        navController = navController,
        startDestination = Routes.RacesGraph,
    ) {
        navigation<Routes.RacesGraph>(startDestination = Routes.Races) {
            composable<Routes.Races> {
                ScheduleScreen(
                    appState = appState,
                    onSelectYear = onSelectYear,
                    onOpenRace = { year, round, title ->
                        navController.navigate(Routes.RaceDetail(year, round, title))
                    },
                    onOpenSettings = { navController.navigate(Routes.Settings) },
                )
            }
        }

        navigation<Routes.DriversGraph>(startDestination = Routes.Drivers) {
            composable<Routes.Drivers> {
                DriversScreen(
                    appState = appState,
                    onSelectYear = onSelectYear,
                    onOpenDriver = { driverId ->
                        navController.navigate(Routes.DriverDetail(appState.selectedYear, driverId))
                    },
                    onOpenHeadToHead = {
                        navController.navigate(Routes.HeadToHead(appState.selectedYear))
                    },
                )
            }
            composable<Routes.DriverDetail> { entry ->
                val route = entry.toRoute<Routes.DriverDetail>()
                DriverDetailScreen(
                    year = route.year,
                    driverId = route.driverId,
                    onBack = navController::popBackStack,
                    onOpenTitleDecider = {
                        navController.navigate(Routes.TitleDecider(route.year))
                    },
                )
            }
            composable<Routes.HeadToHead> { entry ->
                val route = entry.toRoute<Routes.HeadToHead>()
                HeadToHeadScreen(year = route.year, onBack = navController::popBackStack)
            }
            composable<Routes.TitleDecider> { entry ->
                val route = entry.toRoute<Routes.TitleDecider>()
                TitleDeciderScreen(
                    year = route.year,
                    onBack = navController::popBackStack,
                    onOpenDriver = { driverId ->
                        navController.navigate(Routes.DriverDetail(route.year, driverId))
                    },
                )
            }
        }

        navigation<Routes.TeamsGraph>(startDestination = Routes.Teams) {
            composable<Routes.Teams> {
                TeamsScreen(
                    appState = appState,
                    onSelectYear = onSelectYear,
                    onOpenTeam = { teamId ->
                        navController.navigate(Routes.TeamDetail(appState.selectedYear, teamId))
                    },
                )
            }
            composable<Routes.TeamDetail> { entry ->
                val route = entry.toRoute<Routes.TeamDetail>()
                TeamDetailScreen(
                    year = route.year,
                    teamId = route.teamId,
                    onBack = navController::popBackStack,
                )
            }
        }

        navigation<Routes.StandingsGraph>(startDestination = Routes.Standings) {
            composable<Routes.Standings> {
                StandingsScreen(
                    appState = appState,
                    onSelectYear = onSelectYear,
                    onOpenDriver = { driverId ->
                        navController.navigate(Routes.DriverDetail(appState.selectedYear, driverId))
                    },
                )
            }
        }

        navigation<Routes.CircuitsGraph>(startDestination = Routes.Circuits) {
            composable<Routes.Circuits> {
                CircuitsScreen(
                    appState = appState,
                    onSelectYear = onSelectYear,
                    onOpenCircuit = { year, round, name ->
                        navController.navigate(Routes.CircuitDetail(year, round, name))
                    },
                )
            }
            composable<Routes.CircuitDetail> { entry ->
                val route = entry.toRoute<Routes.CircuitDetail>()
                CircuitDetailScreen(
                    year = route.year,
                    round = route.round,
                    circuitName = route.circuitName,
                    onBack = navController::popBackStack,
                    onOpenReplay = { title ->
                        navController.navigate(Routes.Replay(route.year, route.round, title))
                    },
                    onOpenResults = { title ->
                        navController.navigate(Routes.RaceDetail(route.year, route.round, title))
                    },
                )
            }
        }

        /* -------- shared destinations, declared once ------------------- */

        composable<Routes.RaceDetail>(
            // Session reminders open the race that is about to start.
            deepLinks = listOf(navDeepLink { uriPattern = Routes.RACE_DEEP_LINK }),
        ) { entry ->
            val route = entry.toRoute<Routes.RaceDetail>()
            RaceDetailScreen(
                year = route.year,
                round = route.round,
                title = route.title,
                onBack = navController::popBackStack,
                onOpenAnalysis = { navController.navigate(it) },
            )
        }

        composable<Routes.Settings> {
            SettingsScreen(onBack = navController::popBackStack)
        }

        analysisDestinations(navController)
    }
}

private fun NavGraphBuilder.analysisDestinations(navController: NavHostController) {
    composable<Routes.Replay> { entry ->
        val route = entry.toRoute<Routes.Replay>()
        ReplayScreen(
            year = route.year,
            round = route.round,
            title = route.title,
            onBack = navController::popBackStack,
            onOpenDriver = { driverId -> navController.navigate(Routes.DriverDetail(route.year, driverId)) },
        )
    }
    composable<Routes.Telemetry> { entry ->
        val route = entry.toRoute<Routes.Telemetry>()
        TelemetryScreen(route.year, route.round, route.title, navController::popBackStack)
    }
    composable<Routes.BrakeThrottle> { entry ->
        val route = entry.toRoute<Routes.BrakeThrottle>()
        BrakeThrottleScreen(route.year, route.round, route.title, navController::popBackStack)
    }
    composable<Routes.LapTimes> { entry ->
        val route = entry.toRoute<Routes.LapTimes>()
        LapTimesScreen(route.year, route.round, route.title, navController::popBackStack)
    }
    composable<Routes.Strategy> { entry ->
        val route = entry.toRoute<Routes.Strategy>()
        StrategyScreen(
            year = route.year,
            round = route.round,
            title = route.title,
            onBack = navController::popBackStack,
            onOpenDriver = { driverId -> navController.navigate(Routes.DriverDetail(route.year, driverId)) },
        )
    }
    composable<Routes.RaceTrace> { entry ->
        val route = entry.toRoute<Routes.RaceTrace>()
        RaceTraceScreen(route.year, route.round, route.title, navController::popBackStack)
    }
    composable<Routes.PositionChart> { entry ->
        val route = entry.toRoute<Routes.PositionChart>()
        PositionChartScreen(route.year, route.round, route.title, navController::popBackStack)
    }
    composable<Routes.TyrePerformance> { entry ->
        val route = entry.toRoute<Routes.TyrePerformance>()
        TyrePerformanceScreen(route.year, route.round, route.title, navController::popBackStack)
    }
    composable<Routes.PitSwimlane> { entry ->
        val route = entry.toRoute<Routes.PitSwimlane>()
        PitSwimlaneScreen(route.year, route.round, route.title, navController::popBackStack)
    }
    composable<Routes.PitStops> { entry ->
        val route = entry.toRoute<Routes.PitStops>()
        PitStopsScreen(route.year, route.round, route.title, navController::popBackStack)
    }
    composable<Routes.QualifyingSectors> { entry ->
        val route = entry.toRoute<Routes.QualifyingSectors>()
        QualifyingSectorsScreen(route.year, route.round, route.title, navController::popBackStack)
    }
    composable<Routes.SpeedTrap> { entry ->
        val route = entry.toRoute<Routes.SpeedTrap>()
        SpeedTrapScreen(route.year, route.round, route.title, navController::popBackStack)
    }
    composable<Routes.MiniSectors> { entry ->
        val route = entry.toRoute<Routes.MiniSectors>()
        MiniSectorsScreen(route.year, route.round, route.title, navController::popBackStack)
    }
    composable<Routes.Qualifying> { entry ->
        val route = entry.toRoute<Routes.Qualifying>()
        QualifyingScreen(route.year, route.round, route.title, navController::popBackStack)
    }
    composable<Routes.QualifyingLadder> { entry ->
        val route = entry.toRoute<Routes.QualifyingLadder>()
        QualifyingLadderScreen(route.year, route.round, route.title, navController::popBackStack)
    }
    composable<Routes.TrackMap> { entry ->
        val route = entry.toRoute<Routes.TrackMap>()
        TrackMapScreen(route.year, route.round, route.title, navController::popBackStack)
    }
    composable<Routes.CornerSpeedProfile> { entry ->
        val route = entry.toRoute<Routes.CornerSpeedProfile>()
        CornerSpeedProfileScreen(route.year, route.round, route.title, navController::popBackStack)
    }
    composable<Routes.Weather> { entry ->
        val route = entry.toRoute<Routes.Weather>()
        WeatherScreen(route.year, route.round, route.title, navController::popBackStack)
    }
    composable<Routes.Retirements> { entry ->
        val route = entry.toRoute<Routes.Retirements>()
        RetirementsScreen(
            year = route.year,
            round = route.round,
            title = route.title,
            onBack = navController::popBackStack,
            onOpenDriver = { driverId -> navController.navigate(Routes.DriverDetail(route.year, driverId)) },
        )
    }
    composable<Routes.Flags> { entry ->
        val route = entry.toRoute<Routes.Flags>()
        FlagsScreen(route.year, route.round, route.title, navController::popBackStack)
    }
    composable<Routes.RaceControl> { entry ->
        val route = entry.toRoute<Routes.RaceControl>()
        RaceControlScreen(route.year, route.round, route.title, navController::popBackStack)
    }
    composable<Routes.Penalties> { entry ->
        val route = entry.toRoute<Routes.Penalties>()
        PenaltiesScreen(
            year = route.year,
            round = route.round,
            title = route.title,
            onBack = navController::popBackStack,
            onOpenDriver = { driverId -> navController.navigate(Routes.DriverDetail(route.year, driverId)) },
        )
    }
}
