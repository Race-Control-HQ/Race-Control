package com.owlmedia.racecontrol.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.owlmedia.racecontrol.R
import com.owlmedia.racecontrol.core.design.Dimens
import com.owlmedia.racecontrol.core.design.RcTheme
import kotlinx.coroutines.delay

/**
 * Renders the right thing for a [UiState], with a built-in retry on failure.
 *
 * The Compose counterpart of iOS `LoadableView`. Having exactly one of these
 * is what makes "every screen has a loading, error and empty state" true by
 * construction rather than by discipline.
 */
@Composable
fun <T> LoadableContent(
    state: UiState<T>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    loadingLabel: String = stringResource(R.string.loading),
    content: @Composable (T) -> Unit,
) {
    when (state) {
        is UiState.Idle, is UiState.Loading -> LoadingIndicator(modifier, loadingLabel)
        is UiState.Failed -> ErrorState(state.message, onRetry, modifier)
        is UiState.Loaded -> Column(modifier) {
            if (state.fromCache) CachedDataBanner()
            content(state.value)
        }
    }
}

@Composable
fun LoadingIndicator(
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.loading),
) {
    // FastF1 downloads and caches a session on first request, which can take
    // the best part of a minute. Silence for that long reads as "broken", so
    // after 8 seconds we explain what is happening.
    var slow by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(8_000)
        slow = true
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .semantics { liveRegion = LiveRegionMode.Polite },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.MD),
        ) {
            CircularProgressIndicator(color = RcTheme.colors.racingRed)
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = RcTheme.colors.textSecondary,
            )
            if (slow) {
                Text(
                    text = stringResource(R.string.slow_first_load),
                    style = MaterialTheme.typography.bodySmall,
                    color = RcTheme.colors.textTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = Dimens.XL),
                )
            }
        }
    }
}

@Composable
fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(Dimens.MD),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.MD),
        ) {
            Icon(
                imageVector = Icons.Filled.WarningAmber,
                contentDescription = null,
                tint = RcTheme.colors.warning,
                modifier = Modifier.size(40.dp),
            )
            Text(
                text = stringResource(R.string.error_title),
                style = MaterialTheme.typography.titleMedium,
                color = RcTheme.colors.textPrimary,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = RcTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = Dimens.LG),
            )
            Button(
                onClick = onRetry,
                modifier = Modifier.defaultMinSize(minHeight = Dimens.MinTouch),
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Text(
                    text = stringResource(R.string.try_again),
                    modifier = Modifier.padding(start = Dimens.SM),
                )
            }
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(Dimens.MD),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.SM),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = RcTheme.colors.textTertiary,
                modifier = Modifier.size(40.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = RcTheme.colors.textPrimary,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = RcTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = Dimens.LG),
            )
        }
    }
}

/**
 * Shown above cached content when the network call failed but OkHttp had a
 * usable stale response. The iOS app has no cache; on Android, connectivity is
 * variable enough that showing yesterday's schedule beats showing an error.
 */
@Composable
fun CachedDataBanner(modifier: Modifier = Modifier) {
    Surface(
        color = RcTheme.colors.warning.copy(alpha = 0.14f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SM),
            modifier = Modifier.padding(horizontal = Dimens.MD, vertical = Dimens.SM),
        ) {
            Icon(
                imageVector = Icons.Filled.CloudOff,
                contentDescription = null,
                tint = RcTheme.colors.warning,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = stringResource(R.string.showing_cached),
                style = MaterialTheme.typography.bodySmall,
                color = RcTheme.colors.warning,
            )
        }
    }
}
