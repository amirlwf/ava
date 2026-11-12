package com.harper.ava.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harper.ava.AvaApplication
import com.harper.ava.R
import com.harper.ava.data.preferences.Accent
import com.harper.ava.data.preferences.AppIcon
import com.harper.ava.data.preferences.AppLanguage
import com.harper.ava.data.preferences.ThemeMode
import com.harper.ava.ui.theme.AvaTokens
import com.harper.ava.ui.theme.spec
import java.util.Locale
import kotlinx.coroutines.launch

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
fun appIconLabel(icon: AppIcon): String = stringResource(
    when (icon) {
        AppIcon.DEFAULT -> R.string.app_icon_default
        AppIcon.PURPLE -> R.string.app_icon_purple
        AppIcon.GREEN -> R.string.app_icon_green
    },
)

/** Preview drawable for an icon variant (the same foreground as the launcher icon). */
private fun appIconPreviewRes(icon: AppIcon): Int = when (icon) {
    AppIcon.DEFAULT -> R.drawable.ic_launcher_foreground
    AppIcon.PURPLE -> R.drawable.ic_launcher_purple_foreground
    AppIcon.GREEN -> R.drawable.ic_launcher_green_foreground
}

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
        ),
    )
    val settings by viewModel.settings.collectAsState()
    val stats by viewModel.storageStats.collectAsState()
    val scope = rememberCoroutineScope()

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
            // App icon: three launcher-icon variants (Harper's own artwork,
            // activity-alias). Tapping one persists the choice and switches
            // Tapping one persists the choice and switches the launcher
            // entry via PackageManager; the change shows up in the
            // launcher within seconds, no restart needed.
            SettingsSectionHeader(stringResource(R.string.app_icon_title))
            SettingsNote(stringResource(R.string.app_icon_hint))
            FlowRow(
                modifier = Modifier.padding(horizontal = AvaTokens.space20),
                horizontalArrangement = Arrangement.spacedBy(AvaTokens.space12),
                verticalArrangement = Arrangement.spacedBy(AvaTokens.space12),
            ) {
                AppIcon.entries.forEach { icon ->
                    AppIconSwatch(
                        icon = icon,
                        selected = settings.appIcon == icon,
                        onClick = {
                            scope.launch {
                                app.container.userPreferences.setAppIcon(icon)
                            }
                        },
                    )
                }
            }
            SettingsSpacer()
        }
    }
}

/** One icon variant: a rounded preview of the real launcher icon + label. */
@Composable
private fun AppIconSwatch(icon: AppIcon, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(72.dp),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .then(
                    if (selected) {
                        Modifier.border(
                            2.dp,
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(16.dp),
                        )
                    } else {
                        Modifier
                    },
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(appIconPreviewRes(icon)),
                contentDescription = appIconLabel(icon),
                modifier = Modifier.fillMaxSize(),
            )
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(AvaTokens.space4))
        Text(
            text = appIconLabel(icon),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
