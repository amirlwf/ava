package com.harper.ava.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harper.ava.AvaApplication
import com.harper.ava.R
import com.harper.ava.data.preferences.Accent
import com.harper.ava.data.preferences.AppLanguage
import com.harper.ava.data.preferences.ThemeMode
import com.harper.ava.data.update.ManualUpdateCheckState
import com.harper.ava.ui.theme.AvaTokens
import com.harper.ava.ui.theme.spec
import com.harper.ava.ui.theme.glassAwareScaffoldContainerColor
import com.harper.ava.ui.theme.glassAwareTopBarContainerColor
import java.util.Locale

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
    onOpenInsights: () -> Unit,
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
            updateManager = app.container.updateManager,
        ),
    )
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val stats by viewModel.storageStats.collectAsState()
    val manualUpdateCheck by viewModel.manualUpdateCheckState.collectAsState()

    Scaffold(
        containerColor = glassAwareScaffoldContainerColor(),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = glassAwareTopBarContainerColor()),
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
                    val spec = settings.appTheme.spec
                    val themeName =
                        if (Locale.getDefault().language == "fa") spec.nameFa else spec.nameEn
                    "${themeModeLabel(settings.themeMode)} · $themeName"
                },
                onClick = onOpenAppearance,
            )
            SettingsNavRow(
                icon = Icons.Filled.BarChart,
                title = stringResource(R.string.settings_insights),
                subtitle = stringResource(R.string.settings_insights_sub),
                onClick = onOpenInsights,
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
            // Harper's website (0.7.4): opens https://amirlwf.ir in the browser.
            SettingsLinkRow(
                icon = Icons.Filled.Public,
                title = stringResource(R.string.settings_website),
                subtitle = stringResource(R.string.settings_website_sub),
                onClick = { openHarperWebsite(context) },
            )
            // Updates (0.7.2): automatic check toggle + manual "check now".
            // The automatic check itself runs from the update banner; this
            // section only exposes its switch and a manual check.
            SettingsSectionHeader(stringResource(R.string.update_title))
            SettingsSwitchRow(
                title = stringResource(R.string.update_auto_title),
                subtitle = stringResource(R.string.update_auto_sub),
                checked = settings.autoUpdateCheck,
                onCheckedChange = { viewModel.setAutoUpdateCheck(it) },
            )
            Row(
                modifier = Modifier.padding(
                    horizontal = AvaTokens.space20,
                    vertical = AvaTokens.space8,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = { viewModel.checkForUpdatesNow() },
                    enabled = manualUpdateCheck != ManualUpdateCheckState.Checking,
                ) {
                    Icon(
                        imageVector = Icons.Filled.SystemUpdate,
                        contentDescription = null,
                    )
                    Spacer(modifier = Modifier.width(AvaTokens.space8))
                    Text(stringResource(R.string.update_check_now))
                }
            }
            when (val check = manualUpdateCheck) {
                ManualUpdateCheckState.Idle -> Unit
                ManualUpdateCheckState.Checking ->
                    SettingsNote(stringResource(R.string.update_checking))

                is ManualUpdateCheckState.Available ->
                    SettingsNote(stringResource(R.string.update_available_short, check.versionName))

                is ManualUpdateCheckState.UpToDate ->
                    SettingsNote(stringResource(R.string.update_uptodate, check.currentVersion))

                ManualUpdateCheckState.Error ->
                    SettingsNote(stringResource(R.string.update_check_error))
            }
        }
    }
}
