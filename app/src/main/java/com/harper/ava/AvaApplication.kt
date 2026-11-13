package com.harper.ava

import android.app.Application
import com.harper.ava.core.util.CrashReporter
import com.harper.ava.core.util.StartupTracer
import com.harper.ava.data.local.db.AppDatabase
import com.harper.ava.data.media.LyricsRepository
import com.harper.ava.data.media.MediaStoreScanner
import com.harper.ava.data.media.SafFolderScanner
import com.harper.ava.data.preferences.UserPreferences
import com.harper.ava.data.repository.MusicRepository
import com.harper.ava.data.repository.PlayerRepository
import com.harper.ava.data.update.UpdateManager

/** Manual dependency container (no DI framework in stage 1 — keep it explicit). */
class AppContainer(app: Application) {
    val database: AppDatabase = AppDatabase.get(app).also {
        StartupTracer.mark(app, "container.database.ok")
    }
    val musicRepository = MusicRepository(database).also {
        StartupTracer.mark(app, "container.musicRepo.ok")
    }
    // Preferences first: the player needs it for the resume checkpoint.
    val userPreferences = UserPreferences(app).also {
        StartupTracer.mark(app, "container.prefs.ok")
    }
    val playerRepository = run {
        // 0.5.2: mark before the constructor — Dispatchers.Main is touched
        // while building the scope, before the constructor body runs.
        StartupTracer.mark(app, "container.player.begin")
        PlayerRepository(app, userPreferences)
    }.also {
        StartupTracer.mark(app, "container.player.ok")
    }
    val mediaStoreScanner = MediaStoreScanner(app, musicRepository).also {
        StartupTracer.mark(app, "container.scanner.ok")
    }
    val lyricsRepository = LyricsRepository(app).also {
        StartupTracer.mark(app, "container.lyrics.ok")
    }
    val safFolderScanner = SafFolderScanner(app).also {
        StartupTracer.mark(app, "container.saf.ok")
    }
    // 0.7.2: auto-update check — shared by the banner (nav graph) and
    // Settings. Built last: it only reads preferences + network, and its
    // own IO scope means it can never block startup.
    val updateManager = UpdateManager(app, userPreferences).also {
        StartupTracer.mark(app, "container.update.ok")
    }
}

class AvaApplication : Application() {
    lateinit var container: AppContainer
        private set

    /**
     * True when the previous run died during startup (0.5.2 diagnostic):
     * the heavy container is skipped and MainActivity shows a minimal
     * safe-mode screen instead of running the same startup again.
     */
    var safeMode: Boolean = false
        private set

    override fun onCreate() {
        super.onCreate()
        // First: capture any crash (even one while building the container)
        // into an on-device report — there is no logcat access otherwise.
        CrashReporter.install(this)
        StartupTracer.mark(this, "app.onCreate")
        // If the flag is still there, the last run crashed before the first
        // screen composed: open safe mode instead of crashing the same way.
        safeMode = StartupTracer.beginStartup(this)
        if (!safeMode) {
            container = AppContainer(this)
            StartupTracer.mark(this, "app.container.done")
            // 0.3-feedback (Track 3): single launcher icon — re-enable
            // MainActivity on every start. A user who had picked the
            // purple/green alias has MainActivity DISABLED in
            // PackageManager, so sync it back (idempotent; see
            // [UserPreferences.ensureMainActivityLauncherEnabled]).
            runCatching {
                container.userPreferences.ensureMainActivityLauncherEnabled()
            }
        } else {
            StartupTracer.mark(this, "app.safeMode.skipContainer")
        }
    }
}
