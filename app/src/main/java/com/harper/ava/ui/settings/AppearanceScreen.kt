package com.harper.ava.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import com.harper.ava.data.preferences.AppTheme
import com.harper.ava.data.preferences.ThemeMode
import com.harper.ava.ui.theme.AvaTokens
import com.harper.ava.ui.theme.accentPalette
import com.harper.ava.ui.theme.spec
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * Appearance (0.7.0): ten full themes + theme mode + custom colour.
 *
 * - The theme picker is the main choice: each theme is a complete design
 *   system (palette, hero gradient, corner radii) and applies app-wide
 *   the moment it is picked (MainActivity reads `settings.appTheme`
 *   into `AvaTheme`), including the media-notification accent.
 * - Theme mode (System / Dark / Light / AMOLED) is independent of the
 *   theme: every theme is readable in all three modes, and AMOLED keeps
 *   a pure black background.
 * - The old Accent preset swatches are gone on purpose: the theme
 *   *replaces* the accent preset (the stored `Accent` value is kept in
 *   DataStore but no longer drives any colour). The HSV custom colour
 *   stays: it overrides the theme's primary family until cleared, and
 *   the theme's surfaces / gradient / shapes stay underneath it.
 *
 * Theme names and moods come from the spec itself (`ThemeSpec.nameFa` /
 * `nameEn`), so no new string resources were needed; the two section
 * headers below are the only hard-coded labels and follow the app
 * language (fa/en).
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
    val scope = rememberCoroutineScope()
    val fa = Locale.getDefault().language == "fa"

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
            SettingsSectionHeader(if (fa) "تم‌های آوا" else "Ava themes")
            SettingsNote(
                if (fa) "هر تم یک دیزاین کامل است: رنگ، گرادیان و گوشه‌ها. با انتخاب تم، رنگ تأکیدی قبلی جای خود را به تم می‌دهد."
                else "Each theme is a complete design: colours, gradient and corners. Picking a theme replaces the old accent preset.",
            )
            AppTheme.entries.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AvaTokens.space20, vertical = AvaTokens.space6),
                    horizontalArrangement = Arrangement.spacedBy(AvaTokens.space12),
                ) {
                    row.forEach { theme ->
                        AppThemeCard(
                            theme = theme,
                            fa = fa,
                            selected = settings.appTheme == theme,
                            onClick = {
                                scope.launch {
                                    app.container.userPreferences.setAppTheme(theme)
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }

            // 0.7.2: seasonal effects — the leaves / snow / sun / stars
            // decor on Home + Now Playing. Off hides every decor,
            // including the Galaxy starfield.
            SettingsSwitchRow(
                title = stringResource(R.string.appearance_seasonal_title),
                subtitle = stringResource(R.string.appearance_seasonal_sub),
                checked = settings.seasonalEffectsEnabled,
                onCheckedChange = { enabled ->
                    scope.launch {
                        app.container.userPreferences.setSeasonalEffectsEnabled(enabled)
                    }
                },
            )

            SettingsSectionHeader(if (fa) "حالت نمایش" else "Display mode")
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

/** One theme card: its hero gradient on top, name + mood below. */
@Composable
private fun AppThemeCard(
    theme: AppTheme,
    fa: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spec = theme.spec
    val shape = RoundedCornerShape(AvaTokens.radiusLarge)
    Column(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant,
                shape = shape,
            )
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(
                    Brush.linearGradient(spec.heroDark.map { Color(it) }),
                ),
            contentAlignment = Alignment.TopEnd,
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .padding(AvaTokens.space8)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        Column(modifier = Modifier.padding(AvaTokens.space12)) {
            Text(
                text = if (fa) spec.nameFa else spec.nameEn,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(AvaTokens.space2))
            Text(
                text = if (fa) spec.moodFa else spec.moodEn,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
