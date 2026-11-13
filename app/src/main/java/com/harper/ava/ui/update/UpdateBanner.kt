package com.harper.ava.ui.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.harper.ava.AvaApplication
import com.harper.ava.R
import com.harper.ava.data.update.UpdateChecker
import com.harper.ava.ui.theme.AvaTokens

/**
 * Non-intrusive in-app update banner (0.7.2), pinned above the NavHost
 * so it is visible on every screen until dismissed. Shown only when the
 * automatic check found a newer release the user has not dismissed.
 *
 * The banner only READS the state — it never triggers a check.
 * The automatic check is triggered from MainActivity's RESUMED
 * lifecycle (0.2-fix, Track 3: every entry into the app, cold start
 * and return from background), and
 * [com.harper.ava.data.update.UpdateManager] does the network work
 * on its own IO scope, so startup is never blocked. All text styles
 * come from MaterialTheme (Vazirmatn body / Lalezar display) — no
 * system font.
 */
@Composable
fun UpdateBanner(app: AvaApplication) {
    val updateManager = app.container.updateManager
    val banner by updateManager.bannerState.collectAsState()

    val state = banner ?: return
    val context = LocalContext.current
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = AvaTokens.space16,
                    end = AvaTokens.space8,
                    top = AvaTokens.space8,
                    bottom = AvaTokens.space8,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.SystemUpdate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(modifier = Modifier.width(AvaTokens.space12))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.update_available_title, state.versionName),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.update_available_body),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = AvaTokens.space4),
                ) {
                    Button(
                        onClick = {
                            UpdateChecker.openReleasePage(context, state.htmlUrl)
                            updateManager.dismissForNow()
                        },
                    ) {
                        Text(stringResource(R.string.update_download))
                    }
                    Spacer(modifier = Modifier.width(AvaTokens.space8))
                    TextButton(onClick = { updateManager.dismissForNow() }) {
                        Text(stringResource(R.string.update_later))
                    }
                    TextButton(onClick = { updateManager.dismissVersion(state.tag) }) {
                        Text(stringResource(R.string.update_dont_remind))
                    }
                }
            }
            IconButton(onClick = { updateManager.dismissForNow() }) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.update_dismiss),
                )
            }
        }
    }
}
