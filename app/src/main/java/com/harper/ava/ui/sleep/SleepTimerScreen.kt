package com.harper.ava.ui.sleep

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harper.ava.AvaApplication
import com.harper.ava.R
import com.harper.ava.core.util.SLEEP_TIMER_MAX_MINUTES
import com.harper.ava.core.util.SLEEP_TIMER_MIN_MINUTES
import com.harper.ava.core.util.SLEEP_TIMER_PRESETS_MINUTES
import com.harper.ava.core.util.formatDuration
import com.harper.ava.ui.settings.SettingsNote
import com.harper.ava.ui.settings.SettingsSectionHeader
import com.harper.ava.ui.settings.SettingsSpacer
import com.harper.ava.ui.theme.AvaTokens

/**
 * Sleep Timer: ready-made durations, a custom duration (1–480 min), and
 * cancel. While a timer runs, the remaining time counts down live.
 * The timer lives in the PlayerRepository, so it keeps working when the
 * app is closed (playback is a foreground service) and the volume fades
 * out over the last 10 seconds before playback pauses.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SleepTimerScreen(app: AvaApplication, onBack: () -> Unit) {
    val viewModel: SleepTimerViewModel = viewModel(
        factory = SleepTimerViewModel.factory(app.container.playerRepository),
    )
    val timer by viewModel.sleepTimer.collectAsState()
    var customText by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sleep_timer_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(AvaTokens.space24))
            Icon(
                imageVector = Icons.Filled.Bedtime,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(AvaTokens.space8),
            )
            if (timer.active) {
                Text(
                    text = formatDuration(timer.remainingMs),
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.sleep_timer_remaining),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(AvaTokens.space16))
                OutlinedButton(onClick = { viewModel.cancelTimer() }) {
                    Text(stringResource(R.string.sleep_timer_cancel))
                }
            } else {
                Text(
                    text = stringResource(R.string.sleep_timer_pick),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = AvaTokens.space24),
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start,
            ) {
                SettingsSectionHeader(stringResource(R.string.sleep_timer_presets))
                FlowRow(
                    modifier = Modifier.padding(horizontal = AvaTokens.space20),
                    horizontalArrangement = Arrangement.spacedBy(AvaTokens.space8),
                    verticalArrangement = Arrangement.spacedBy(AvaTokens.space8),
                ) {
                    SLEEP_TIMER_PRESETS_MINUTES.forEach { minutes ->
                        FilterChip(
                            // Stage 6: the running timer's preset now shows
                            // as selected (it never did before).
                            selected = timer.active && timer.durationMinutes == minutes,
                            onClick = { viewModel.setTimer(minutes) },
                            label = { Text(stringResource(R.string.sleep_timer_minutes, minutes)) },
                        )
                    }
                }

                SettingsSectionHeader(stringResource(R.string.sleep_timer_custom_title))
                Column(modifier = Modifier.padding(horizontal = AvaTokens.space20)) {
                    OutlinedTextField(
                        value = customText,
                        onValueChange = { input ->
                            // Digits only; empty means "no custom value yet".
                            customText = input.filter { it.isDigit() }.take(3)
                        },
                        textStyle = MaterialTheme.typography.bodyLarge,
                        label = { Text(stringResource(R.string.sleep_timer_custom_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(AvaTokens.space12))
                    Button(
                        onClick = {
                            val minutes = customText.toIntOrNull() ?: return@Button
                            viewModel.setCustomTimer(minutes)
                            customText = ""
                        },
                        enabled = customText.toIntOrNull() != null,
                    ) {
                        Text(stringResource(R.string.sleep_timer_set))
                    }
                }
                SettingsNote(
                    stringResource(
                        R.string.sleep_timer_custom_hint,
                        SLEEP_TIMER_MIN_MINUTES,
                        SLEEP_TIMER_MAX_MINUTES,
                    ),
                )
                SettingsNote(stringResource(R.string.sleep_timer_fade_note))
            }
            SettingsSpacer()
        }
    }
}
