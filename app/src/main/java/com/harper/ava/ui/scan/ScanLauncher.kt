package com.harper.ava.ui.scan

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.harper.ava.AvaApplication
import com.harper.ava.data.media.AudioPermission
import com.harper.ava.data.media.ScanResult
import kotlinx.coroutines.launch

/** UI state of a library scan, shared by Home and Library. */
sealed interface ScanUiState {
    data object Idle : ScanUiState
    data object Scanning : ScanUiState
    data class Done(val found: Int, val removedStale: Int) : ScanUiState
    data object PermissionDenied : ScanUiState
    data object Error : ScanUiState
}

fun ScanResult.toUiState(): ScanUiState = when {
    error == "permission_denied" -> ScanUiState.PermissionDenied
    error != null -> ScanUiState.Error
    else -> ScanUiState.Done(found, removedStale)
}

/**
 * Returns a `requestScan()` trigger shared by Home and Library. It asks
 * for the audio permission (with the system dialog) when needed, then runs
 * the MediaStore rescan off the main thread and reports the result.
 * The caller owns the [ScanUiState] and renders it (progress, count,
 * permission explanation card).
 */
@Composable
fun rememberScanLauncher(
    app: AvaApplication,
    onScanStarted: () -> Unit,
    onScanResult: (ScanResult) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Stage 6: a double-tap (or two screens' buttons) used to start two
    // concurrent rescans. The second tap is now ignored while one runs.
    var inFlight by remember { mutableStateOf(false) }
    val runScan: () -> Unit = {
        if (!inFlight) {
            inFlight = true
            onScanStarted()
            scope.launch {
                val result = app.container.mediaStoreScanner.rescan()
                inFlight = false
                onScanResult(result)
            }
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            runScan()
        } else {
            onScanResult(ScanResult(0, 0, 0, 0, error = "permission_denied"))
        }
    }
    return {
        if (AudioPermission.isGranted(context)) {
            runScan()
        } else {
            permissionLauncher.launch(AudioPermission.permissionName())
        }
    }
}

/** Opens the app's system Settings page (used when permission was denied). */
fun openAppSettings(context: android.content.Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
