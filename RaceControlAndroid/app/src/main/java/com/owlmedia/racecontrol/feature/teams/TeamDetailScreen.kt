package com.owlmedia.racecontrol.feature.teams

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.owlmedia.racecontrol.R
import com.owlmedia.racecontrol.core.design.Dimens
import com.owlmedia.racecontrol.core.design.RcTheme
import com.owlmedia.racecontrol.core.design.legibleOnSurface
import com.owlmedia.racecontrol.core.design.teamColor
import com.owlmedia.racecontrol.core.ui.DriverAvatar
import com.owlmedia.racecontrol.core.ui.LoadableContent
import com.owlmedia.racecontrol.core.ui.RcCard
import com.owlmedia.racecontrol.core.ui.RcDetailScaffold
import com.owlmedia.racecontrol.core.ui.SectionHeader
import com.owlmedia.racecontrol.core.ui.StatCell
import com.owlmedia.racecontrol.core.ui.TeamLogo
import com.owlmedia.racecontrol.core.ui.UiState
import com.owlmedia.racecontrol.data.remote.dto.TeamDriverDto
import com.owlmedia.racecontrol.data.remote.dto.TeamDto
import com.owlmedia.racecontrol.data.repository.RaceControlRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class TeamDetailViewModel @Inject constructor(
    private val repository: RaceControlRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<TeamDto>>(UiState.Idle)
    val state: StateFlow<UiState<TeamDto>> = _state.asStateFlow()

    fun load(year: Int, teamId: String) {
        if (_state.value is UiState.Loaded) return
        viewModelScope.launch {
            _state.value = UiState.Loading
            repository.teamDetail(year, teamId)
                .onSuccess { _state.value = UiState.Loaded(it) }
                .onFailure { _state.value = UiState.Failed(repository.messageFor(it)) }
        }
    }
}

@Composable
fun TeamDetailScreen(
    year: Int,
    teamId: String,
    onBack: () -> Unit,
    viewModel: TeamDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(year, teamId) { viewModel.load(year, teamId) }

    val title = (state as? UiState.Loaded)?.value?.teamName.orEmpty()

    RcDetailScaffold(title = title, onBack = onBack) { modifier ->
        LoadableContent(
            state = state,
            onRetry = { viewModel.load(year, teamId) },
            modifier = modifier,
        ) { team ->
            val accent = teamColor(team.teamColor).legibleOnSurface()
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(Dimens.MD),
                verticalArrangement = Arrangement.spacedBy(Dimens.MD),
            ) {
                RcCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.SM)) {
                        TeamLogo(url = team.teamLogoUrl, size = 40.dp)
                        Column {
                            Text(
                                text = team.teamName.orEmpty(),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = accent,
                            )
                            Text(
                                text = team.nationality.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = RcTheme.colors.textSecondary,
                            )
                        }
                    }
                }

                RcCard {
                    Row(Modifier.fillMaxWidth()) {
                        StatCell(
                            value = team.pointsString,
                            label = stringResource(R.string.stat_points),
                            modifier = Modifier.weight(1f),
                        )
                        StatCell(
                            value = team.wins?.numberLabel ?: "0",
                            label = stringResource(R.string.stat_wins),
                            modifier = Modifier.weight(1f),
                        )
                        StatCell(
                            value = team.positionInt?.toString() ?: "–",
                            label = stringResource(R.string.stat_position),
                            modifier = Modifier.weight(1f),
                            accent = accent,
                        )
                    }
                }

                if (team.drivers.isNotEmpty()) {
                    PointsBreakdownCard(drivers = team.drivers, accent = accent)

                    RcCard {
                        SectionHeader(stringResource(R.string.line_up))
                        team.drivers.forEach { driver ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = Dimens.SM),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Dimens.SM),
                            ) {
                                DriverAvatar(
                                    url = driver.headshotUrl,
                                    initials = driver.initials,
                                    accent = accent,
                                    size = 44.dp,
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = driver.name.orEmpty(),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = RcTheme.colors.textPrimary,
                                    )
                                    driver.number?.stringValue?.let {
                                        Text(
                                            text = "#$it",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = RcTheme.colors.textTertiary,
                                        )
                                    }
                                }
                                Text(
                                    text = stringResource(R.string.points_suffix, driver.pointsLabel),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = RcTheme.colors.textPrimary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Who's actually carried the team, at a glance: a driver list alone doesn't
 * show whether points are split evenly or one driver is doing most of the
 * scoring.
 */
@Composable
private fun PointsBreakdownCard(drivers: List<TeamDriverDto>, accent: Color) {
    val total = drivers.sumOf { it.pointsValue }
    if (total <= 0) return

    RcCard {
        SectionHeader(stringResource(R.string.points_breakdown))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(RcTheme.colors.surfaceElevated),
        ) {
            drivers.forEachIndexed { index, driver ->
                val fraction = (driver.pointsValue / total).toFloat()
                if (fraction > 0f) {
                    Box(
                        modifier = Modifier
                            .weight(fraction)
                            .fillMaxHeight()
                            .background(accent.copy(alpha = if (index == 0) 1f else 0.5f)),
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Dimens.SM),
            horizontalArrangement = Arrangement.spacedBy(Dimens.MD),
        ) {
            drivers.forEach { driver ->
                val pct = if (total > 0) ((driver.pointsValue / total) * 100).toInt() else 0
                Text(
                    text = stringResource(
                        R.string.points_breakdown_entry,
                        driver.code ?: driver.name.orEmpty(),
                        driver.pointsLabel,
                        pct,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = RcTheme.colors.textSecondary,
                )
            }
        }
    }
}
