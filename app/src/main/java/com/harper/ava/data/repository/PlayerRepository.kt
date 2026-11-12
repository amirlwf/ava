package com.harper.ava.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.harper.ava.core.util.SLEEP_TIMER_FADE_MS
import com.harper.ava.core.util.SleepTimerState
import com.harper.ava.core.util.StartupTracer
import com.harper.ava.core.util.needsPlayerRebuild
import com.harper.ava.core.util.songToMediaItem
import com.harper.ava.core.util.segmentListenedMs
import com.harper.ava.core.util.shouldRecordPlay
import com.harper.ava.data.local.db.AppDatabase
import com.harper.ava.data.repository.MusicRepository
import com.harper.ava.core.util.restoreSleepTimerMinutes
import com.harper.ava.core.util.insertPlayNext
import com.harper.ava.core.util.moveItem
import com.harper.ava.core.util.removeItemAt
import com.harper.ava.core.util.shuffleKeepingFirst
import com.harper.ava.core.util.sleepTimerRemainingMs
import com.harper.ava.data.audio.EqualizerController
import com.harper.ava.data.preferences.UserPreferences
import com.harper.ava.domain.model.Song
import com.harper.ava.playback.AvaPlaybackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class RepeatMode { OFF, ALL, ONE }

/** What the Equalizer screen shows (and whether the device really supports it). */
data class EqualizerState(
    val supported: Boolean = false,
    /** String-resource key for the failure reason, or null when supported. */
    val reasonKey: String? = "eq_reason_no_session",
    val enabled: Boolean = false,
    val bandCount: Int = 0,
    val centerFreqsHz: List<Int> = emptyList(),
    val minLevelDb: Float = -15f,
    val maxLevelDb: Float = 15f,
    val presets: List<String> = emptyList(),
    /** Selected preset index, or -1 for manual band levels. */
    val presetIndex: Int = -1,
    val bandLevelsDb: List<Float> = emptyList(),
)

data class PlaybackState(
    val currentSong: Song? = null,
    val queue: List<Song> = emptyList(),
    val queueIndex: Int = -1,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val playbackSpeed: Float = 1f,
)

/**
 * Real local-file playback backed by Media3 ExoPlayer.
 *
 * The player instance is owned here (application scope) so both the UI and
 * [com.harper.ava.playback.AvaPlaybackService] share the exact same player
 * through the MediaSession.
 */
