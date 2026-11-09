package com.harper.ava.ui.lyrics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harper.ava.AvaApplication
import com.harper.ava.R
import com.harper.ava.core.util.ParsedLyrics
import com.harper.ava.core.util.lyricIndexAt
import com.harper.ava.ui.components.EmptyState
import com.harper.ava.ui.theme.AvaTokens

/**
 * Lyrics: the current song's local .lrc file, with the playing line
 * highlighted and followed as playback advances. Files without
 * timestamps are shown as plain scrollable text. When no .lrc file
 * exists, the screen says so and tells the user where to put one
 * (same folder, same name as the song file) instead of showing a
 * blank page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsScreen(app: AvaApplication, onBack: () -> Unit) {
    val viewModel: LyricsViewModel = viewModel(
        factory = LyricsViewModel.factory(
            app.container.lyricsRepository,
            app.container.userPreferences,
            app.container.playerRepository,
        ),
    )
    val song by viewModel.currentSong.collectAsState()
    val positionMs by viewModel.positionMs.collectAsState()
    val lyricsState by viewModel.lyrics.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.lyrics_title)) },
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
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (song != null) {
                Text(
                    text = song!!.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AvaTokens.space24, vertical = AvaTokens.space4),
                )
                val artist = song!!.artist
                if (artist.isNotBlank()) {
                    Text(
                        text = artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            when (val state = lyricsState) {
                LyricsUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                LyricsUiState.NoSong -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        // Stage 6: this used the queue's empty copy — wrong screen, wrong text.
                        EmptyState(
                            title = stringResource(R.string.lyrics_no_song_title),
                            body = stringResource(R.string.lyrics_no_song_body),
                        )
                    }
                }
                LyricsUiState.NotFound -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        EmptyState(
                            title = stringResource(R.string.lyrics_empty_title),
                            body = stringResource(
                                R.string.lyrics_empty_body,
                                song?.fileName ?: song?.title.orEmpty(),
                            ),
                        )
                    }
                }
                is LyricsUiState.Loaded -> {
                    LyricsList(lyrics = state.lyrics, positionMs = positionMs)
                }
            }
        }
    }
}

@Composable
private fun LyricsList(lyrics: ParsedLyrics, positionMs: Long) {
    val listState = rememberLazyListState()
    // Stage 6: following is a real toggle now. Scrolling by hand pauses it
    // (the list used to yank the user back to the playing line on every
    // line change); a "follow" pill brings it back. Programmatic scrolls
    // are flagged so they never pause following themselves.
    var followEnabled by remember { mutableStateOf(true) }
    var programmaticScroll by remember { mutableStateOf(false) }
    val currentIndex = if (lyrics.hasTimestamps) {
        lyricIndexAt(lyrics.lines, positionMs)
    } else {
        -1
    }
    // Follow the playing line, keeping two lines of context above it.
    LaunchedEffect(currentIndex, followEnabled) {
        if (followEnabled && currentIndex >= 0) {
            programmaticScroll = true
            listState.animateScrollToItem((currentIndex - 2).coerceAtLeast(0))
            programmaticScroll = false
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (scrolling && !programmaticScroll) followEnabled = false
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = AvaTokens.space24, vertical = AvaTokens.space8),
        ) {
        itemsIndexed(lyrics.lines, key = { index, line -> "$index|${line.timeMs}" }) { index, line ->
            val isCurrent = index == currentIndex
            Text(
                text = line.text,
                style = if (isCurrent) {
                    MaterialTheme.typography.titleLarge
                } else {
                    MaterialTheme.typography.bodyLarge
                },
                color = if (isCurrent) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(AvaTokens.radiusSmall))
                    .background(
                        if (isCurrent) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        } else {
                            MaterialTheme.colorScheme.background
                        },
                    )
                    .padding(vertical = AvaTokens.space8, horizontal = AvaTokens.space8),
            )
        }
        item { Spacer(modifier = Modifier.height(AvaTokens.space48)) }
        }
        if (!followEnabled && currentIndex >= 0) {
            FilledTonalButton(
                onClick = { followEnabled = true },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = AvaTokens.space16),
            ) {
                Text(stringResource(R.string.lyrics_follow))
            }
        }
    }
}
