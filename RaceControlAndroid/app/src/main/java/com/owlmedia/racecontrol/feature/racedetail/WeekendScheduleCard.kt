package com.owlmedia.racecontrol.feature.racedetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.owlmedia.racecontrol.R
import com.owlmedia.racecontrol.core.design.Dimens
import com.owlmedia.racecontrol.core.design.RcTheme
import com.owlmedia.racecontrol.core.design.tabular
import com.owlmedia.racecontrol.core.ui.RcCard
import com.owlmedia.racecontrol.core.ui.SectionHeader
import com.owlmedia.racecontrol.core.util.formatWeekdayTime
import com.owlmedia.racecontrol.data.remote.dto.RaceEventDto

/**
 * The weekend running order: every session with its start time converted to the
 * device's timezone, which is the whole point of showing it.
 */
@Composable
fun WeekendScheduleCard(event: RaceEventDto, modifier: Modifier = Modifier) {
    val sessions = event.sessions.filter { it.parsedDate != null }
    if (sessions.isEmpty()) return

    RcCard(modifier = modifier) {
        SectionHeader(stringResource(R.string.weekend_schedule))
        sessions.forEachIndexed { index, session ->
            val start = session.parsedDate ?: return@forEachIndexed
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Dimens.SM)
                    .semantics {
                        contentDescription =
                            "${session.reminderName}, ${start.formatWeekdayTime()}"
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = session.reminderName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = RcTheme.colors.textPrimary,
                    )
                }
                Text(
                    text = start.formatWeekdayTime(),
                    style = MaterialTheme.typography.bodyMedium.tabular(),
                    color = RcTheme.colors.textSecondary,
                )
            }
            if (index != sessions.lastIndex) {
                HorizontalDivider(color = RcTheme.colors.stroke)
            }
        }
    }
}
