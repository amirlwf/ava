package com.harper.ava.data.repository

import android.app.RecoverableSecurityException
import android.content.Context
import android.content.IntentSender
import android.os.Build
import android.provider.MediaStore
import androidx.core.net.toUri
import com.harper.ava.domain.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Outcome of a song-delete attempt, shown to the user as a Toast. */
enum class SongDeleteOutcome { DELETED, CANCELLED, FAILED }

/**
 * Owns the 0.7.2 song-delete flow end to end.
 *
 * Why a process-wide object: the delete needs an
 * ActivityResultLauncher, which only MainActivity can own (the
 * ViewModels are built by the nav graph and never see the Activity),
 * while the trigger lives in five different screens' ViewModels.
 * MainActivity registers its launcher once ([attachLauncher]); the
 * ViewModels just call [requestDelete].
 *
 * Flow:
 * 1. The screen shows its own in-app confirm dialog first.
 * 2. API 30+: [MediaStore.createDeleteRequest] builds the system
 *    consent dialog; the launcher fires it. API <30: the file is
 *    deleted directly with [android.content.ContentResolver.delete].
 * 3. ONLY after the file is really gone: the song leaves the queue
 *    ([PlayerRepository.removeSongById] — a playing song advances to
 *    the next one, or playback stops when it was the only song) and
 *    the library ([MusicRepository.deleteSongFromLibrary] — song row
 *    + favorite; playlist memberships cascade, history is kept).
 *    Room flows re-emit, so every list refreshes by itself.
 * 4. A cancelled/failed system dialog changes NOTHING and emits
 *    [SongDeleteOutcome.CANCELLED] / [SongDeleteOutcome.FAILED].
 *
 * If the process dies while the system dialog is open, the pending
 * request is simply lost — nothing was deleted yet, so the library
 * stays consistent.
 */
object SongDeleteCoordinator {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _outcomes = MutableSharedFlow<SongDeleteOutcome>(extraBufferCapacity = 1)
    val outcomes: SharedFlow<SongDeleteOutcome> = _outcomes.asSharedFlow()

    private var launchDeleteRequest: ((IntentSender) -> Unit)? = null
    private var appContext: Context? = null

    /** A delete waiting on the system dialog's answer, if any. */
    private var pending: PendingDelete? = null

    private data class PendingDelete(
        val song: Song,
        val music: MusicRepository,
        val player: PlayerRepository,
    )

    /** Called once by MainActivity with its delete launcher. */
    fun attachLauncher(launch: (IntentSender) -> Unit) {
        launchDeleteRequest = launch
    }

    /** Called once by MainActivity so file deletes have a resolver. */
    fun attachContext(context: Context) {
        appContext = context.applicationContext
    }

    /**
     * Starts deleting [song] after the in-app confirm dialog. Called
     * from a ViewModel's scope. Emits the outcome for the UI Toast.
     */
    suspend fun requestDelete(song: Song, music: MusicRepository, player: PlayerRepository) {
        val context = appContext
        if (context == null) {
            _outcomes.emit(SongDeleteOutcome.FAILED)
            return
        }
        val uri = song.uri.toUri()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val request = try {
                MediaStore.createDeleteRequest(context.contentResolver, listOf(uri))
            } catch (e: Exception) {
                _outcomes.emit(SongDeleteOutcome.FAILED)
                return
            }
            val launch = launchDeleteRequest
            if (launch == null) {
                _outcomes.emit(SongDeleteOutcome.FAILED)
                return
            }
            pending = PendingDelete(song, music, player)
            launch(request.intentSender)
            return
        }
        // API <30: direct delete, no system dialog exists.
        val deleted = try {
            withContext(Dispatchers.IO) {
                context.contentResolver.delete(uri, null, null) > 0
            }
        } catch (e: RecoverableSecurityException) {
            // The file needs user consent after all — route it through
            // the same system dialog as API 30+.
            val launch = launchDeleteRequest
            if (launch == null) {
                _outcomes.emit(SongDeleteOutcome.FAILED)
                return
            }
            pending = PendingDelete(song, music, player)
            launch(e.userAction.actionIntent.intentSender)
            return
        } catch (e: Exception) {
            _outcomes.emit(SongDeleteOutcome.FAILED)
            return
        }
        if (deleted) {
            finishDelete(PendingDelete(song, music, player))
        } else {
            _outcomes.emit(SongDeleteOutcome.FAILED)
        }
    }

    /**
     * Called by MainActivity's launcher callback with the system
     * dialog's answer. `granted` is true only when the user confirmed.
     */
    fun onSystemDeleteResult(granted: Boolean) {
        val request = pending
        pending = null
        if (request == null) return
        scope.launch {
            if (granted) {
                finishDelete(request)
            } else {
                _outcomes.emit(SongDeleteOutcome.CANCELLED)
            }
        }
    }

    /**
     * Post-file-delete cleanup: queue first (a playing song advances
     * or playback stops), then the library rows. Only ever called
     * after the file is confirmed gone.
     */
    private suspend fun finishDelete(request: PendingDelete) {
        try {
            request.player.removeSongById(request.song.id)
            request.music.deleteSongFromLibrary(request.song.id)
            _outcomes.emit(SongDeleteOutcome.DELETED)
        } catch (e: Exception) {
            _outcomes.emit(SongDeleteOutcome.FAILED)
        }
    }
}
