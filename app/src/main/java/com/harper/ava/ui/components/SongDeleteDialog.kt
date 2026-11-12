package com.harper.ava.ui.components

import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.harper.ava.R
import com.harper.ava.data.repository.SongDeleteCoordinator
import com.harper.ava.data.repository.SongDeleteOutcome
import com.harper.ava.domain.model.Song

/**
 * Shared 0.7.2 song-delete UI, used by Home / Library / Search /
 * Favorites / Playlist detail so the confirm dialog and the result
 * feedback are identical everywhere.
 *
 * The dialog is the IN-APP confirmation (song name + permanent-delete
 * warning). Confirming calls [onConfirm] — the ViewModel then runs
 * [SongDeleteCoordinator.requestDelete], which shows the system
 * consent dialog on API 30+. The outcome Toast is wired by
 * [DeleteSongOutcomeToasts], placed once per screen.
 */
@Composable
fun SongDeleteConfirmDialog(
    song: Song?,
    onDismiss: () -> Unit,
    onConfirm: (Song) -> Unit,
) {
    val toDelete = song ?: return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.song_delete_confirm_title)) },
        text = { Text(stringResource(R.string.song_delete_confirm_body, toDelete.title)) },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(toDelete) },
            ) {
                Text(
                    stringResource(R.string.song_delete_confirm),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.playlists_cancel))
            }
        },
    )
}

/**
 * Collects [SongDeleteCoordinator.outcomes] and shows one Toast per
 * delete attempt (deleted / cancelled / failed). Place once per
 * screen that offers song delete. The screens have no Scaffold /
 * SnackbarHost, so a Toast is the feedback channel.
 */
@Composable
fun DeleteSongOutcomeToasts() {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        SongDeleteCoordinator.outcomes.collect { outcome ->
            val res = when (outcome) {
                SongDeleteOutcome.DELETED -> R.string.song_delete_success
                SongDeleteOutcome.CANCELLED -> R.string.song_delete_cancelled
                SongDeleteOutcome.FAILED -> R.string.song_delete_failed
            }
            Toast.makeText(context, res, Toast.LENGTH_SHORT).show()
        }
    }
}
