package com.owlmedia.racecontrol.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.owlmedia.racecontrol.R
import com.owlmedia.racecontrol.core.design.Dimens
import com.owlmedia.racecontrol.core.design.RcShapes
import com.owlmedia.racecontrol.core.design.RcTheme
import com.owlmedia.racecontrol.core.design.TyreCompound
import com.owlmedia.racecontrol.core.design.tabular

/* ---------------------------------------------------------------- season picker */

/**
 * Season selector shown in the app bar of every top-level tab.
 *
 * iOS uses a `Menu` in the navigation bar; the Material equivalent is a chip
 * that opens a dropdown anchored to itself.
 */
@Composable
fun SeasonPickerChip(
    seasons: List<Int>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        AssistChip(
            onClick = { expanded = true },
            label = {
                Text(
                    text = selected.toString(),
                    style = MaterialTheme.typography.labelLarge.tabular(),
                )
            },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Filled.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = RcTheme.colors.surfaceElevated,
                labelColor = RcTheme.colors.textPrimary,
                trailingIconContentColor = RcTheme.colors.textSecondary,
            ),
            modifier = Modifier.semantics {
                contentDescription = "Season, currently $selected"
            },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            seasons.forEach { year ->
                DropdownMenuItem(
                    text = { Text(year.toString()) },
                    onClick = {
                        onSelect(year)
                        expanded = false
                    },
                    trailingIcon = if (year == selected) {
                        { Icon(Icons.Filled.Star, contentDescription = null, tint = RcTheme.colors.racingRed) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

/* ---------------------------------------------------------------- driver avatar */

/**
 * Async headshot with a team-tinted initials fallback.
 *
 * Headshots come from the F1 media CDN and are frequently missing for older
 * seasons, so the fallback is the common case, not the exception.
 */
@Composable
fun DriverAvatar(
    url: String?,
    initials: String,
    accent: Color,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 48.dp,
) {
    val shape = CircleShape
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .border(1.5.dp, accent.copy(alpha = 0.5f), shape),
        contentAlignment = Alignment.Center,
    ) {
        val fallback: @Composable () -> Unit = {
            Box(
                modifier = Modifier
                    .size(size)
                    .background(accent.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = initials,
                    color = accent,
                    fontWeight = FontWeight.Bold,
                    fontSize = (size.value * 0.36f).sp,
                )
            }
        }

        if (url.isNullOrBlank()) {
            fallback()
        } else {
            SubcomposeAsyncImage(
                model = url,
                contentDescription = null, // the driver's name is always adjacent
                loading = { fallback() },
                error = { fallback() },
                modifier = Modifier.size(size),
            )
        }
    }
}

/* ------------------------------------------------------------------ team logo */

/**
 * Team logo with a graceful no-op fallback when the URL is missing or fails
 * to load. Neither FastF1 nor Ergast expose team logos, so the backend
 * serves these from a hardcoded mapping onto F1.com's own media CDN (see
 * `_team_logo_url`), one that needs manual upkeep on renames or new
 * entrants, so a broken URL should just render nothing rather than a
 * broken-image glyph. The source marks are white PNGs, so they sit on a
 * dark chip to stay legible against any background.
 */
@Composable
fun TeamLogo(
    url: String?,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
) {
    if (url.isNullOrBlank()) return
    SubcomposeAsyncImage(
        model = url,
        contentDescription = null, // the team name is always adjacent
        loading = {},
        error = {},
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black.copy(alpha = 0.8f))
            .padding(2.dp),
    )
}

/* ---------------------------------------------------------------- small pieces */

/** Vertical team-livery stripe used down the left of result rows. */
@Composable
fun TeamAccentBar(
    color: Color,
    modifier: Modifier = Modifier,
    width: androidx.compose.ui.unit.Dp = 4.dp,
    height: androidx.compose.ui.unit.Dp = 40.dp,
) {
    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(width / 2))
            .background(color),
    )
}

/** Finishing position; P1-P3 get the gold podium gradient. */
@Composable
fun PositionBadge(
    text: String,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
) {
    val podium = Brush.verticalGradient(
        listOf(RcTheme.colors.gold, RcTheme.colors.goldDeep),
    )
    Box(
        modifier = modifier
            .size(34.dp)
            .clip(RcShapes.Small)
            .then(
                if (highlight) {
                    Modifier.background(podium)
                } else {
                    Modifier.background(RcTheme.colors.surfaceElevated)
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall.tabular(),
            fontWeight = FontWeight.Bold,
            color = if (highlight) Color.Black else RcTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun PointsPill(points: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(RcTheme.colors.surfaceElevated)
            .padding(horizontal = Dimens.SM, vertical = Dimens.XS),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = points.ifEmpty { "0" },
            style = MaterialTheme.typography.labelLarge.tabular(),
            color = RcTheme.colors.textPrimary,
        )
    }
}

/**
 * Places gained or lost against the starting grid.
 *
 * Colour alone would fail WCAG 1.4.1, so direction is carried by the arrow icon
 * and the sign as well as green/red.
 */
@Composable
fun GridDeltaTag(delta: Int, modifier: Modifier = Modifier) {
    val (icon, tint, description) = when {
        delta > 0 -> Triple(
            Icons.Filled.ArrowUpward,
            RcTheme.colors.positive,
            stringResource(R.string.grid_delta_gained, delta),
        )
        delta < 0 -> Triple(
            Icons.Filled.ArrowDownward,
            RcTheme.colors.negative,
            stringResource(R.string.grid_delta_lost, -delta),
        )
        else -> Triple(
            Icons.Filled.Remove,
            RcTheme.colors.textTertiary,
            stringResource(R.string.grid_delta_none),
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.semantics { contentDescription = description },
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(12.dp))
        Text(
            text = if (delta == 0) "0" else kotlin.math.abs(delta).toString(),
            style = MaterialTheme.typography.labelSmall.tabular(),
            fontWeight = FontWeight.Bold,
            color = tint,
        )
    }
}

/** Circular tyre-compound badge in the official compound colour. */
@Composable
fun TyreBadge(
    compound: String?,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 26.dp,
) {
    val description = TyreCompound.contentDescription(compound)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(TyreCompound.color(compound))
            .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = TyreCompound.letter(compound),
            color = Color.Black,
            fontWeight = FontWeight.Black,
            fontSize = (size.value * 0.42f).sp,
        )
    }
}

/** The standard section container; iOS `Card`. */
@Composable
fun RcCard(
    modifier: Modifier = Modifier,
    contentPadding: androidx.compose.ui.unit.Dp = Dimens.MD,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RcShapes.Medium,
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, RcTheme.colors.stroke),
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content,
        )
    }
}

/** Big number over a small uppercase caption. */
@Composable
fun StatCell(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    accent: Color = RcTheme.colors.textPrimary,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = "$label: $value"
        },
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.tabular(),
            fontWeight = FontWeight.Bold,
            color = accent,
        )
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = RcTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Favourite toggle.
 *
 * The visual star is 22dp but the touch target is a full 48dp `IconButton`,
 * which is the Android minimum (iOS uses 44).
 */
@Composable
fun FavoriteStar(
    isFavorite: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(
        if (isFavorite) R.string.favourite_remove else R.string.favourite_add
    )
    IconButton(
        onClick = onToggle,
        modifier = modifier
            .size(Dimens.MinTouch)
            .semantics { stateDescription = if (isFavorite) "Favourite" else "Not a favourite" },
    ) {
        Icon(
            imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
            contentDescription = label,
            tint = if (isFavorite) RcTheme.colors.gold else RcTheme.colors.textTertiary,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** Small uppercase section heading used above card groups. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = RcTheme.colors.textSecondary,
        modifier = modifier.padding(vertical = Dimens.XS),
    )
}
