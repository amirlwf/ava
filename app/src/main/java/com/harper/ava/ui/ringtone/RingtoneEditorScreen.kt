package com.harper.ava.ui.ringtone

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.harper.ava.AvaApplication
import com.harper.ava.R
import com.harper.ava.core.util.RINGTONE_MIN_MS
import com.harper.ava.core.util.TrimRange
import com.harper.ava.core.util.defaultTrimRange
import com.harper.ava.core.util.formatDuration
import com.harper.ava.core.util.ringtoneFileName
import com.harper.ava.core.util.sanitizeTrimRange
import com.harper.ava.domain.model.Song
import com.harper.ava.playback.SetRingtoneResult
import com.harper.ava.playback.TrimResult
import com.harper.ava.playback.setAsRingtone
import com.harper.ava.playback.trimSongForRingtone
import com.harper.ava.ui.theme.AvaTokens
import java.io.File
import kotlinx.coroutines.launch

/**
 * Ringtone editor (0.1-fix1, Track G).
 *
 * Trim a segment of a song (RangeSlider, 1–60 s, default the first
 * 30 s), preview the segment through the shared player, then set it
 * as the phone ringtone — a real Transformer trim + a real MediaStore
 * + `RingtoneManager` set. Nothing here is a mock: the sliders move a
 * real [TrimRange], the preview really plays, and the set button
 * really trims and really sets.
 *
 * Preview and the queue: previewing plays through the shared player
 * (per the brief), which would replace the user's queue — so before
 * a preview of a *different* song the current queue + index +
 * position are snapshotted and restored (paused) when the preview
 * ends, manually or at the segment end. If the edited song is already
 * playing, there is nothing to restore.
 *
 * WRITE_SETTINGS: setting the system ringtone needs the "Modify
 * system settings" grant. When it is missing the editor explains and
 * offers the grant screen; the trimmed file is kept in the cache, so
 * granting and retrying does not re-trim.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RingtoneEditorScreen(
    app: AvaApplication,
    songId: Long,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val playerRepository = app.container.playerRepository
    val song by produceState<Song?>(initialValue = null, songId) {
        value = app.container.musicRepository.getSongById(songId)
    }
    val scope = rememberCoroutineScope()

    var range by remember(song) {
        mutableStateOf(defaultTrimRange(song?.durationMs ?: 0L))
    }
    var trimming by remember { mutableStateOf(false) }
    var lastTrimmedFile by remember { mutableStateOf<File?>(null) }
    var error by remember { mutableStateOf(false) }
    var needsPermission by remember { mutableStateOf(false) }
    var success by remember { mutableStateOf(false) }
    var previewing by remember { mutableStateOf(false) }
    var previewRestore by remember { mutableStateOf<PreviewRestore?>(null) }

    val positionMs by playerRepository.positionMs.collectAsState()
    val playback by playerRepository.state.collectAsState()

    fun stopPreview() {
        if (!previewing) return
        previewing = false
        val restore = previewRestore
        previewRestore = null
        if (playerRepository.player.isPlaying) playerRepository.togglePlayPause()
        if (restore != null && restore.queue.isNotEmpty()) {
            playerRepository.playSongs(restore.queue, restore.index)
            playerRepository.seekTo(restore.positionMs)
            if (playerRepository.player.isPlaying) playerRepository.togglePlayPause()
        }
    }

    // The segment end stops the preview; leaving the screen stops it too.
    LaunchedEffect(previewing, positionMs) {
        if (previewing && playback.isPlaying && positionMs >= range.endMs) {
            stopPreview()
        }
    }
    DisposableEffect(Unit) {
        onDispose { stopPreview() }
    }

    fun startPreview() {
        val currentSong = song ?: return
        val current = playerRepository.state.value
        if (current.currentSong?.id != currentSong.id) {
            previewRestore = PreviewRestore(
                queue = current.queue,
                index = current.queueIndex,
                positionMs = playerRepository.positionMs.value,
            )
            playerRepository.playSongs(listOf(currentSong))
        } else {
            previewRestore = null
        }
        playerRepository.seekTo(range.startMs)
        if (!playerRepository.player.isPlaying) playerRepository.togglePlayPause()
        previewing = true
    }

    fun setRingtone() {
        val currentSong = song ?: return
        scope.launch {
            trimming = true
            error = false
            needsPermission = false
            success = false
            val file = lastTrimmedFile?.takeIf { it.exists() } ?: run {
                when (val result = trimSongForRingtone(context, currentSong, range)) {
                    is TrimResult.Success -> {
                        lastTrimmedFile = result.file
                        result.file
                    }
                    is TrimResult.Failure -> {
                        trimming = false
                        error = true
                        return@launch
                    }
                }
            }
            when (setAsRingtone(context, file, ringtoneFileName(currentSong.title))) {
                is SetRingtoneResult.Success -> success = true
                is SetRingtoneResult.NeedsWriteSettings -> needsPermission = true
                is SetRingtoneResult.Failure -> error = true
            }
            trimming = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ringtone_editor_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        val currentSong = song
        if (currentSong == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        val durationMs = currentSong.durationMs
        val tooShort = durationMs < RINGTONE_MIN_MS
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = AvaTokens.space24),
            verticalArrangement = Arrangement.spacedBy(AvaTokens.space16),
        ) {
            Text(
                text = stringResource(R.string.ringtone_editor_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(verticalArrangement = Arrangement.spacedBy(AvaTokens.space2)) {
                Text(
                    text = currentSong.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Text(
                    text = currentSong.artist.ifBlank {
                        stringResource(R.string.unknown_artist)
                    } + " • " + formatDuration(durationMs),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }

            if (!tooShort) {
                val durationSec = durationMs / 1000f
                RangeSlider(
                    value = range.startMs / 1000f..range.endMs / 1000f,
                    onValueChange = { sliderRange ->
                        range = sanitizeTrimRange(
                            (sliderRange.start * 1000).toLong(),
                            (sliderRange.endInclusive * 1000).toLong(),
                            durationMs,
                        )
                        // A changed range invalidates the cached trim.
                        lastTrimmedFile = null
                        success = false
                    },
                    valueRange = 0f..durationSec,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.ringtone_trim_start) +
                            ": " + formatDuration(range.startMs),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = formatDuration(range.lengthMs),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringResource(R.string.ringtone_trim_end) +
                            ": " + formatDuration(range.endMs),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            if (tooShort || error) {
                Text(
                    text = stringResource(R.string.ringtone_error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            OutlinedButton(
                onClick = { if (previewing) stopPreview() else startPreview() },
                enabled = !tooShort && !trimming,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(
                        if (previewing) {
                            R.string.ringtone_stop_preview
                        } else {
                            R.string.ringtone_preview
                        },
                    ),
                )
            }
            Text(
                text = stringResource(R.string.ringtone_preview_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (needsPermission) {
                Text(
                    text = stringResource(R.string.ringtone_needs_permission),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = {
                        val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.ringtone_open_settings))
                }
            }

            if (success) {
                Text(
                    text = stringResource(R.string.ringtone_success),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.ringtone_done))
                }
            } else {
                Button(
                    onClick = { setRingtone() },
                    enabled = !tooShort && !trimming,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (trimming) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = AvaTokens.space8),
                        )
                        Text(stringResource(R.string.ringtone_trimming))
                    } else {
                        Text(stringResource(R.string.ringtone_set))
                    }
                }
            }
            Spacer(modifier = Modifier.height(AvaTokens.space8))
        }
    }
}

/** What a preview of another song must put back when it ends. */
private data class PreviewRestore(
    val queue: List<Song>,
    val index: Int,
    val positionMs: Long,
)
