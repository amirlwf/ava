package com.harper.ava.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harper.ava.AvaApplication
import com.harper.ava.R
import com.harper.ava.core.util.SPEED_OPTIONS
import com.harper.ava.ui.theme.AvaTokens
import com.harper.ava.ui.theme.glassAwareScaffoldContainerColor
import com.harper.ava.ui.theme.glassAwareTopBarContainerColor

/**
 * Playback settings: default speed (applied when playback starts and
 * when changed here), resume-playback, and fade in/out.
 * The fade note is honest: the volume ramp only runs for the in-app
 * play/pause buttons — the notification and headset controls pause the
 * ExoPlayer directly and stop immediately.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlaybackSettingsScreen(app: AvaApplication, onBack: () -> Unit) {
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(
            app,
            app.container.userPreferences,
            app.container.musicRepository,
            app.container.playerRepository,
        ),
    )
    val settings by viewModel.settings.collectAsState()

    Scaffold(
        containerColor = glassAwareScaffoldContainerColor(),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = glassAwareTopBarContainerColor()),
                title = { Text(stringResource(R.string.playback_settings_title)) },
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
        ) {
            SettingsSectionHeader(stringResource(R.string.playback_speed_title))
            FlowRow(
                modifier = Modifier.padding(horizontal = AvaTokens.space20),
                horizontalArrangement = Arrangement.spacedBy(AvaTokens.space8),
                verticalArrangement = Arrangement.spacedBy(AvaTokens.space8),
            ) {
                SPEED_OPTIONS.forEach { speed ->
                    FilterChip(
                        selected = settings.defaultSpeed == speed,
                        onClick = { viewModel.setDefaultSpeed(speed) },
                        label = { Text(speedLabel(speed)) },
                    )
                }
            }
            SettingsNote(stringResource(R.string.playback_speed_sub))

            SettingsSwitchRow(
                title = stringResource(R.string.playback_resume),
                subtitle = stringResource(R.string.playback_resume_sub),
                checked = settings.resumePlayback,
                onCheckedChange = { viewModel.setResumePlayback(it) },
            )
            SettingsSwitchRow(
                title = stringResource(R.string.playback_fade),
                subtitle = stringResource(R.string.playback_fade_sub),
                checked = settings.fadeEnabled,
                onCheckedChange = { viewModel.setFadeEnabled(it) },
            )
            SettingsNote(stringResource(R.string.playback_fade_note))
            SettingsSpacer()
        }
    }
}

private fun speedLabel(speed: Float): String = when (speed) {
    1.0f -> "1×"
    0.5f -> "0.5×"
    0.75f -> "0.75×"
    1.25f -> "1.25×"
    1.5f -> "1.5×"
    2.0f -> "2×"
    else -> "${speed}×"
}
