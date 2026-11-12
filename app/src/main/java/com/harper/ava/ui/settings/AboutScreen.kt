package com.harper.ava.ui.settings

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.harper.ava.R
import com.harper.ava.core.util.CrashReporter
import com.harper.ava.ui.theme.AvaTokens

/**
 * About: app name, the real installed version (from PackageManager —
 * never a hard-coded string), the dedication to Ava Rahmani Rad, a link
 * to Harper's website, and honest offline/privacy notes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val versionName = remember {
        runCatching {
            val packageManager = context.packageManager
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0),
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(context.packageName, 0)
            }
            info.versionName
        }.getOrNull() ?: "—"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
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
            Spacer(modifier = Modifier.height(AvaTokens.space16))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = AvaTokens.space20),
            )
            // Dedication (0.7.4): the app is named after Ava Rahmani Rad and
            // offered in her memory. The title uses the Lalezar display
            // face (headlineSmall) and the body Vazirmatn — the app's own
            // fonts only, no system font. The text says nothing about how
            // she died; it is about her name and her memory.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = AvaTokens.space20,
                        vertical = AvaTokens.space12,
                    )
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(AvaTokens.radiusMedium),
                    )
                    .padding(AvaTokens.space20),
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.about_dedication_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(AvaTokens.space8))
                    Text(
                        text = stringResource(R.string.about_dedication_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            SettingsInfoRow(
                label = stringResource(R.string.about_version),
                value = versionName,
            )
            SettingsSectionHeader(stringResource(R.string.about_offline_title))
            SettingsNote(stringResource(R.string.about_offline_body))
            SettingsSectionHeader(stringResource(R.string.about_privacy_title))
            SettingsNote(stringResource(R.string.about_privacy_body))
            SettingsSectionHeader(stringResource(R.string.about_built_title))
            SettingsNote(stringResource(R.string.about_built_body))
            // Harper's website (0.7.4): opens https://amirlwf.ir in the browser.
            SettingsLinkRow(
                icon = Icons.Filled.Public,
                title = stringResource(R.string.about_website_title),
                subtitle = stringResource(R.string.about_website_value),
                onClick = { openHarperWebsite(context) },
            )
            // Crash diagnostics (0.5.1): if the app ever dies, the trace is
            // saved on the device and can be shared from here as plain text.
            SettingsSectionHeader(stringResource(R.string.about_diagnostics_title))
            val crashLogs = remember { CrashReporter.listLogs(context) }
            if (crashLogs.isEmpty()) {
                SettingsNote(stringResource(R.string.about_diagnostics_none))
            } else {
                SettingsNote(stringResource(R.string.about_diagnostics_body))
                OutlinedButton(
                    onClick = {
                        val text = CrashReporter.readLatest(context) ?: return@OutlinedButton
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                        context.startActivity(Intent.createChooser(intent, null))
                    },
                    modifier = Modifier.padding(
                        horizontal = AvaTokens.space20,
                        vertical = AvaTokens.space8,
                    ),
                ) {
                    Text(stringResource(R.string.about_diagnostics_share))
                }
            }
            SettingsSpacer()
        }
    }
}
