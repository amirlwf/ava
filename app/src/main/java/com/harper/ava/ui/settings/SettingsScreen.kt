package com.harper.ava.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harper.ava.AvaApplication
import com.harper.ava.R
import com.harper.ava.data.preferences.Accent
import com.harper.ava.data.preferences.AppLanguage
import com.harper.ava.data.preferences.ThemeMode

@Composable
fun themeModeLabel(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.SYSTEM -> R.string.theme_system
        ThemeMode.DARK -> R.string.theme_dark
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.AMOLED -> R.string.theme_amoled
    },
)

@Composable
fun accentLabel(accent: Accent): String = stringResource(
    when (accent) {
        Accent.VIOLET -> R.string.accent_violet
        Accent.ELECTRIC_BLUE -> R.string.accent_electric_blue
        Accent.EMERALD -> R.string.accent_emerald
        Accent.CRIMSON -> R.string.accent_crimson
        Accent.AMBER -> R.string.accent_amber
        Accent.ROSE -> R.string.accent_rose
        Accent.MONOCHROME -> R.string.accent_monochrome
    },
)

@Composable
fun languageLabel(language: AppLanguage): String = stringResource(
    when (language) {
        AppLanguage.SYSTEM -> R.string.language_system
        AppLanguage.FA -> R.string.language_fa
        AppLanguage.EN -> R.string.language_en
    },
)

/** The settings hub: one row per area, each opening its own sub-screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    app: AvaApplication,
    onBack: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenLanguage: () -> Unit,
    onOpenPlayback: () -> Unit,
    onOpenStorage: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(
            app,
            app.container.userPreferences,
            app.container.musicRepository,
            app.container.playerRepository,
        ),
    )
    val settings by viewModel.settings.collectAsState()
    val stats by viewModel.storageStats.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
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
            SettingsNavRow(
                icon = Icons.Filled.Palette,
                title = stringResource(R.string.settings_appearance),
                subtitle = if (settings.customAccentArgb != null) {
                    "${themeModeLabel(settings.themeMode)} · ${stringResource(R.string.appearance_custom_active)}"
                } else {
                    "${themeModeLabel(settings.themeMode)} · ${accentLabel(settings.accent)}"
                },
                onClick = onOpenAppearance,
            )
            SettingsNavRow(
                icon = Icons.Filled.Language,
                title = stringResource(R.string.settings_language),
                subtitle = languageLabel(settings.language),
                onClick = onOpenLanguage,
            )
            SettingsNavRow(
                icon = Icons.Filled.PlayArrow,
                title = stringResource(R.string.settings_playback),
                subtitle = if (settings.resumePlayback) {
                    "${settings.defaultSpeed}× · ${stringResource(R.string.playback_resume)}"
                } else {
                    "${settings.defaultSpeed}×"
                },
                onClick = onOpenPlayback,
            )
            SettingsNavRow(
                icon = Icons.Filled.Storage,
                title = stringResource(R.string.settings_storage),
                subtitle = stringResource(R.string.library_songs_count, stats.songCount),
                onClick = onOpenStorage,
            )
            SettingsNavRow(
                icon = Icons.Filled.Info,
                title = stringResource(R.string.settings_about),
                subtitle = stringResource(R.string.settings_about_sub),
                onClick = onOpenAbout,
            )
            SettingsSpacer()
        }
    }
}
