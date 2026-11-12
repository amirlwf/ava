package com.harper.ava.ui.insights

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harper.ava.AvaApplication
import com.harper.ava.R
import com.harper.ava.core.util.DayTotal
import com.harper.ava.core.util.InsightsData
import com.harper.ava.core.util.TopNamed
import com.harper.ava.core.util.TopSong
import com.harper.ava.core.util.formatDuration
import com.harper.ava.core.util.formatListeningTime
import com.harper.ava.ui.components.EmptyState
import com.harper.ava.ui.theme.AvaTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Listening insights (0.7.0): top songs / artists / albums / genres,
 * listening time (today / week / month), a 7-day trend and listening
 * habits — all from the on-device play history, nothing leaves the
 * phone. Visual language follows the 0.6.0 design: soft large radii,
 * Lalezar display titles (displayLarge / headlineSmall), Vazirmatn body,
 * [AvaTokens] spacing everywhere.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(
    app: AvaApplication,
    onBack: () -> Unit,
) {
    val viewModel: InsightsViewModel = viewModel(
        factory = InsightsViewModel.factory(app.container.musicRepository, app.container.playerRepository),
    )
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.insights_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.settings_back),
                    )
                }
            },
        )
        when (val state = uiState) {
            InsightsUiState.Loading -> Box(modifier = Modifier.fillMaxSize())
            InsightsUiState.Empty -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(
                    title = stringResource(R.string.insights_empty_title),
                    body = stringResource(R.string.insights_empty_body),
                    icon = Icons.Filled.BarChart,
                    note = stringResource(R.string.insights_empty_note),
                )
            }
            is InsightsUiState.Data -> InsightsContent(
                data = state.insights,
                onPlaySong = { viewModel.playTopSong(it) },
            )
        }
    }
}

@Composable
private fun InsightsContent(
    data: InsightsData,
    onPlaySong: (Long) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = AvaTokens.space20,
            end = AvaTokens.space20,
            top = AvaTokens.space8,
            bottom = AvaTokens.space24,
        ),
        verticalArrangement = Arrangement.spacedBy(AvaTokens.space24),
    ) {
        item(key = "header") {
            Column {
                Text(
                    text = stringResource(R.string.insights_title),
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.height(AvaTokens.space6))
                Text(
                    text = stringResource(R.string.insights_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item(key = "summary") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AvaTokens.space8),
            ) {
                SummaryCard(
                    label = stringResource(R.string.insights_today),
                    value = formatListeningTime(data.listenedTodayMs),
                    sub = stringResource(R.string.insights_plays_count, data.playsToday),
                    modifier = Modifier.weight(1f),
                    hero = true,
                )
                SummaryCard(
                    label = stringResource(R.string.insights_week),
                    value = formatListeningTime(data.listenedWeekMs),
                    sub = stringResource(R.string.insights_plays_count, data.playsWeek),
                    modifier = Modifier.weight(1f),
                )
                SummaryCard(
                    label = stringResource(R.string.insights_month),
                    value = formatListeningTime(data.listenedMonthMs),
                    sub = stringResource(R.string.insights_plays_count, data.playsMonth),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item(key = "trend") {
            InsightsSection(title = stringResource(R.string.insights_trend_title)) {
                SevenDayTrend(days = data.last7Days)
            }
        }

        item(key = "habits") {
            InsightsSection(title = stringResource(R.string.insights_habit_title)) {
                HabitRow(
                    icon = Icons.Filled.Schedule,
                    label = stringResource(R.string.insights_peak_hour),
                    value = data.peakHour?.let { stringResource(R.string.insights_hour_format, it) }
                        ?: "—",
                )
                HabitRow(
                    icon = Icons.Filled.Whatshot,
                    label = stringResource(R.string.insights_streak),
                    value = stringResource(R.string.insights_days_count, data.streakDays),
                )
                HabitRow(
                    icon = Icons.Filled.MusicNote,
                    label = stringResource(R.string.insights_total_plays),
                    value = stringResource(R.string.insights_plays_count, data.totalPlays),
                )
            }
        }

        if (data.topSongs.isNotEmpty()) {
            item(key = "top-songs-header") {
                Text(
                    text = stringResource(R.string.insights_top_songs),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            itemsIndexed(data.topSongs, key = { _, song -> "top-song-${song.songId}" }) { index, song ->
                TopSongRow(rank = index + 1, song = song, onClick = { onPlaySong(song.songId) })
            }
        }

        if (data.topArtists.isNotEmpty()) {
            item(key = "top-artists-header") {
                Text(
                    text = stringResource(R.string.insights_top_artists),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            items(data.topArtists, key = { "top-artist-${it.name}" }) { artist ->
                TopNamedRow(item = artist)
            }
        }

        if (data.topAlbums.isNotEmpty()) {
            item(key = "top-albums-header") {
                Text(
                    text = stringResource(R.string.insights_top_albums),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            items(data.topAlbums, key = { "top-album-${it.name}" }) { album ->
                TopNamedRow(item = album)
            }
        }

        if (data.topGenres.isNotEmpty()) {
            item(key = "top-genres-header") {
                Text(
                    text = stringResource(R.string.insights_top_genres),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            items(data.topGenres, key = { "top-genre-${it.name}" }) { genre ->
                TopNamedRow(item = genre)
            }
        }
    }
}

@Composable
private fun InsightsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AvaTokens.space12)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            shape = RoundedCornerShape(AvaTokens.radiusLarge),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(AvaTokens.space16)) {
                content()
            }
        }
    }
}

@Composable
private fun SummaryCard(
    label: String,
    value: String,
    sub: String,
    modifier: Modifier = Modifier,
    hero: Boolean = false,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (hero) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
        shape = RoundedCornerShape(AvaTokens.radiusLarge),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(AvaTokens.space12)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (hero) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Spacer(modifier = Modifier.height(AvaTokens.space4))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = if (hero) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = sub,
                style = MaterialTheme.typography.bodySmall,
                color = if (hero) {
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SevenDayTrend(days: List<DayTotal>) {
    val maxMs = days.maxOfOrNull { it.listenedMs }?.coerceAtLeast(1L) ?: 1L
    val dayFormat = SimpleDateFormat("d", Locale.getDefault())
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom,
    ) {
        days.forEach { day ->
            val fraction = (day.listenedMs.toFloat() / maxMs).coerceIn(0f, 1f)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Text(
                    text = formatListeningTime(day.listenedMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(AvaTokens.space4))
                Box(
                    modifier = Modifier
                        .width(28.dp)
                        .height((16 + fraction * 80).dp)
                        .clip(RoundedCornerShape(AvaTokens.radiusSmall))
                        .background(
                            if (day.listenedMs > 0L) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            },
                        ),
                )
                Spacer(modifier = Modifier.height(AvaTokens.space4))
                Text(
                    text = dayFormat.format(Date(day.dayStartMs)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HabitRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AvaTokens.space6),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(AvaTokens.space12))
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TopSongRow(
    rank: Int,
    song: TopSong,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AvaTokens.radiusMedium))
            .clickable(onClick = onClick)
            .padding(vertical = AvaTokens.space8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = rank.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(modifier = Modifier.width(AvaTokens.space12))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = song.artist.ifBlank { stringResource(R.string.insights_unknown_artist) },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = stringResource(R.string.insights_plays_count, song.plays),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = formatDuration(song.listenedMs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TopNamedRow(item: TopNamed) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AvaTokens.space8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatListeningTime(item.listenedMs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = stringResource(R.string.insights_plays_count, item.plays),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
