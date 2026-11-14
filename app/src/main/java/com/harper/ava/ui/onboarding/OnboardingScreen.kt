package com.harper.ava.ui.onboarding

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harper.ava.AvaApplication
import com.harper.ava.R
import com.harper.ava.ui.scan.ScanUiState
import com.harper.ava.ui.scan.openAppSettings
import com.harper.ava.ui.scan.rememberScanLauncher
import com.harper.ava.ui.scan.toUiState
import com.harper.ava.ui.theme.AvaTokens
import com.harper.ava.ui.theme.INACTIVE_TRACK_ALPHA
import kotlinx.coroutines.launch

/**
 * First-run onboarding (stage 6, spec §14): three pages — what Ava is,
 * how it can look, and the music-access permission with a real scan.
 * Skippable on every page; nothing here is a dead button. When the user
 * grants access, the first scan runs and the app opens on Home once it
 * finishes; when access is denied, the page itself offers a retry and an
 * "Open settings" button (same actions as Home's permission card), so
 * the user is never stuck on a bare message.
 */
@Composable
fun OnboardingScreen(app: AvaApplication, onFinished: () -> Unit) {
    val viewModel: OnboardingViewModel = viewModel(
        factory = OnboardingViewModel.factory(app.container.userPreferences),
    )
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var scanState by remember { mutableStateOf<ScanUiState>(ScanUiState.Idle) }
    val finish: () -> Unit = { viewModel.finish(onFinished) }

    // On the last page the access button runs a real scan; finishing waits
    // for its result instead of leaving the user on an empty Home.
    val requestScan = rememberScanLauncher(
        app = app,
        onScanStarted = { scanState = ScanUiState.Scanning },
        onScanResult = { result ->
            scanState = result.toUiState()
            if (scanState is ScanUiState.Done) finish()
        },
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            // Edge-to-edge (Harper 2026-11-13): no TopAppBar here, so
            // the screen owns its own status-bar inset (see HomeScreen).
            .statusBarsPadding()
            .padding(horizontal = AvaTokens.space24),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(AvaTokens.space64))
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
        ) { page ->
            when (page) {
                0 -> OnboardingPage(
                    icon = Icons.Filled.MusicNote,
                    title = stringResource(R.string.onboarding_title_1),
                    body = stringResource(R.string.onboarding_body_1),
                )
                1 -> OnboardingPage(
                    icon = Icons.Filled.Palette,
                    title = stringResource(R.string.onboarding_title_2),
                    body = stringResource(R.string.onboarding_body_2),
                )
                else -> OnboardingPage(
                    icon = Icons.Filled.Folder,
                    title = stringResource(R.string.onboarding_title_3),
                    body = stringResource(R.string.onboarding_body_3),
                    extra = {
                        Spacer(modifier = Modifier.height(AvaTokens.space24))
                        when (scanState) {
                            ScanUiState.Scanning -> {
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth(),
                                    // 0.2-fix (Track 2): the M3 default
                                    // track is surfaceVariant — invisible
                                    // on pure black in AMOLED. An explicit
                                    // onSurface track at
                                    // INACTIVE_TRACK_ALPHA stays visible.
                                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = INACTIVE_TRACK_ALPHA),
                                )
                                Spacer(modifier = Modifier.height(AvaTokens.space8))
                                Text(
                                    text = stringResource(R.string.scan_scanning),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            ScanUiState.PermissionDenied -> {
                                // Improve run 16: this used to be a bare
                                // message — the grant button was gone, so
                                // the only way out was the bottom "Start"
                                // button and then re-granting from Home.
                                // Same two actions as Home's permission card:
                                // retry the request, or open Settings when
                                // the denial is permanent.
                                Text(
                                    text = stringResource(R.string.scan_permission_denied),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(modifier = Modifier.height(AvaTokens.space12))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(
                                        AvaTokens.space8,
                                        Alignment.CenterHorizontally,
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Button(onClick = requestScan) {
                                        Text(stringResource(R.string.scan_permission_grant))
                                    }
                                    OutlinedButton(onClick = { openAppSettings(context) }) {
                                        Text(stringResource(R.string.scan_permission_settings))
                                    }
                                }
                            }
                            ScanUiState.Error -> {
                                // Improve run 16: same dead end as the
                                // denied case — a bare message with no
                                // retry. The button re-runs the scan.
                                Text(
                                    text = stringResource(R.string.scan_error),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(modifier = Modifier.height(AvaTokens.space12))
                                Button(onClick = requestScan) {
                                    Text(stringResource(R.string.scan_retry))
                                }
                            }
                            else -> {
                                Button(
                                    onClick = requestScan,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(Icons.Filled.Refresh, contentDescription = null)
                                    Spacer(modifier = Modifier.width(AvaTokens.space8))
                                    Text(stringResource(R.string.scan_permission_grant))
                                }
                            }
                        }
                    },
                )
            }
        }

        // Dots.
        Row(
            horizontalArrangement = Arrangement.spacedBy(AvaTokens.space8),
            modifier = Modifier.padding(vertical = AvaTokens.space16),
        ) {
            repeat(3) { index ->
                Box(
                    modifier = Modifier
                        .size(if (index == pagerState.currentPage) 10.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (index == pagerState.currentPage) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            },
                        ),
                )
            }
        }

        if (pagerState.currentPage < 2) {
            Button(
                onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.onboarding_next))
            }
            TextButton(onClick = finish) {
                Text(stringResource(R.string.onboarding_continue))
            }
        } else {
            Button(
                onClick = finish,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.onboarding_start))
            }
            Spacer(modifier = Modifier.height(AvaTokens.space16))
        }
        Spacer(modifier = Modifier.height(AvaTokens.space24))
    }
}

@Composable
private fun OnboardingPage(
    icon: ImageVector,
    title: String,
    body: String,
    extra: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(48.dp),
            )
        }
        Spacer(modifier = Modifier.height(AvaTokens.space24))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(AvaTokens.space12))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        extra?.invoke()
    }
}
