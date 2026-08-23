package com.owlmedia.racecontrol.feature.standings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.res.stringResource
import com.owlmedia.racecontrol.R
import com.owlmedia.racecontrol.core.ui.RcDetailScaffold
import com.owlmedia.racecontrol.feature.AppState

/**
 * A thin, single-purpose host for [WdcCalculatorView] reached from a driver's detail
 * screen (the "Can win WDC" / "Can't win WDC" badge). Unlike the Standings tab, this
 * screen only ever shows one year and has no season picker, so it gets its own
 * screen-scoped [StandingsViewModel] rather than sharing the tab's, and only needs an
 * [AppState] carrying the fixed year WdcCalculatorView reads from.
 */
@Composable
fun TitleDeciderScreen(
    year: Int,
    onBack: () -> Unit,
    onOpenDriver: (String) -> Unit,
    viewModel: StandingsViewModel = hiltViewModel(),
) {
    LaunchedEffect(year) { viewModel.load(year, StandingsMode.WDC) }

    RcDetailScaffold(title = stringResource(R.string.title_decider), onBack = onBack) {
        WdcCalculatorView(
            viewModel = viewModel,
            appState = AppState(selectedYear = year),
            onOpenDriver = onOpenDriver,
        )
    }
}
