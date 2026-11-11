package com.harper.ava.ui.settings

import android.app.Activity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harper.ava.AvaApplication
import com.harper.ava.R
import com.harper.ava.core.util.LocaleHelper
import com.harper.ava.data.preferences.AppLanguage

/**
 * Language: Persian / English / system default, independent of the phone
 * language. The choice is persisted first (DataStore + the synchronous
 * mirror `attachBaseContext` reads), then the Activity is recreated so
 * every string reloads in the new language and the layout flips RTL/LTR.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageScreen(app: AvaApplication, onBack: () -> Unit) {
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(
            app,
            app.container.userPreferences,
            app.container.musicRepository,
            app.container.playerRepository,
        ),
    )
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.language_title)) },
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
            SettingsChoiceRow(
                title = stringResource(R.string.language_system),
                subtitle = stringResource(R.string.language_system_sub),
                selected = settings.language == AppLanguage.SYSTEM,
                onClick = {
                    // 0.5.6: on API 33+ the platform recreates the
                    // Activity itself for the locale change; an explicit
                    // recreate() here would restart it a second time.
                    viewModel.setLanguage(AppLanguage.SYSTEM) { LocaleHelper.recreateForLanguageChange(activity) }
                },
            )
            SettingsChoiceRow(
                title = stringResource(R.string.language_fa),
                subtitle = stringResource(R.string.language_fa_sub),
                selected = settings.language == AppLanguage.FA,
                onClick = {
                    viewModel.setLanguage(AppLanguage.FA) { LocaleHelper.recreateForLanguageChange(activity) }
                },
            )
            SettingsChoiceRow(
                title = stringResource(R.string.language_en),
                subtitle = stringResource(R.string.language_en_sub),
                selected = settings.language == AppLanguage.EN,
                onClick = {
                    viewModel.setLanguage(AppLanguage.EN) { LocaleHelper.recreateForLanguageChange(activity) }
                },
            )
            SettingsNote(stringResource(R.string.language_note))
            SettingsSpacer()
        }
    }
}
