package com.owlmedia.racecontrol.feature.analysis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.owlmedia.racecontrol.core.design.MonoFamily
import com.owlmedia.racecontrol.core.design.RcShapes
import com.owlmedia.racecontrol.core.design.RcTheme
import com.owlmedia.racecontrol.core.design.legibleOnSurface
import com.owlmedia.racecontrol.core.design.tabular
import com.owlmedia.racecontrol.core.design.teamColor
import com.owlmedia.racecontrol.core.ui.EmptyState
import com.owlmedia.racecontrol.core.ui.LoadableContent
import com.owlmedia.racecontrol.core.ui.PositionBadge
import com.owlmedia.racecontrol.core.ui.RcDetailScaffold
import com.owlmedia.racecontrol.core.ui.SectionHeader
import com.owlmedia.racecontrol.core.ui.TeamAccentBar
import com.owlmedia.racecontrol.core.ui.UiState
import com.owlmedia.racecontrol.data.remote.dto.ResultEntryDto
import com.owlmedia.racecontrol.data.remote.dto.SessionResultsDto
import com.owlmedia.racecontrol.data.repository.RaceControlRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class QualifyingViewModel @Inject constructor(
    private val repository: RaceControlRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<SessionResultsDto>>(UiState.Idle)
    val state: StateFlow<UiState<SessionResultsDto>> = _state.asStateFlow()

    fun load(year: Int, round: Int) {
        if (_state.value is UiState.Loaded) return
        viewModelScope.launch {
            _state.value = UiState.Loading
            repository.results(year, round, "Q")
                .onSuccess { _state.value = UiState.Loaded(it) }
                .onFailure { _state.value = UiState.Failed(repository.messageFor(it)) }
        }
    }
}

@Composable
fun QualifyingScreen(
    year: Int,
    round: Int,
    title: String,
    onBack: () -> Unit,
    viewModel: QualifyingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(year, round) { viewModel.load(year, round) }

    RcDetailScaffold(
        title = stringResource(R.string.analysis_qualifying),
        onBack = onBack,
    ) { modifier ->
        LoadableContent(
            state = state,
            onRetry = { viewModel.load(year, round) },
            modifier = modifier,
        ) { data ->
            if (data.results.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.Timer,
                    title = stringResource(R.string.no_qualifying_title),
                    message = stringResource(R.string.no_qualifying_message),
                )
                return@LoadableContent
            }

            val poleMillis = remember(data) {
                data.results.firstOrNull()?.let { parseLapTime(it.bestQualifyingTime) }
            }

            LazyColumn(
                contentPadding = PaddingValues(Dimens.MD),
                verticalArrangement = Arrangement.spacedBy(Dimens.SM),
            ) {
                item(key = "header") {
                    Column {
                        Text(
                            text = data.eventName ?: title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = RcTheme.colors.textSecondary,
                        )
                        SectionHeader(stringResource(R.string.gap_to_pole))
                    }
                }
                items(
                    items = data.results,
                    key = { it.id },
                    contentType = { "qualifying" },
                ) { entry ->
                    QualifyingRow(entry = entry, poleMillis = poleMillis)
                }
            }
        }
    }
}

@Composable
private fun QualifyingRow(entry: ResultEntryDto, poleMillis: Long?) {
    val accent = teamColor(entry.teamColor).legibleOnSurface()
    val best = entry.bestQualifyingTime
    val gap = remember(best, poleMillis) {
        val ms = parseLapTime(best)
        if (ms == null || poleMillis == null || ms == poleMillis) null
        else String.format(java.util.Locale.US, "+%.3f", (ms - poleMillis) / 1000.0)
    }

    // Which segment the driver was eliminated in, shown as a quiet label rather
    // than a colour-only cue.
    val eliminatedIn = when {
        entry.q3 != null -> null
        entry.q2 != null -> "Q3"
        entry.q1 != null -> "Q2"
        else -> null
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RcShapes.Medium)
            .background(MaterialTheme.colorScheme.surface)
            .padding(Dimens.SM),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SM),
    ) {
        PositionBadge(text = entry.positionLabel, highlight = entry.isPodium)
        TeamAccentBar(color = accent, height = 36.dp)
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.fullName ?: entry.abbreviation.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = RcTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SM)) {
                SegmentTime("Q1", entry.q1, entry.q1Gap)
                SegmentTime("Q2", entry.q2, entry.q2Gap)
                SegmentTime("Q3", entry.q3, entry.q3Gap)
            }
            eliminatedIn?.let {
                Text(
                    text = "Out in $it",
                    style = MaterialTheme.typography.labelSmall,
                    color = RcTheme.colors.textTertiary,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = best ?: "–",
                style = MaterialTheme.typography.bodyMedium.tabular(),
                fontFamily = MonoFamily,
                fontWeight = FontWeight.SemiBold,
                color = RcTheme.colors.textPrimary,
            )
            gap?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall.tabular(),
                    fontFamily = MonoFamily,
                    color = RcTheme.colors.racingRedText,
                )
            }
        }
    }
}

@Composable
private fun SegmentTime(label: String, time: String?, gap: String?) {
    Column {
        Text(
            text = "$label ${time ?: "–"}",
            style = MaterialTheme.typography.labelSmall.tabular(),
            color = if (time == null) RcTheme.colors.textTertiary else RcTheme.colors.textSecondary,
        )
        if (gap != null) {
            Text(
                text = gap,
                style = MaterialTheme.typography.labelSmall.tabular(),
                color = RcTheme.colors.textTertiary,
            )
        }
    }
}

/**
 * Parses "1:23.456" or "23.456" into milliseconds.
 *
 * Qualifying times arrive as pre-formatted strings, not numbers, so computing a
 * gap means reading them back.
 */
internal fun parseLapTime(value: String?): Long? {
    if (value.isNullOrBlank()) return null
    val parts = value.trim().split(":")
    return try {
        when (parts.size) {
            1 -> (parts[0].toDouble() * 1000).toLong()
            2 -> (parts[0].toLong() * 60_000) + (parts[1].toDouble() * 1000).toLong()
            3 -> (parts[0].toLong() * 3_600_000) +
                (parts[1].toLong() * 60_000) +
                (parts[2].toDouble() * 1000).toLong()
            else -> null
        }
    } catch (e: NumberFormatException) {
        null
    }
}
