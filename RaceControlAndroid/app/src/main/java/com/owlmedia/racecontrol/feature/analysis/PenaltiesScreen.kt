package com.owlmedia.racecontrol.feature.analysis

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.owlmedia.racecontrol.R
import com.owlmedia.racecontrol.core.design.Dimens
import com.owlmedia.racecontrol.core.design.RcShapes
import com.owlmedia.racecontrol.core.design.RcTheme
import com.owlmedia.racecontrol.core.ui.EmptyState
import com.owlmedia.racecontrol.core.ui.LoadableContent
import com.owlmedia.racecontrol.core.ui.RcDetailScaffold
import com.owlmedia.racecontrol.core.ui.TeamLogo
import com.owlmedia.racecontrol.core.ui.UiState
import com.owlmedia.racecontrol.core.util.FlexibleDate
import com.owlmedia.racecontrol.core.util.formatTimeWithZone
import com.owlmedia.racecontrol.data.remote.dto.PenaltiesResponseDto
import com.owlmedia.racecontrol.data.remote.dto.PenaltyDto
import com.owlmedia.racecontrol.data.remote.dto.PenaltyType
import com.owlmedia.racecontrol.data.repository.RaceControlRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class PenaltiesViewModel @Inject constructor(
    private val repository: RaceControlRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<PenaltiesResponseDto>>(UiState.Idle)
    val state: StateFlow<UiState<PenaltiesResponseDto>> = _state.asStateFlow()

    fun load(year: Int, round: Int) {
        if (_state.value is UiState.Loaded) return
        viewModelScope.launch {
            _state.value = UiState.Loading
            repository.penalties(year, round)
                .onSuccess { _state.value = UiState.Loaded(it) }
                .onFailure { _state.value = UiState.Failed(repository.messageFor(it)) }
        }
    }
}

/**
 * Stewards' penalties and reprimands for a race: a focused subset of the
 * full [RaceControlScreen] log, surfacing just the rows that carry a
 * consequence (time penalty, drive-through, grid drop, reprimand,
 * disqualification) so they don't get lost among car-event chatter.
 */
@Composable
fun PenaltiesScreen(
    year: Int,
    round: Int,
    title: String,
    onBack: () -> Unit,
    onOpenDriver: (String) -> Unit = {},
    viewModel: PenaltiesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(year, round) { viewModel.load(year, round) }

    RcDetailScaffold(
        title = stringResource(R.string.analysis_penalties),
        onBack = onBack,
    ) { modifier ->
        LoadableContent(
            state = state,
            onRetry = { viewModel.load(year, round) },
            modifier = modifier,
        ) { data ->
            if (data.penalties.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.CheckCircle,
                    title = stringResource(R.string.no_penalties_title),
                    message = stringResource(R.string.no_penalties_message),
                )
                return@LoadableContent
            }

            LazyColumn(
                contentPadding = PaddingValues(
                    start = Dimens.MD,
                    end = Dimens.MD,
                    top = Dimens.SM,
                    bottom = Dimens.MD,
                ),
                verticalArrangement = Arrangement.spacedBy(Dimens.SM),
            ) {
                items(
                    items = data.penalties,
                    key = { "${it.time}-${it.driverCode}-${it.type}" },
                    contentType = { "penalty" },
                ) { penalty ->
                    PenaltyRow(penalty = penalty, onOpenDriver = onOpenDriver)
                }
            }
        }
    }
}

@Composable
private fun penaltyIcon(type: PenaltyType): ImageVector = when (type) {
    PenaltyType.TIME -> Icons.Filled.Timer
    PenaltyType.STOP_AND_GO -> Icons.Filled.ReportProblem
    PenaltyType.DRIVE_THROUGH -> Icons.Filled.ReportProblem
    PenaltyType.GRID -> Icons.Filled.FormatListNumbered
    PenaltyType.REPRIMAND -> Icons.Filled.Warning
    PenaltyType.DISQUALIFICATION -> Icons.Filled.Block
}

@Composable
private fun penaltyColor(type: PenaltyType): Color = when (type) {
    PenaltyType.TIME -> RcTheme.colors.warning
    PenaltyType.STOP_AND_GO -> RcTheme.colors.warning
    PenaltyType.DRIVE_THROUGH -> RcTheme.colors.warning
    PenaltyType.GRID -> RcTheme.colors.info
    PenaltyType.REPRIMAND -> RcTheme.colors.textSecondary
    PenaltyType.DISQUALIFICATION -> RcTheme.colors.racingRed
}

@Composable
private fun PenaltyRow(penalty: PenaltyDto, onOpenDriver: (String) -> Unit) {
    val type = penalty.penaltyType
    val color = penaltyColor(type)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RcShapes.Medium)
            .background(MaterialTheme.colorScheme.surface)
            .padding(Dimens.MD),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(Dimens.MD),
    ) {
        Icon(
            imageVector = penaltyIcon(type),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(24.dp),
        )
        Column(Modifier.weight(1f)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val lap = penalty.lap
                if (lap != null) {
                    Text(
                        text = stringResource(R.string.flag_lap_single, lap),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = RcTheme.colors.textPrimary,
                    )
                }
                TeamLogo(url = penalty.teamLogoUrl, size = 16.dp)
                val driverLabel = penalty.driverName ?: penalty.driverCode
                if (!driverLabel.isNullOrBlank()) {
                    Text(
                        text = driverLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (penalty.driverId != null) RcTheme.colors.info else RcTheme.colors.textSecondary,
                        modifier = Modifier
                            .clip(RcShapes.Small)
                            .background(RcTheme.colors.surfaceElevated)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                            .clickable(enabled = penalty.driverId != null) {
                                onOpenDriver(penalty.driverId ?: return@clickable)
                            },
                    )
                }
                val timeText = remember(penalty.time) { FlexibleDate.parse(penalty.time)?.formatTimeWithZone() }
                if (timeText != null) {
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = RcTheme.colors.textTertiary,
                    )
                }
            }
            Text(
                text = penalty.value?.let { "${penalty.type} ($it)" } ?: penalty.type,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = color,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clip(RcShapes.Small)
                    .background(color.copy(alpha = 0.16f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
            Text(
                text = penalty.reason ?: penalty.message ?: "–",
                style = MaterialTheme.typography.bodyMedium,
                color = RcTheme.colors.textPrimary,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
