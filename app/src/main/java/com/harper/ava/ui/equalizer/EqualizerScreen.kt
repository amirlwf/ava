package com.harper.ava.ui.equalizer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harper.ava.AvaApplication
import com.harper.ava.R
import com.harper.ava.ui.components.EmptyState
import com.harper.ava.ui.settings.SettingsNote
import com.harper.ava.ui.settings.SettingsSectionHeader
import com.harper.ava.ui.settings.SettingsSpacer
import com.harper.ava.ui.settings.SettingsSwitchRow
import com.harper.ava.ui.theme.AvaTokens
import com.harper.ava.ui.theme.INACTIVE_TRACK_ALPHA

/**
 * Equalizer: device presets + manual band sliders, driven by Android's
 * Equalizer audio effect on ExoPlayer's audio session.
 *
 * Honesty rule: when the device has no equalizer (or no song has played
 * yet, so there is no audio session), the screen says so plainly instead
 * of showing fake sliders. Bands and presets come from the device itself.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EqualizerScreen(app: AvaApplication, onBack: () -> Unit) {
    val viewModel: EqualizerViewModel = viewModel(
        factory = EqualizerViewModel.factory(app.container.playerRepository),
    )
    val eq by viewModel.equalizer.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.eq_title)) },
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
        if (!eq.supported) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(
                    title = stringResource(R.string.eq_unsupported_title),
                    body = stringResource(
                        when (eq.reasonKey) {
                            "eq_reason_no_session" -> R.string.eq_reason_no_session
                            else -> R.string.eq_reason_unsupported
                        },
                    ),
                )
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SettingsSwitchRow(
                title = stringResource(R.string.eq_enable),
                subtitle = stringResource(R.string.eq_enable_sub),
                checked = eq.enabled,
                onCheckedChange = { viewModel.setEnabled(it) },
            )

            if (eq.enabled) {
                if (eq.presets.isNotEmpty()) {
                    SettingsSectionHeader(stringResource(R.string.eq_presets_title))
                    FlowRow(
                        modifier = Modifier.padding(horizontal = AvaTokens.space20),
                        horizontalArrangement = Arrangement.spacedBy(AvaTokens.space8),
                        verticalArrangement = Arrangement.spacedBy(AvaTokens.space8),
                    ) {
                        eq.presets.forEachIndexed { index, name ->
                            FilterChip(
                                selected = eq.presetIndex == index,
                                onClick = { viewModel.applyPreset(index) },
                                label = { Text(name) },
                            )
                        }
                        if (eq.presetIndex == -1) {
                            // Status indicator, not a button: manual bands are active.
                            FilterChip(
                                selected = true,
                                enabled = false,
                                onClick = {},
                                label = { Text(stringResource(R.string.eq_custom)) },
                            )
                        }
                    }
                }

                SettingsSectionHeader(stringResource(R.string.eq_bands_title))
                eq.centerFreqsHz.forEachIndexed { band, freqHz ->
                    val level = eq.bandLevelsDb.getOrNull(band) ?: 0f
                    Column(
                        modifier = Modifier.padding(
                            horizontal = AvaTokens.space20,
                            vertical = AvaTokens.space4,
                        ),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = formatFreq(freqHz),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = formatDb(level),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Slider(
                            value = level,
                            onValueChange = { viewModel.setBand(band, it) },
                            valueRange = eq.minLevelDb..eq.maxLevelDb,
                            modifier = Modifier.fillMaxWidth(),
                            // 0.2-fix (Track 2): the M3 default track
                            // is surfaceVariant — invisible on pure
                            // black in AMOLED. An explicit onSurface
                            // track at INACTIVE_TRACK_ALPHA stays
                            // visible.
                            colors = SliderDefaults.colors(
                                inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = INACTIVE_TRACK_ALPHA),
                            ),
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AvaTokens.space20, vertical = AvaTokens.space8),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Button(onClick = { viewModel.resetBands() }) {
                        Text(stringResource(R.string.eq_reset))
                    }
                }
                SettingsNote(stringResource(R.string.eq_note))
            } else {
                SettingsNote(stringResource(R.string.eq_enable_sub))
            }
            SettingsSpacer()
        }
    }
}

/** 60 -> "60 Hz", 1200 -> "1.2 kHz", 16000 -> "16 kHz". */
private fun formatFreq(freqHz: Int): String =
    if (freqHz < 1000) "$freqHz Hz"
    else {
        val khz = freqHz / 1000f
        val text = if (khz % 1f == 0f) "${khz.toInt()}" else "%.1f".format(khz)
        "$text kHz"
    }

/** +3.0 dB / -2.5 dB / 0.0 dB. */
private fun formatDb(levelDb: Float): String =
    "${if (levelDb > 0f) "+" else ""}%.1f dB".format(levelDb)
