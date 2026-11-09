package com.harper.ava.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harper.ava.AvaApplication
import com.harper.ava.R
import com.harper.ava.core.util.argbToHex
import com.harper.ava.core.util.argbToHsv
import com.harper.ava.core.util.hsvToArgb
import com.harper.ava.data.preferences.Accent
import com.harper.ava.data.preferences.ThemeMode
import com.harper.ava.ui.theme.AvaTokens
import com.harper.ava.ui.theme.accentPalette

/**
 * Appearance: theme mode (System / Dark / Light / AMOLED) + accent.
 * Every change writes to DataStore and MainActivity re-themes the whole
 * app from the same flow, so the change is instant on every screen and
 * survives restarts. A custom color (HSV picker) overrides the preset
 * until a preset is picked again.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(app: AvaApplication, onBack: () -> Unit) {
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
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_appearance)) },
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
            SettingsSectionHeader(stringResource(R.string.appearance_theme_title))
            SettingsChoiceRow(
                title = stringResource(R.string.theme_system),
                subtitle = stringResource(R.string.theme_system_sub),
                selected = settings.themeMode == ThemeMode.SYSTEM,
                onClick = { viewModel.setThemeMode(ThemeMode.SYSTEM) },
            )
            SettingsChoiceRow(
                title = stringResource(R.string.theme_dark),
                subtitle = stringResource(R.string.theme_dark_sub),
                selected = settings.themeMode == ThemeMode.DARK,
                onClick = { viewModel.setThemeMode(ThemeMode.DARK) },
            )
            SettingsChoiceRow(
                title = stringResource(R.string.theme_light),
                subtitle = stringResource(R.string.theme_light_sub),
                selected = settings.themeMode == ThemeMode.LIGHT,
                onClick = { viewModel.setThemeMode(ThemeMode.LIGHT) },
            )
            SettingsChoiceRow(
                title = stringResource(R.string.theme_amoled),
                subtitle = stringResource(R.string.theme_amoled_sub),
                selected = settings.themeMode == ThemeMode.AMOLED,
                onClick = { viewModel.setThemeMode(ThemeMode.AMOLED) },
            )

            SettingsSectionHeader(stringResource(R.string.appearance_accent_title))
            FlowRow(
                modifier = Modifier.padding(horizontal = AvaTokens.space20),
                horizontalArrangement = Arrangement.spacedBy(AvaTokens.space12),
                verticalArrangement = Arrangement.spacedBy(AvaTokens.space12),
            ) {
                Accent.entries.forEach { accent ->
                    val palette = accentPalette(accent)
                    val selected = settings.customAccentArgb == null && settings.accent == accent
                    AccentSwatch(
                        color = palette.primary,
                        label = accentLabel(accent),
                        selected = selected,
                        onClick = { viewModel.setAccent(accent) },
                    )
                }
            }

            SettingsSectionHeader(stringResource(R.string.appearance_custom_title))
            SettingsNote(stringResource(R.string.appearance_custom_hint))
            CustomColorPicker(
                currentCustomArgb = settings.customAccentArgb,
                fallbackArgb = accentPalette(settings.accent).primary,
                onApply = { argb -> viewModel.setCustomAccent(argb) },
                onClear = { viewModel.clearCustomAccent() },
            )
            SettingsSpacer()
        }
    }
}

@Composable
private fun AccentSwatch(color: Color, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(color)
                .then(
                    if (selected) {
                        Modifier.border(2.dp, MaterialTheme.colorScheme.onBackground, CircleShape)
                    } else {
                        Modifier
                    },
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = Color.White,
                )
            }
        }
        Spacer(modifier = Modifier.height(AvaTokens.space4))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * HSV color picker: three sliders + a live preview + Apply. The sliders
 * only change the local preview; nothing is persisted until Apply, and
 * applying writes one DataStore value that re-themes the whole app.
 * The slider state resets when the stored custom color changes (apply /
 * clear / preset pick), so it never shows a stale color.
 */
@Composable
private fun CustomColorPicker(
    currentCustomArgb: Int?,
    fallbackArgb: Color,
    onApply: (Int) -> Unit,
    onClear: () -> Unit,
) {
    val initialArgb = currentCustomArgb ?: fallbackArgb.toArgb()
    val (initialHue, initialSaturation, initialValue) = remember(currentCustomArgb) {
        argbToHsv(initialArgb)
    }
    var hue by remember(currentCustomArgb) { mutableFloatStateOf(initialHue) }
    var saturation by remember(currentCustomArgb) { mutableFloatStateOf(initialSaturation) }
    var value by remember(currentCustomArgb) { mutableFloatStateOf(initialValue) }
    val previewArgb = hsvToArgb(hue, saturation, value)

    Column(modifier = Modifier.padding(horizontal = AvaTokens.space20)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color(previewArgb))
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
            )
            Spacer(modifier = Modifier.width(AvaTokens.space16))
            Column {
                Text(
                    text = argbToHex(previewArgb),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (currentCustomArgb != null) {
                    Text(
                        text = stringResource(R.string.appearance_custom_active),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(AvaTokens.space16))
        Text(
            text = stringResource(R.string.color_hue),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Slider(value = hue, onValueChange = { hue = it }, valueRange = 0f..360f)
        Text(
            text = stringResource(R.string.color_saturation),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Slider(value = saturation, onValueChange = { saturation = it }, valueRange = 0f..1f)
        Text(
            text = stringResource(R.string.color_brightness),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Slider(value = value, onValueChange = { value = it }, valueRange = 0f..1f)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AvaTokens.space12),
        ) {
            Button(onClick = { onApply(previewArgb) }) {
                Text(stringResource(R.string.color_apply))
            }
            if (currentCustomArgb != null) {
                TextButton(onClick = onClear) {
                    Text(stringResource(R.string.appearance_custom_clear))
                }
            }
        }
    }
}
