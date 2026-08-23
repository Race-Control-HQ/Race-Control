package com.owlmedia.racecontrol.feature.analysis

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.owlmedia.racecontrol.R
import com.owlmedia.racecontrol.core.design.Dimens
import com.owlmedia.racecontrol.core.design.RcShapes
import com.owlmedia.racecontrol.core.design.RcTheme
import com.owlmedia.racecontrol.core.design.legibleOnSurface
import com.owlmedia.racecontrol.core.design.teamColor
import com.owlmedia.racecontrol.core.ui.EmptyState
import com.owlmedia.racecontrol.core.ui.LoadableContent
import com.owlmedia.racecontrol.core.ui.RcDetailScaffold
import com.owlmedia.racecontrol.core.ui.SectionHeader
import com.owlmedia.racecontrol.core.ui.TeamAccentBar
import com.owlmedia.racecontrol.core.ui.TeamLogo
import com.owlmedia.racecontrol.core.ui.UiState
import com.owlmedia.racecontrol.data.remote.dto.RetirementCause
import com.owlmedia.racecontrol.data.remote.dto.RetirementDto
import com.owlmedia.racecontrol.data.remote.dto.RetirementsResponseDto
import com.owlmedia.racecontrol.data.repository.RaceControlRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class RetirementsViewModel @Inject constructor(
    private val repository: RaceControlRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<RetirementsResponseDto>>(UiState.Idle)
    val state: StateFlow<UiState<RetirementsResponseDto>> = _state.asStateFlow()

    fun load(year: Int, round: Int) {
        if (_state.value is UiState.Loaded) return
        viewModelScope.launch {
            _state.value = UiState.Loading
            repository.retirements(year, round)
                .onSuccess { _state.value = UiState.Loaded(it) }
                .onFailure { _state.value = UiState.Failed(repository.messageFor(it)) }
        }
    }
}

@Composable
fun RetirementsScreen(
    year: Int,
    round: Int,
    title: String,
    onBack: () -> Unit,
    onOpenDriver: (String) -> Unit = {},
    viewModel: RetirementsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(year, round) { viewModel.load(year, round) }

    RcDetailScaffold(
        title = stringResource(R.string.analysis_retirements),
        onBack = onBack,
    ) { modifier ->
        LoadableContent(
            state = state,
            onRetry = { viewModel.load(year, round) },
            modifier = modifier,
        ) { data ->
            if (data.retirements.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.CheckCircle,
                    title = stringResource(R.string.no_retirements_title),
                    message = stringResource(R.string.no_retirements_message),
                )
                return@LoadableContent
            }

            // Grouped by cause so the shape of a race - mechanical attrition vs
            // a first-lap pile-up - is visible at a glance.
            val grouped = remember(data) { data.retirements.groupBy { it.cause } }

            LazyColumn(
                contentPadding = PaddingValues(Dimens.MD),
                verticalArrangement = Arrangement.spacedBy(Dimens.SM),
            ) {
                RetirementCause.entries.forEach { cause ->
                    val entries = grouped[cause].orEmpty()
                    if (entries.isEmpty()) return@forEach

                    item(key = "header-$cause") {
                        SectionHeader(cause.label())
                    }
                    items(
                        items = entries,
                        key = { "${cause.name}-${it.id}" },
                        contentType = { "retirement" },
                    ) { retirement ->
                        RetirementRow(
                            retirement = retirement,
                            causeColor = cause.color(),
                            onOpenDriver = onOpenDriver,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RetirementCause.label(): String = stringResource(
    when (this) {
        RetirementCause.MECHANICAL -> R.string.cause_mechanical
        RetirementCause.ACCIDENT -> R.string.cause_accident
        RetirementCause.DISQUALIFIED -> R.string.cause_disqualified
        RetirementCause.OTHER -> R.string.cause_other
    }
)

@Composable
private fun RetirementCause.color(): Color = when (this) {
    RetirementCause.MECHANICAL -> RcTheme.colors.warning
    RetirementCause.ACCIDENT -> RcTheme.colors.negative
    RetirementCause.DISQUALIFIED -> RcTheme.colors.racingRedText
    RetirementCause.OTHER -> RcTheme.colors.textTertiary
}

@Composable
private fun RetirementRow(
    retirement: RetirementDto,
    causeColor: Color,
    onOpenDriver: (String) -> Unit,
) {
    val accent = teamColor(retirement.teamColor).legibleOnSurface()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RcShapes.Medium)
            .background(MaterialTheme.colorScheme.surface)
            .padding(Dimens.SM),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SM),
    ) {
        TeamAccentBar(color = accent, height = 36.dp)
        TeamLogo(url = retirement.teamLogoUrl, size = 20.dp)
        Column(Modifier.weight(1f)) {
            Text(
                text = retirement.fullName ?: retirement.driver.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (retirement.driverId != null) RcTheme.colors.info else RcTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable(enabled = retirement.driverId != null) {
                    onOpenDriver(retirement.driverId ?: return@clickable)
                },
            )
            Text(
                text = retirement.teamName.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = RcTheme.colors.textSecondary,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = retirement.status.orEmpty(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = causeColor,
            )
            retirement.lapsCompleted?.let { laps ->
                Text(
                    text = stringResource(R.string.retirement_lap, laps),
                    style = MaterialTheme.typography.labelSmall,
                    color = RcTheme.colors.textTertiary,
                )
            }
        }
    }
}
