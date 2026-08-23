package com.owlmedia.racecontrol.feature.teams

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.owlmedia.racecontrol.core.design.RcShapes
import com.owlmedia.racecontrol.core.design.RcTheme
import com.owlmedia.racecontrol.core.design.legibleOnSurface
import com.owlmedia.racecontrol.core.design.tabular
import com.owlmedia.racecontrol.core.design.teamColor
import com.owlmedia.racecontrol.core.ui.DriverAvatar
import com.owlmedia.racecontrol.core.ui.EmptyState
import com.owlmedia.racecontrol.core.ui.FavoriteStar
import com.owlmedia.racecontrol.core.ui.LoadableContent
import com.owlmedia.racecontrol.core.ui.PointsPill
import com.owlmedia.racecontrol.core.ui.RcTabScaffold
import com.owlmedia.racecontrol.core.ui.SeasonPickerChip
import com.owlmedia.racecontrol.core.ui.TeamAccentBar
import com.owlmedia.racecontrol.core.ui.TeamLogo
import com.owlmedia.racecontrol.core.ui.UiState
import com.owlmedia.racecontrol.data.local.FavoritesStore
import com.owlmedia.racecontrol.data.remote.dto.TeamDto
import com.owlmedia.racecontrol.data.repository.RaceControlRepository
import com.owlmedia.racecontrol.feature.AppState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class TeamsViewModel @Inject constructor(
    private val repository: RaceControlRepository,
    private val favorites: FavoritesStore,
) : ViewModel() {

    private val _raw = MutableStateFlow<UiState<List<TeamDto>>>(UiState.Idle)
    private var loadedYear: Int? = null

    val state: StateFlow<UiState<List<TeamDto>>> =
        combine(_raw, favorites.teamIds) { state, favouriteIds ->
            if (state !is UiState.Loaded) return@combine state
            UiState.Loaded(
                state.value.sortedWith(
                    compareByDescending<TeamDto> { it.id in favouriteIds }
                        .thenBy { it.positionInt ?: Int.MAX_VALUE }
                ),
                state.fromCache,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Idle)

    val favoriteIds: StateFlow<Set<String>> =
        favorites.teamIds.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun load(year: Int, force: Boolean = false) {
        if (!force && loadedYear == year && _raw.value is UiState.Loaded) return
        viewModelScope.launch {
            _raw.value = UiState.Loading
            repository.teams(year)
                .onSuccess {
                    _raw.value = UiState.Loaded(it)
                    loadedYear = year
                }
                .onFailure { _raw.value = UiState.Failed(repository.messageFor(it)) }
        }
    }

    fun toggleFavorite(teamId: String) {
        viewModelScope.launch { favorites.toggleTeam(teamId) }
    }
}

@Composable
fun TeamsScreen(
    appState: AppState,
    onSelectYear: (Int) -> Unit,
    onOpenTeam: (String) -> Unit,
    viewModel: TeamsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteIds.collectAsStateWithLifecycle()

    LaunchedEffect(appState.selectedYear) { viewModel.load(appState.selectedYear) }

    RcTabScaffold(
        title = stringResource(R.string.tab_teams),
        actions = {
            SeasonPickerChip(
                seasons = appState.seasons,
                selected = appState.selectedYear,
                onSelect = onSelectYear,
            )
            Spacer(Modifier.width(Dimens.SM))
        },
    ) { modifier ->
        LoadableContent(
            state = state,
            onRetry = { viewModel.load(appState.selectedYear, force = true) },
            modifier = modifier,
        ) { teams ->
            if (teams.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.DirectionsCar,
                    title = stringResource(R.string.no_teams_title),
                    message = stringResource(
                        R.string.no_teams_message,
                        appState.selectedYear.toString(),
                    ),
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(Dimens.MD),
                    verticalArrangement = Arrangement.spacedBy(Dimens.SM),
                ) {
                    items(
                        items = teams,
                        key = { it.id },
                        contentType = { "team" },
                    ) { team ->
                        TeamRow(
                            team = team,
                            isFavorite = team.id in favorites,
                            onToggleFavorite = { viewModel.toggleFavorite(team.id) },
                            onClick = { onOpenTeam(team.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun TeamRow(
    team: TeamDto,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit,
) {
    val accent = teamColor(team.teamColor).legibleOnSurface()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RcShapes.Medium)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(Dimens.SM),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SM),
        ) {
            team.positionInt?.let {
                Text(
                    text = it.toString(),
                    style = MaterialTheme.typography.titleMedium.tabular(),
                    fontWeight = FontWeight.Bold,
                    color = RcTheme.colors.textSecondary,
                    modifier = Modifier.width(24.dp),
                )
            }
            TeamAccentBar(color = accent, height = 36.dp)
            TeamLogo(url = team.teamLogoUrl, size = 28.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    text = team.teamName.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = RcTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = team.nationality.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = RcTheme.colors.textSecondary,
                )
            }
            PointsPill(points = team.pointsString)
            FavoriteStar(isFavorite = isFavorite, onToggle = onToggleFavorite)
        }

        if (team.drivers.isNotEmpty()) {
            Row(
                modifier = Modifier.padding(top = Dimens.SM),
                horizontalArrangement = Arrangement.spacedBy(Dimens.MD),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                team.drivers.forEach { driver ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        DriverAvatar(
                            url = driver.headshotUrl,
                            initials = driver.initials,
                            accent = accent,
                            size = 28.dp,
                        )
                        Text(
                            text = driver.name.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = RcTheme.colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
