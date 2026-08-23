package com.owlmedia.racecontrol.feature.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.owlmedia.racecontrol.BuildConfig
import com.owlmedia.racecontrol.R
import com.owlmedia.racecontrol.core.design.Dimens
import com.owlmedia.racecontrol.core.design.RcTheme
import com.owlmedia.racecontrol.core.ui.RcCard
import com.owlmedia.racecontrol.core.ui.RcDetailScaffold
import com.owlmedia.racecontrol.core.ui.SectionHeader
import com.owlmedia.racecontrol.data.local.SettingsDataStore
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val token by viewModel.token.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var urlDraft by remember(settings.baseUrl) { mutableStateOf(settings.baseUrl) }
    var tokenDraft by remember(token) { mutableStateOf(token) }
    var tokenVisible by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var showExactAlarmDialog by remember { mutableStateOf(false) }

    // API 33+ needs an explicit grant before a single reminder can be posted.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.setNotificationsEnabled(true)
            if (!viewModel.canScheduleExactAlarms()) showExactAlarmDialog = true
        } else {
            viewModel.setNotificationsEnabled(false)
            showPermissionDialog = true
        }
    }

    RcDetailScaffold(title = stringResource(R.string.settings), onBack = onBack) { modifier ->
        Column(
            modifier = modifier
                .verticalScroll(rememberScrollState())
                .padding(Dimens.MD),
            verticalArrangement = Arrangement.spacedBy(Dimens.MD),
        ) {
            /* ------------------------------------------- backend server */
            RcCard {
                SectionHeader(stringResource(R.string.backend_server))
                OutlinedTextField(
                    value = urlDraft,
                    onValueChange = {
                        urlDraft = it
                        viewModel.setBaseUrl(it)
                    },
                    label = { Text(stringResource(R.string.backend_url_label)) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        capitalization = KeyboardCapitalization.None,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Dimens.SM),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    OutlinedButton(onClick = viewModel::testConnection) {
                        Icon(Icons.Filled.WifiTethering, contentDescription = null)
                        Text(
                            text = stringResource(R.string.test_connection),
                            modifier = Modifier.padding(start = Dimens.SM),
                        )
                    }
                    ConnectionStatusIndicator(status)
                }

                (status as? ConnectionStatus.Failed)?.let {
                    Text(
                        text = it.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = RcTheme.colors.negative,
                        modifier = Modifier.padding(top = Dimens.XS),
                    )
                }

                TextButton(
                    onClick = {
                        viewModel.resetBaseUrl()
                        urlDraft = com.owlmedia.racecontrol.data.local
                            .SettingsDataStore.DEFAULT_BASE_URL
                    },
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Text(
                        text = stringResource(R.string.reset_to_default),
                        modifier = Modifier.padding(start = Dimens.SM),
                    )
                }

                Text(
                    text = stringResource(R.string.backend_footer),
                    style = MaterialTheme.typography.bodySmall,
                    color = RcTheme.colors.textTertiary,
                )
            }

            /* ------------------------------------------------- security */
            RcCard {
                SectionHeader(stringResource(R.string.security))
                Text(
                    text = stringResource(R.string.security_footer),
                    style = MaterialTheme.typography.bodySmall,
                    color = RcTheme.colors.textSecondary,
                )
                HorizontalDivider(
                    color = RcTheme.colors.stroke,
                    modifier = Modifier.padding(vertical = Dimens.SM),
                )
                OutlinedTextField(
                    value = tokenDraft,
                    onValueChange = {
                        tokenDraft = it
                        viewModel.setToken(it)
                    },
                    label = { Text(stringResource(R.string.api_token_label)) },
                    singleLine = true,
                    visualTransformation = if (tokenVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = { tokenVisible = !tokenVisible }) {
                            Icon(
                                imageVector = if (tokenVisible) Icons.Filled.VisibilityOff
                                else Icons.Filled.Visibility,
                                contentDescription = stringResource(
                                    if (tokenVisible) R.string.api_token_hide
                                    else R.string.api_token_show
                                ),
                            )
                        }
                    },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        capitalization = KeyboardCapitalization.None,
                        imeAction = ImeAction.Done,
                    ),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.api_token_footer),
                    style = MaterialTheme.typography.bodySmall,
                    color = RcTheme.colors.textTertiary,
                    modifier = Modifier.padding(top = Dimens.XS),
                )
            }

            /* -------------------------------------------- notifications */
            RcCard {
                SectionHeader(stringResource(R.string.notifications))
                SettingSwitch(
                    label = stringResource(R.string.race_reminders),
                    checked = settings.notificationsEnabled,
                    onCheckedChange = { enabled ->
                        if (!enabled) {
                            viewModel.setNotificationsEnabled(false)
                            return@SettingSwitch
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            viewModel.setNotificationsEnabled(true)
                            if (!viewModel.canScheduleExactAlarms()) showExactAlarmDialog = true
                        }
                    },
                )
                Text(
                    text = stringResource(R.string.notifications_footer),
                    style = MaterialTheme.typography.bodySmall,
                    color = RcTheme.colors.textTertiary,
                )
            }

            if (settings.notificationsEnabled) {
                RcCard {
                    SectionHeader(stringResource(R.string.remind_me))
                    SettingSwitch(
                        stringResource(R.string.notify_day_before),
                        settings.notifyDayBefore,
                        viewModel::setNotifyDayBefore,
                    )
                    SettingSwitch(
                        stringResource(R.string.notify_1_hour),
                        settings.notify1Hour,
                        viewModel::setNotify1Hour,
                    )
                    SettingSwitch(
                        stringResource(R.string.notify_15_min),
                        settings.notify15Min,
                        viewModel::setNotify15Min,
                    )
                }

                RcCard {
                    SectionHeader(stringResource(R.string.for_these_sessions))
                    SettingSwitch(
                        stringResource(R.string.session_practice),
                        settings.notifyPractice,
                        viewModel::setNotifyPractice,
                    )
                    SettingSwitch(
                        stringResource(R.string.session_qualifying),
                        settings.notifyQualifying,
                        viewModel::setNotifyQualifying,
                    )
                    SettingSwitch(
                        stringResource(R.string.session_sprint_label),
                        settings.notifySprint,
                        viewModel::setNotifySprint,
                    )
                    SettingSwitch(
                        stringResource(R.string.session_race_label),
                        settings.notifyRace,
                        viewModel::setNotifyRace,
                    )
                }
            }

            /* ---------------------------------------------------- about */
            RcCard {
                SectionHeader(stringResource(R.string.about))
                AboutRow(
                    stringResource(R.string.about_data_label),
                    stringResource(R.string.about_data),
                )
                AboutRow(
                    stringResource(R.string.about_app_label),
                    "RaceControl ${BuildConfig.VERSION_NAME}",
                )
            }
        }
    }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text(stringResource(R.string.notifications_denied_title)) },
            text = { Text(stringResource(R.string.notifications_denied_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionDialog = false
                    context.startActivity(
                        Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = android.net.Uri.fromParts("package", context.packageName, null)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    )
                }) {
                    Text(stringResource(R.string.open_android_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showExactAlarmDialog) {
        AlertDialog(
            onDismissRequest = { showExactAlarmDialog = false },
            title = { Text(stringResource(R.string.exact_alarms_title)) },
            text = { Text(stringResource(R.string.exact_alarms_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showExactAlarmDialog = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        runCatching {
                            context.startActivity(
                                Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }
                }) {
                    Text(stringResource(R.string.open_android_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = { showExactAlarmDialog = false }) {
                    Text(stringResource(R.string.ok))
                }
            },
        )
    }
}

@Composable
private fun SettingSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.XS),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = RcTheme.colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.XS),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = RcTheme.colors.textSecondary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = RcTheme.colors.textPrimary,
        )
    }
}

@Composable
private fun ConnectionStatusIndicator(status: ConnectionStatus) {
    when (status) {
        ConnectionStatus.Unknown -> Unit
        ConnectionStatus.Checking -> CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
        )
        ConnectionStatus.Ok -> Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = stringResource(R.string.connection_ok),
            tint = RcTheme.colors.positive,
        )
        is ConnectionStatus.Failed -> Icon(
            imageVector = Icons.Filled.Cancel,
            contentDescription = status.message,
            tint = RcTheme.colors.negative,
        )
    }
}