class PlayerRepository(
    context: Context,
    private val userPreferences: UserPreferences? = null,
) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // 0.5.2 diagnostics: mark the two most crash-prone steps of startup
    // (Dispatchers.Main resolution and ExoPlayer construction).
    init {
        StartupTracer.mark(appContext, "player.constructor.begin")
    }

    /** The single ExoPlayer instance for the whole process. */
    val player: ExoPlayer = ExoPlayer.Builder(context.applicationContext).build().apply {
        StartupTracer.mark(appContext, "player.exoplayer.built")
        addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.update { it.copy(isPlaying = isPlaying) }
                // 0.7.0 Insights: a pause banks the listened time so far
                // into the open history row; a resume keeps banking into
                // the SAME row (a resume is not a new play).
                if (isPlaying) {
                    if (listenSong == null) {
                        _state.value.currentSong?.let { beginListen(it) }
                    } else {
                        resumeListen()
                    }
                } else {
                    pauseListen()
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val song = mediaItem?.mediaId?.toLongOrNull()?.let { id ->
                    _state.value.queue.firstOrNull { it.id == id }
                }
                _state.update {
                    it.copy(
                        currentSong = song,
                        queueIndex = song?.let { s -> it.queue.indexOf(s) } ?: -1,
                        durationMs = safeDuration(),
                    )
                }
                // 0.7.0 Insights: a new track while playing is a real
                // play — including auto-advance, Next/Previous and
                // notification/headset skips, which no ViewModel ever
                // sees. A transition while paused only closes the old
                // row (prepare/restore is not a play). A track that
                // played to its end (auto-advance) is credited its full
                // duration — see bankListen.
                if (song == null) {
                    closeListen()
                } else if (player.isPlaying) {
                    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                        closeListen(creditFullDuration = true)
                    }
                    beginListen(song)
                } else if (song.id != listenSong?.id) {
                    closeListen()
                }
                // New track while playing: checkpoint the resume position
                // (start of track). Skip when paused at 0 — that is
                // prepareSong() restoring a saved position, and saving 0
                // here would clobber the restored position.
                if (player.isPlaying || player.currentPosition > 0L) saveCheckpoint()
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                // The equalizer attaches to this session; a new session
                // (first play, or a new track after a release) needs a
                // re-attach with the persisted settings.
                refreshEqualizer()
            }
        })
    }

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var positionJob: Job? = null

    // ------------------------------------------------------------------
    // Listening history (0.7.0 Insights) — one row per real play.
    //
    // The repository is built lazily from the Room singleton (same
    // database the MusicRepository uses) so no constructor or container
    // change is needed. All history writes go through a single-threaded
    // scope so an insert and the finalize of the previous row can never
    // reorder. Every write is best-effort (runCatching): analytics must
    // never break playback.
    // ------------------------------------------------------------------

    private val historyRepository: MusicRepository by lazy {
        MusicRepository(AppDatabase.get(appContext))
    }
    private val listenScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    /** History row id of the play currently being listened to (0 = not stored yet). */
    private var listenHistoryId: Long = 0L
    private var listenSong: Song? = null
    /** Player position where the current uninterrupted stretch started. */
    private var listenSegmentStartMs: Long = 0L
    /** Listened time banked into the open row across pauses. */
    private var listenAccumulatedMs: Long = 0L
    /** Last recorded play, for the same-song replay dedupe. */
    private var lastRecordedSongId: Long? = null
    private var lastRecordedAtMs: Long = 0L

    /**
     * Starts recording a play of [song]: closes any open row, then
     * inserts a new history row. A same-song restart within 30s is
     * ignored (callback double-fire / player rebuild / resume), while a
     * genuine replay later still counts.
     */
    private fun beginListen(song: Song) {
        val now = System.currentTimeMillis()
        if (!shouldRecordPlay(lastRecordedSongId, lastRecordedAtMs, song.id, now)) return
        closeListen()
        lastRecordedSongId = song.id
        lastRecordedAtMs = now
        listenSong = song
        listenSegmentStartMs = player.currentPosition.coerceAtLeast(0L)
        listenAccumulatedMs = 0L
        listenScope.launch {
            runCatching { listenHistoryId = historyRepository.recordPlayStart(song, now) }
        }
    }

    /**
     * Banks the stretch listened since the last resume (or bank) into
     * the open row (absolute total, so pauses accumulate). The row stays
     * open: a later resume keeps banking into it. If the row id has not
     * been assigned yet, the accumulated time is kept and flushed on
     * the next bank.
     *
     * [creditFullDuration] is for a track that played to its end
     * (auto-advance): by the time the transition fires, the player
     * position already belongs to the NEW track, so the stretch math
     * would credit ~0 — the full duration is the honest value.
     */
    private fun bankListen(creditFullDuration: Boolean = false) {
        val song = listenSong ?: return
        if (creditFullDuration) {
            listenAccumulatedMs = maxOf(listenAccumulatedMs, song.durationMs)
        } else {
            listenAccumulatedMs += segmentListenedMs(
                listenSegmentStartMs,
                player.currentPosition,
                song.durationMs,
            )
        }
        listenSegmentStartMs = player.currentPosition.coerceAtLeast(0L)
        val id = listenHistoryId
        val total = listenAccumulatedMs
        if (id <= 0L) return
        listenScope.launch {
            runCatching { historyRepository.setHistoryListened(id, total) }
        }
    }

    /** A pause banks the listened time so far into the open history row. */
    private fun pauseListen() = bankListen()

    /** A resume continues the open row from the current position. */
    private fun resumeListen() {
        if (listenSong == null) return
        listenSegmentStartMs = player.currentPosition.coerceAtLeast(0L)
    }

    /** Banks the final stretch and closes the open row. */
    private fun closeListen(creditFullDuration: Boolean = false) {
        bankListen(creditFullDuration)
        listenHistoryId = 0L
        listenSong = null
        listenAccumulatedMs = 0L
        listenSegmentStartMs = 0L
    }

    // ------------------------------------------------------------------
    // Sleep timer — an absolute deadline owned by this repository, so it
    // keeps counting while the app is closed (playback is a foreground
    // service) and across Activity recreates. The deadline is persisted
    // in DataStore so a process restart can re-arm it (see
    // restoreSleepTimer). At the deadline the volume fades out over
    // SLEEP_TIMER_FADE_MS and playback pauses.
    // ------------------------------------------------------------------

    private val _sleepTimer = MutableStateFlow(SleepTimerState())
    val sleepTimerState: StateFlow<SleepTimerState> = _sleepTimer.asStateFlow()
    private var sleepJob: Job? = null
    private var sleepTickerJob: Job? = null

    fun setSleepTimer(durationMinutes: Int) {
        val minutes = durationMinutes.coerceIn(1, 480)
        startSleepTimer(
            System.currentTimeMillis() + minutes * 60_000L,
            durationMinutes = minutes,
        )
    }

    /** Re-arms a persisted deadline after a process restart (no-op when none/past). */
    fun restoreSleepTimer(endsAtMs: Long?) {
        if (endsAtMs == null) return
        val now = System.currentTimeMillis()
        if (endsAtMs <= now) {
            scope.launch { userPreferences?.setSleepTimerEndsAt(null) }
            return
        }
        // Duration is unknown after a restart; recover it from the
        // remaining time (rounded up) so the preset chip still highlights.
        val minutes = restoreSleepTimerMinutes(endsAtMs, now)
        startSleepTimer(endsAtMs, persist = false, durationMinutes = minutes)
    }

    private fun startSleepTimer(
        endsAtMs: Long,
        persist: Boolean = true,
        durationMinutes: Int = 0,
    ) {
        cancelSleepTimerJobs()
        _sleepTimer.value = SleepTimerState(
            active = true,
            endsAtMs = endsAtMs,
            remainingMs = sleepTimerRemainingMs(endsAtMs, System.currentTimeMillis()),
            durationMinutes = durationMinutes,
        )
        if (persist) scope.launch { userPreferences?.setSleepTimerEndsAt(endsAtMs) }
        sleepTickerJob = scope.launch {
            while (true) {
                delay(1_000L)
                val remaining = sleepTimerRemainingMs(endsAtMs, System.currentTimeMillis())
                _sleepTimer.update { it.copy(remainingMs = remaining) }
                if (remaining <= 0L) break
            }
        }
        sleepJob = scope.launch {
            val total = endsAtMs - System.currentTimeMillis()
            if (total <= 0L) {
                finishSleepTimer()
                return@launch
            }
            val fadeMs = minOf(SLEEP_TIMER_FADE_MS, total)
            delay((total - fadeMs).coerceAtLeast(0L))
            rampVolume(0f, fadeMs)
            if (player.isPlaying) player.pause()
            player.volume = 1f
            finishSleepTimer()
        }
    }

    private fun finishSleepTimer() {
        cancelSleepTimerJobs()
        _sleepTimer.value = SleepTimerState()
        scope.launch { userPreferences?.setSleepTimerEndsAt(null) }
        saveCheckpoint()
    }

    fun cancelSleepTimer() {
        cancelSleepTimerJobs()
        // A fade in progress must not leave the volume turned down.
        player.volume = 1f
        _sleepTimer.value = SleepTimerState()
        scope.launch { userPreferences?.setSleepTimerEndsAt(null) }
    }

    private fun cancelSleepTimerJobs() {
        sleepJob?.cancel()
        sleepJob = null
        sleepTickerJob?.cancel()
        sleepTickerJob = null
    }

    // ------------------------------------------------------------------
    // Equalizer — attached to ExoPlayer's audio session. The device is
    // asked, never assumed: when the effect is unavailable the state
    // reports supported = false and the UI says so honestly.
    // ------------------------------------------------------------------

    private val eqController = EqualizerController()
    private val _equalizer = MutableStateFlow(EqualizerState())
    val equalizerState: StateFlow<EqualizerState> = _equalizer.asStateFlow()

    /** (Re)attaches the equalizer and applies the persisted settings. */
    fun refreshEqualizer() {
        scope.launch {
            val prefs = userPreferences?.settings?.first() ?: return@launch
            applyEqualizerSettings(prefs.eqEnabled, prefs.eqPresetIndex, prefs.eqBandLevels)
        }
    }

    private fun applyEqualizerSettings(
        enabled: Boolean,
        presetIndex: Int,
        bandLevelsMillibels: List<Int>,
    ) {
        val support = eqController.ensure(
            player.audioSessionId,
            enabled,
            presetIndex,
            bandLevelsMillibels,
        )
        val levels = if (support.supported) eqController.currentBandLevelsMillibels() else emptyList()
        _equalizer.value = EqualizerState(
            supported = support.supported,
            reasonKey = support.reasonKey,
            enabled = enabled && support.supported,
            bandCount = support.bandCount,
            centerFreqsHz = support.centerFreqsHz,
            minLevelDb = support.minLevelDb,
            maxLevelDb = support.maxLevelDb,
            presets = support.presets,
            presetIndex = if (support.supported) presetIndex else -1,
            bandLevelsDb = levels.map { it / 100f },
        )
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        scope.launch {
            userPreferences?.setEqEnabled(enabled)
            val prefs = userPreferences?.settings?.first()
            applyEqualizerSettings(
                enabled,
                prefs?.eqPresetIndex ?: -1,
                prefs?.eqBandLevels ?: emptyList(),
            )
        }
    }

    /** Applies a device preset and persists it (with the levels it produces). */
    fun applyEqualizerPreset(presetIndex: Int) {
        scope.launch {
            val prefs = userPreferences?.settings?.first()
            eqController.ensure(player.audioSessionId, true, presetIndex, prefs?.eqBandLevels ?: emptyList())
            val levels = eqController.currentBandLevelsMillibels()
            userPreferences?.setEqEnabled(true)
            userPreferences?.setEqPreset(presetIndex, levels)
            applyEqualizerSettings(true, presetIndex, levels)
        }
    }

    /** Sets one band manually (dB); the preset becomes "Custom" (-1). */
    fun setEqualizerBand(band: Int, levelDb: Float) {
        scope.launch {
            val current = _equalizer.value
            if (band !in current.bandLevelsDb.indices) return@launch
            val levels = current.bandLevelsDb.toMutableList().also { it[band] = levelDb }
            val millibels = levels.map { (it * 100).toInt() }
            userPreferences?.setEqEnabled(true)
            userPreferences?.setEqBandLevels(millibels)
            applyEqualizerSettings(true, -1, millibels)
        }
    }

    /** Resets all bands to 0 dB (manual mode, keeps the on/off state). */
    fun resetEqualizerBands() {
        scope.launch {
            val current = _equalizer.value
            if (current.bandCount <= 0) return@launch
            val zeros = List(current.bandCount) { 0 }
            userPreferences?.setEqBandLevels(zeros)
            applyEqualizerSettings(current.enabled, -1, zeros)
        }
    }

    /** ExoPlayer reports TIME_UNSET (-2^63+1) for an unknown duration; never leak that into state. */
    private fun safeDuration(): Long = player.duration.takeIf { it > 0L } ?: 0L

    /**
     * Improve loop, run 1: media items now carry title / artist / album
     * / artwork in their MediaMetadata (see `core/util/MediaItemLogic`),
     * so the notification and lock screen show the song instead of
     * blank lines. Uri + media id are unchanged.
     */
    private fun Song.toMediaItem(): MediaItem = songToMediaItem(this)

    init {
        positionJob = scope.launch {
            var listenBankTicks = 0
            while (true) {
                delay(500L)
                val current = player.currentMediaItem
                if (current != null && player.isPlaying) {
                    _state.update {
                        it.copy(positionMs = player.currentPosition, durationMs = safeDuration())
                    }
                    // 0.7.0 Insights: bank the listened time every ~10s
                    // while playing, so a skip or an auto-advance never
                    // loses more than one bank interval (the position at
                    // a track change already belongs to the new track).
                    listenBankTicks++
                    if (listenBankTicks % 20 == 0) bankListen()
                }
            }
        }
        StartupTracer.mark(appContext, "player.constructor.done")
    }

    /**
     * (Re)starts the playback service so every playback start gets a
     * fresh foreground start. 0.5.7: the service foregrounds itself
     * synchronously in onStartCommand, so this is cheap and idempotent
     * — but it must be called from EVERY path that starts playback
     * (playSongs, playQueueItem, togglePlayPause), not just playSongs:
     * after a pause Media3 demotes the service, and a later play from
     * the Mini Player or the queue would otherwise start playback
     * with no service at all.
     */
    private fun ensurePlaybackService() {
        appContext.startForegroundService(Intent(appContext, AvaPlaybackService::class.java))
    }

    fun playSongs(songs: List<Song>, startIndex: Int = 0) {
        if (songs.isEmpty()) return
        val index = startIndex.coerceIn(0, songs.lastIndex)
        val items = songs.map { it.toMediaItem() }
        player.setMediaItems(items, index, 0L)
        player.prepare()
        if (fadeEnabled) player.volume = 0f
        player.play()
        if (fadeEnabled) fadeVolumeTo(1f, 700L)
        // Bring up the MediaSessionService so playback survives the UI and
        // the system (notification / lock screen / headset) can control it.
        ensurePlaybackService()
        _state.update {
            it.copy(queue = songs, queueIndex = index, currentSong = songs[index])
        }
        refreshEqualizer()
        beginListen(songs[index])
        saveCheckpoint()
    }

    fun play(song: Song) = playSongs(listOf(song), 0)

    /**
     * Prepares one song paused at [positionMs] without starting playback.
     * Used by resume-playback on app restart: the user sees the last song
     * ready in the Mini Player and presses play when they want it.
     */
    fun prepareSong(song: Song, positionMs: Long) {
        player.setMediaItems(listOf(song.toMediaItem()))
        player.prepare()
        player.seekTo(positionMs.coerceAtLeast(0L))
        val position = positionMs.coerceAtLeast(0L)
        _state.update {
            it.copy(
                queue = listOf(song),
                queueIndex = 0,
                currentSong = song,
                durationMs = safeDuration(),
                positionMs = position,
            )
        }
        refreshEqualizer()
    }

    /**
     * Prepares a whole queue paused at [positionMs] inside [startIndex]
     * without starting playback. Used by resume-playback: the full queue
     * comes back, not just one song. Songs missing from the library were
     * already dropped by the caller.
     */
    fun prepareQueue(songs: List<Song>, startIndex: Int = 0, positionMs: Long = 0L) {
        if (songs.isEmpty()) return
        val index = startIndex.coerceIn(0, songs.lastIndex)
        val position = positionMs.coerceAtLeast(0L)
        player.setMediaItems(songs.map { it.toMediaItem() }, index, position)
        player.prepare()
        _state.update {
            it.copy(
                queue = songs,
                queueIndex = index,
                currentSong = songs[index],
                durationMs = safeDuration(),
                positionMs = position,
            )
        }
        refreshEqualizer()
    }

    // ------------------------------------------------------------------
    // Queue management — every op mirrors the same change into ExoPlayer
    // and into the StateFlow, so the Queue screen and the player can never
    // disagree about the order.
    // ------------------------------------------------------------------

    /**
     * Rebuilds the player's playlist from the repository state after the
     * player was reset (round 8). ExoPlayer.stop() leaves the player
     * IDLE with no current media item, and the playback service used to
     * call stop() in onDestroy — the service is destroyed when the user
     * dismisses the media notification, so after a dismiss the queue,
     * the index and the position still lived here while the player had
     * nothing to play, and play() was a silent no-op until a new song
     * was picked. No-op when the player is healthy (items present and
     * not idle), so normal playback never pays for it.
     */
    private fun ensurePlayable() {
        val current = _state.value
        if (!needsPlayerRebuild(player.mediaItemCount, player.playbackState == Player.STATE_IDLE, current.queue.size)) return
        val index = current.queueIndex.coerceIn(0, current.queue.lastIndex)
        val position = current.positionMs.coerceAtLeast(0L)
        player.setMediaItems(current.queue.map { it.toMediaItem() }, index, position)
        player.prepare()
    }

    /** Jumps to a queue item and starts it. */
    fun playQueueItem(index: Int) {
        if (index !in _state.value.queue.indices) return
        ensurePlayable()
        player.seekTo(index, 0L)
        player.play()
        ensurePlaybackService()
        beginListen(_state.value.queue[index])
        saveCheckpoint()
    }

    /** Adds a song right after the current one (Play Next) or at the end (Play Last). */
    fun addToQueue(song: Song, playNext: Boolean) {
        val current = _state.value
        if (current.queue.isEmpty()) {
            play(song)
            return
        }
        val at = if (playNext) (current.queueIndex + 1).coerceIn(0, current.queue.size) else current.queue.size
        player.addMediaItem(at, song.toMediaItem())
        val newQueue = if (playNext) {
            insertPlayNext(current.queue, current.queueIndex, song)
        } else {
            current.queue + song
        }
        _state.update { it.copy(queue = newQueue) }
        saveCheckpoint()
    }

    /**
     * Removes a song from the queue by id (0.7.2 song delete). A no-op
     * when the song is not queued. When it IS the playing song,
     * [removeFromQueue] applies: ExoPlayer advances to the next item
     * (onMediaItemTransition fixes currentSong) — or stops playback
     * when it was the only queued song. The checkpoint is saved, so a
     * later resume never resurrects the deleted song.
     */
    fun removeSongById(songId: Long) {
        val index = _state.value.queue.indexOfFirst { it.id == songId }
        if (index >= 0) removeFromQueue(index)
    }

    /** Removes one queue item. Removing the only item stops playback. */
    fun removeFromQueue(index: Int) {
        val current = _state.value
        if (index !in current.queue.indices) return
        if (current.queue.size <= 1) {
            stop()
            return
        }
        val removedCurrent = index == current.queueIndex
        player.removeMediaItem(index)
        val newQueue = removeItemAt(current.queue, index)
        _state.update {
            it.copy(
                queue = newQueue,
                // If the playing song was removed, ExoPlayer transitions to
                // the next item and onMediaItemTransition fixes currentSong.
                currentSong = if (removedCurrent) null else it.currentSong,
                queueIndex = if (removedCurrent) -1 else newQueue.indexOf(it.currentSong),
            )
        }
        saveCheckpoint()
    }

    /** Reorders the queue (drag & drop). The playing song keeps playing. */
    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val current = _state.value
        if (fromIndex !in current.queue.indices || toIndex !in current.queue.indices) return
        if (fromIndex == toIndex) return
        player.moveMediaItem(fromIndex, toIndex)
        val newQueue = moveItem(current.queue, fromIndex, toIndex)
        _state.update {
            it.copy(
                queue = newQueue,
                queueIndex = newQueue.indexOf(it.currentSong),
            )
        }
        saveCheckpoint()
    }

    /**
     * Shuffles everything after the currently playing song; the current
     * song stays first and keeps playing. Applied with moveMediaItem calls
     * so playback is never restarted.
     */
    fun shuffleQueue() {
        val current = _state.value
        if (current.queue.size <= 2 || current.queueIndex < 0) return
        val head = current.queue.take(current.queueIndex + 1)
        val shuffledTail = shuffleKeepingFirst(
            listOf(current.queue[current.queueIndex]) + current.queue.drop(current.queueIndex + 1),
        ).drop(1)
        val targetOrder = head + shuffledTail
        // Apply the target order with moves, tracking a working copy that
        // mirrors the player's playlist order.
        val working = current.queue.toMutableList()
        for (position in current.queueIndex + 1 until targetOrder.size) {
            val wanted = targetOrder[position]
            val from = working.indexOf(wanted)
            if (from != position) {
                player.moveMediaItem(from, position)
                working.add(position, working.removeAt(from))
            }
        }
        _state.update { it.copy(queue = working) }
        saveCheckpoint()
    }

    /** Clears every queued song except the one currently playing. */
    fun clearQueue() {
        val current = _state.value
        if (current.queue.isEmpty()) return
        if (current.currentSong == null) {
            stop()
            return
        }
        // Remove the tail first, then the head: indices below queueIndex
        // stay valid while the tail is removed.
        for (index in current.queue.lastIndex downTo current.queueIndex + 1) {
            player.removeMediaItem(index)
        }
        for (index in current.queueIndex - 1 downTo 0) {
            player.removeMediaItem(index)
        }
        val song = current.currentSong
        _state.update { it.copy(queue = listOf(song), queueIndex = 0) }
        saveCheckpoint()
    }

    /** Playback speed in 0.5x–2.0x; stored in state so the UI can show it. */
    fun setPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 2.0f)
        player.playbackParameters = PlaybackParameters(clamped)
        _state.update { it.copy(playbackSpeed = clamped) }
    }

    fun togglePlayPause() {
        if (player.isPlaying) {
            if (fadeEnabled) {
                // Ramp down, then pause; restore full volume for the next play.
                fadeVolumeTo(0f, 400L) {
                    player.pause()
                    player.volume = 1f
                    saveCheckpoint()
                }
            } else {
                player.pause()
                saveCheckpoint()
            }
        } else {
            // Round 8: the player may have been reset (service destroyed
            // on notification dismiss) — rebuild it from the saved queue
            // first, or play() below is a silent no-op.
            ensurePlayable()
            // 0.5.7: play from the Mini Player / Now Playing must also
            // (re)start the service — after a pause Media3 demotes it,
            // and playing without a service leaves playback with no
            // foreground service and no notification.
            ensurePlaybackService()
            if (fadeEnabled) {
                player.volume = 0f
                player.play()
                fadeVolumeTo(1f, 700L)
            } else {
                player.play()
            }
        }
    }

    fun playNext() {
        ensurePlayable()
        if (player.hasNextMediaItem()) player.seekToNextMediaItem()
    }

    fun playPrevious() {
        ensurePlayable()
        if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem()
    }

    fun seekTo(positionMs: Long) {
        // Round 8: rebuild first — on a reset player this seek (and the
        // position update below) would silently do nothing.
        ensurePlayable()
        // Stage 6: an unknown duration used to clamp every seek to 0
        // (duration TIME_UNSET -> max 0), so the seekbar was dead on files
        // whose duration ExoPlayer had not resolved yet. ExoPlayer clamps
        // out-of-range seeks itself when the duration is unknown.
        val max = player.duration
        val target = if (max > 0L) {
            positionMs.coerceIn(0L, max)
        } else {
            positionMs.coerceAtLeast(0L)
        }
        player.seekTo(target)
        // After a rebuild the player is still preparing, so
        // currentPosition reads 0 even though the seek will land at
        // target — keep the requested target instead of clobbering the
        // resume position with 0.
        _state.update { it.copy(positionMs = player.currentPosition.takeIf { it > 0L } ?: target) }
    }

    fun toggleShuffle() {
        val enabled = !_state.value.shuffleEnabled
        player.shuffleModeEnabled = enabled
        _state.update { it.copy(shuffleEnabled = enabled) }
    }

    fun cycleRepeatMode() {
        val next = when (_state.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        player.repeatMode = when (next) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
        }
        _state.update { it.copy(repeatMode = next) }
    }

    fun stop() {
        closeListen()
        saveCheckpoint()
        player.stop()
        _state.update { PlaybackState() }
    }

    // ------------------------------------------------------------------
    // Fade in / out (real volume ramps on the player, in-app controls)
    // ------------------------------------------------------------------

    var fadeEnabled: Boolean = false
        private set

    fun setFadeEnabled(enabled: Boolean) {
        fadeEnabled = enabled
        if (!enabled) {
            fadeJob?.cancel()
            fadeJob = null
            player.volume = 1f
        }
    }

    private var fadeJob: Job? = null

    /** Suspends while ramping the player volume to [target] over [durationMs]. */
    private suspend fun rampVolume(target: Float, durationMs: Long) {
        val start = player.volume
        val steps = 14
        val stepMs = (durationMs / steps).coerceAtLeast(16L)
        for (i in 1..steps) {
            player.volume = start + (target - start) * i / steps
            delay(stepMs)
        }
        player.volume = target
    }

    /** Ramps the player volume to [target] over [durationMs], then runs [onDone]. */
    private fun fadeVolumeTo(target: Float, durationMs: Long, onDone: (() -> Unit)? = null) {
        fadeJob?.cancel()
        fadeJob = scope.launch {
            rampVolume(target, durationMs)
            onDone?.invoke()
        }
    }

    /**
     * Persists the current song + position + full queue for resume-playback.
     * Called on track change / pause / stop and on queue changes. No-op
     * when no song is loaded or when no preferences store was injected
     * (unit-test use).
     */
    private fun saveCheckpoint() {
        val prefs = userPreferences ?: return
        val song = _state.value.currentSong ?: return
        val position = player.currentPosition.coerceAtLeast(0L)
        val queueIds = _state.value.queue.map { it.id }
        val queueIndex = _state.value.queueIndex.coerceAtLeast(0)
        scope.launch { prefs.setLastPlayback(song.id, position, queueIds, queueIndex) }
    }

    /**
     * Persists the resume checkpoint now. The playback service calls
     * this in onDestroy (round 8): the position is still valid there,
     * and the next in-app play rebuilds from it.
     */
    fun saveCheckpointNow() {
        saveCheckpoint()
    }

    fun release() {
        cancelSleepTimerJobs()
        positionJob?.cancel()
        eqController.release()
        player.release()
    }

    companion object {
        @Suppress("unused")
        fun uriOf(song: Song): Uri = song.uri.toUri()
    }
}
