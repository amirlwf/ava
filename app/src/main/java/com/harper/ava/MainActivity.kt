package com.harper.ava

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.harper.ava.core.util.LocaleHelper
import com.harper.ava.core.util.StartupTracer
import com.harper.ava.data.preferences.ThemeMode
import com.harper.ava.data.preferences.UserPreferences
import com.harper.ava.ui.components.MiniPlayer
import com.harper.ava.ui.navigation.AvaNavGraph
import com.harper.ava.ui.navigation.Destination
import com.harper.ava.ui.navigation.NowPlayingRoute
import com.harper.ava.ui.navigation.OnboardingRoute
import com.harper.ava.ui.navigation.QueueRoute
import com.harper.ava.ui.splash.SplashScreen
import com.harper.ava.ui.theme.AvaTheme
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {

    /**
     * Applies the in-app language before anything inflates, so strings
     * resolve in the chosen language and the layout direction (RTL/LTR)
     * is right from the first frame. Reads the synchronous language
     * mirror — DataStore cannot be read from here.
     */
    override fun attachBaseContext(newBase: Context) {
        val language = UserPreferences.readLanguageSync(newBase)
        // 0.5.2: trace before the locale wrap — a crash here leaves no Activity.
        StartupTracer.mark(newBase, "activity.attachBaseContext language=$language")
        super.attachBaseContext(LocaleHelper.wrapContextWithLocale(newBase, language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as AvaApplication
        StartupTracer.mark(this, "activity.onCreate safeMode=${app.safeMode}")
        // 0.5.2: the previous run died during startup — show the minimal
        // safe-mode screen (plain Views: no Compose, no Room, no player,
        // no DataStore) instead of running the same startup again.
        if (app.safeMode) {
            showSafeModeScreen()
            return
        }
        enableEdgeToEdge()
        setContent {
            // Stage 6: wait for the persisted settings before composing the
            // graph — otherwise the first frame would use default settings
            // (wrong theme, wrong start destination) and flash. While
            // loading, the branded launch screen is shown (no fake delay).
            val settingsOrNull by app.container.userPreferences.settings
                .collectAsState(initial = null)
            // Plain local val (not a delegate) so the null check smart-casts.
            val settings = settingsOrNull
            if (settings == null) {
                AvaTheme { SplashScreen() }
                return@setContent
            }
            val playback by app.container.playerRepository.state
                .collectAsState()

            // Push playback settings into the player whenever they change.
            LaunchedEffect(settings.fadeEnabled) {
                app.container.playerRepository.setFadeEnabled(settings.fadeEnabled)
            }
            LaunchedEffect(settings.defaultSpeed) {
                app.container.playerRepository.setPlaybackSpeed(settings.defaultSpeed)
            }
            // One-shot resume: if enabled, restore the whole saved queue
            // (stage 5 — not just the last song) paused at its position
            // (no autoplay). Runs once per process. Songs deleted since
            // the checkpoint are dropped and the index is clamped.
            LaunchedEffect(Unit) {
                // 0.5.2: settings arrived and the first screen composed —
                // startup succeeded; clear the crash flag for next launch.
                StartupTracer.mark(this@MainActivity, "activity.settings.first")
                val stored = app.container.userPreferences.settings.first()
                val player = app.container.playerRepository
                if (stored.resumePlayback) {
                    val queueSongs = app.container.musicRepository
                        .getSongsByIds(stored.lastQueueIds)
                    if (queueSongs.isNotEmpty()) {
                        val index = stored.lastQueueIndex
                            .coerceIn(0, queueSongs.lastIndex)
                        player.prepareQueue(queueSongs, index, stored.lastPositionMs)
                    } else if (stored.lastSongId != null) {
                        // Pre-stage-5 checkpoint (queue only): one song.
                        val song = app.container.musicRepository
                            .getSongById(stored.lastSongId)
                        if (song != null) {
                            player.prepareSong(song, stored.lastPositionMs)
                        }
                    }
                }
                // Stage 6: the sleep timer is re-armed independently of the
                // resume-playback setting — a timer set before a restart
                // must not silently die just because resume is off.
                player.restoreSleepTimer(stored.sleepTimerEndsAtMs)
                // 0.5.2: first frame is up — startup succeeded.
                StartupTracer.markStartupComplete(this@MainActivity)
            }

            AvaTheme(
                themeMode = settings.themeMode,
                accent = settings.accent,
                customAccentArgb = settings.customAccentArgb,
            ) {
                val navController = rememberNavController()
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = backStackEntry?.destination?.route
                // Now Playing and Queue are full-screen: no bottom bar, no mini player.
                val isFullScreenRoute = currentRoute == NowPlayingRoute.ROUTE ||
                    currentRoute == QueueRoute.ROUTE

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        if (!isFullScreenRoute) {
                            Column {
                                if (playback.currentSong != null) {
                                    MiniPlayer(
                                        state = playback,
                                        onTogglePlayPause = { app.container.playerRepository.togglePlayPause() },
                                        onNext = { app.container.playerRepository.playNext() },
                                        onOpenNowPlaying = {
                                            navController.navigate(NowPlayingRoute.ROUTE)
                                        },
                                    )
                                }
                                NavigationBar {
                                    Destination.bottomBar.forEach { destination ->
                                        NavigationBarItem(
                                            selected = currentRoute == destination.route,
                                            onClick = {
                                                navController.navigate(destination.route) {
                                                    popUpTo(Destination.Home.route) { saveState = true }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            },
                                            icon = {
                                                Icon(
                                                    imageVector = destination.icon,
                                                    contentDescription = null,
                                                )
                                            },
                                            label = { Text(stringResource(destination.labelRes)) },
                                        )
                                    }
                                }
                            }
                        }
                    },
                ) { padding ->
                    AvaNavGraph(
                        app = app,
                        navController = navController,
                        modifier = Modifier.padding(padding),
                        // First run opens onboarding; afterwards Home.
                        startDestination = if (settings.onboardingSeen) {
                            Destination.Home.route
                        } else {
                            OnboardingRoute.ROUTE
                        },
                    )
                }
            }
        }
    }

    /**
     * 0.5.2 safe mode: the previous run crashed during startup, so the
     * normal path (Compose, Room, ExoPlayer, DataStore) is skipped
     * entirely. Plain Views only: the startup trace is shown, can be
     * shared as text, and the user can retry a normal start (which
     * restarts the process so Application.onCreate runs again).
     */
    private fun showSafeModeScreen() {
        // Export the trace first, so it exists even if this screen is closed.
        val publicPath = StartupTracer.exportPublic(
            this,
            StartupTracer.buildReport(this, null, null),
        )
        val trace = StartupTracer.readTrace(this)

        val scroll = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }
        fun textView(text: String, sizeSp: Float, bold: Boolean = false): TextView =
            TextView(this).apply {
                this.text = text
                setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
                if (bold) setTypeface(Typeface.DEFAULT_BOLD)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { bottomMargin = 24 }
            }
        fun button(text: String, onClick: () -> Unit): Button =
            Button(this).apply {
                this.text = text
                setOnClickListener { onClick() }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = 16 }
            }

        layout.addView(textView(getString(R.string.safe_mode_title), 20f, bold = true))
        layout.addView(textView(getString(R.string.safe_mode_message), 15f))
        val logLine = if (publicPath != null) {
            getString(R.string.safe_mode_log_saved, publicPath)
        } else {
            getString(R.string.safe_mode_no_log)
        }
        layout.addView(textView(logLine, 14f))
        layout.addView(
            textView(trace ?: getString(R.string.safe_mode_no_log), 12f).apply {
                typeface = Typeface.MONOSPACE
            },
        )
        layout.addView(button(getString(R.string.safe_mode_share_log)) {
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Ava startup log")
                putExtra(Intent.EXTRA_TEXT, StartupTracer.buildReport(this@MainActivity, null, null))
            }
            startActivity(Intent.createChooser(share, getString(R.string.safe_mode_share_log)))
        })
        layout.addView(button(getString(R.string.safe_mode_retry)) {
            // Clear the flag and restart the process so Application.onCreate
            // runs again with a normal startup.
            StartupTracer.markStartupComplete(this@MainActivity)
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(intent)
            Runtime.getRuntime().exit(0)
        })
        layout.addView(button(getString(R.string.safe_mode_reset)) {
            // 0.5.3: the crash may be caused by persisted data corrupted
            // by an earlier crashed run (Room DB, DataStore, preferences).
            // Wipe it all (the trace and crash logs are kept), clear the
            // flag and restart the process for a clean normal startup.
            val deleted = StartupTracer.resetAppData(this@MainActivity)
            StartupTracer.mark(this@MainActivity, "activity.safeMode.reset deleted=$deleted")
            StartupTracer.markStartupComplete(this@MainActivity)
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(intent)
            Runtime.getRuntime().exit(0)
        })
        scroll.addView(layout)
        setContentView(scroll)
        StartupTracer.mark(this, "activity.safeMode.shown")
    }
}
