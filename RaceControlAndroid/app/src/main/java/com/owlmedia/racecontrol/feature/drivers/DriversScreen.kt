package com.owlmedia.racecontrol.feature.drivers

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.owlmedia.racecontrol.data.remote.dto.DriverDto
import com.owlmedia.racecontrol.feature.AppState

@Composable
fun DriversScreen(
    appState: AppState,
    onSelectYear: (Int) -> Unit,
    onOpenDriver: (String) -> Unit,
    onOpenHeadToHead: () -> Unit,
    viewModel: DriversViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteIds.collectAsStateWithLifecycle()

    LaunchedEffect(appState.selectedYear) { viewModel.load(appState.selectedYear) }

    RcTabScaffold(
        title = stringResource(R.string.tab_drivers),
        actions = {
            IconButton(onClick = onOpenHeadToHead) {
                Icon(
                    imageVector = Icons.Filled.CompareArrows,
                    contentDescription = stringResource(R.string.head_to_head),
                )
            }
            SeasonPickerChip(
                seasons = appState.seasons,
                selected = appState.selectedYear,
                onSelect = onSelectYear,
            )
            Spacer(Modifier.width(Dimens.SM))
        },
    ) { modifier ->
        Column(modifier) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::setQuery,
                label = { Text(stringResource(R.string.search_drivers)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = ImeAction.Search,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.MD, vertical = Dimens.SM),
            )

            LoadableContent(
                state = state,
                onRetry = { viewModel.load(appState.selectedYear, force = true) },
            ) { drivers ->
                when {
                    drivers.isEmpty() && query.isNotBlank() -> EmptyState(
                        icon = Icons.Filled.Search,
                        title = stringResource(R.string.no_search_results_title),
                        message = stringResource(R.string.no_search_results_message, query),
                    )
                    drivers.isEmpty() -> EmptyState(
                        icon = Icons.Filled.Groups,
                        title = stringResource(R.string.no_drivers_title),
                        message = stringResource(
                            R.string.no_drivers_message,
                            appState.selectedYear.toString(),
                        ),
                    )
                    else -> LazyColumn(
                        contentPadding = PaddingValues(Dimens.MD),
                        verticalArrangement = Arrangement.spacedBy(Dimens.SM),
                    ) {
                        items(
                            items = drivers,
                            key = { it.driverId },
                            contentType = { "driver" },
                        ) { driver ->
                            DriverRow(
                                driver = driver,
                                isFavorite = driver.driverId in favorites,
                                onToggleFavorite = { viewModel.toggleFavorite(driver.driverId) },
                                onClick = { onOpenDriver(driver.driverId) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DriverRow(
    driver: DriverDto,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit,
) {
    val accent = teamColor(driver.teamColor).legibleOnSurface()

    // Two lines rather than one: cramming position + accent bar + avatar +
    // name + number + points pill + favourite star into a single Row left
    // the name Text competing for whatever width survived every fixed-width
    // sibling, on a phone-width screen that was often under 100dp, so long
    // driver names ellipsized after a handful of characters. Name (+
    // favourite) now gets its own top line; number/team/points share a
    // second line below, where truncation is far less noticeable.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RcShapes.Medium)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = Dimens.SM, vertical = Dimens.SM),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SM),
    ) {
        driver.positionInt?.let {
            Text(
                text = it.toString(),
                style = MaterialTheme.typography.titleMedium.tabular(),
                fontWeight = FontWeight.Bold,
                color = RcTheme.colors.textSecondary,
                modifier = Modifier.width(24.dp).padding(top = 2.dp),
            )
        }
        TeamAccentBar(color = accent, height = 56.dp)
        DriverAvatar(
            url = driver.headshotUrl,
            initials = driver.initials,
            accent = accent,
            size = 44.dp,
        )
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = driver.fullName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = RcTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.weight(1f))
                FavoriteStar(isFavorite = isFavorite, onToggle = onToggleFavorite)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                driver.numberString?.let {
                    Text(
                        text = "#$it",
                        style = MaterialTheme.typography.labelMedium.tabular(),
                        color = accent,
                    )
                }
                TeamLogo(url = driver.teamLogoUrl, size = 14.dp)
                Text(
                    text = driver.teamName.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = RcTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.weight(1f))
                PointsPill(points = driver.pointsString)
            }
        }
    }
}
