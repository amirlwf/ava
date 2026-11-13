package com.harper.ava

import android.app.Activity
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.harper.ava.core.util.CrashReporter
import com.harper.ava.core.util.LocaleHelper
import com.harper.ava.core.util.StartupTracer
import com.harper.ava.core.util.rememberAvaHaptics
import com.harper.ava.data.preferences.ThemeMode
import com.harper.ava.data.preferences.UserPreferences
import com.harper.ava.data.repository.SongDeleteCoordinator
import com.harper.ava.ui.common.AvaMotion
import com.harper.ava.ui.components.MiniPlayer
import com.harper.ava.ui.navigation.AvaNavGraph
import com.harper.ava.ui.navigation.Destination
import com.harper.ava.ui.navigation.NowPlayingRoute
import com.harper.ava.ui.navigation.OnboardingRoute
import com.harper.ava.ui.navigation.QueueRoute
import com.harper.ava.ui.splash.SplashScreen
import com.harper.ava.ui.theme.AppBackdrop
import com.harper.ava.ui.theme.AvaTheme
import com.harper.ava.ui.theme.LocalAppThemeSpec
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : ComponentActivity() {

    companion object {
        /** Marks the first composition entry exactly once per process. */
        private val compositionEntryMarked = AtomicBoolean(false)

        /**
         * 0.5.7: the resume-restore below must run once per process.
         * LaunchedEffect(Unit) re-runs on every Activity recreate (e.g.
         * language change), and re-preparing the queue then clobbers the
         * currently playing queue/position and forces a buffering window
         * that can demote the foreground service.
         */
        private val resumeRestored = AtomicBoolean(false)
    }

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
        // 0.5.4: finer marks + a watchdog around the crash window
        // (activity.onCreate -> activity.settings.first). If the app hangs
        // waiting for settings, the watchdog marks keep appearing; if it
        // crashes, they stop at the crash point.
        lifecycleScope.launch {
            var waited = 0
            while (!StartupTracer.startupComplete && waited < 30) {
                delay(2000L)
                waited += 2
                // 0.5.6: re-check after the sleep — the first sleep ends
                // AFTER settings.first arrives (0.5.5 marked "still
                // waiting after 2s" at +2253ms although settings.first
                // had arrived at +1438ms).
                if (!StartupTracer.startupComplete) {
                    StartupTracer.mark(
                        this@MainActivity,
                        "watchdog: still waiting for settings.first after ${waited}s",
                    )
                }
            }
        }
        StartupTracer.mark(this, "activity.edgeToEdge.begin")
        try {
            enableEdgeToEdge()
        } catch (t: Throwable) {
            StartupTracer.markThrowable(this, "activity.edgeToEdge.error", t)
            throw t
        }
        StartupTracer.mark(this, "activity.edgeToEdge.done")
        setContent {
            if (compositionEntryMarked.compareAndSet(false, true)) {
                StartupTracer.mark(this@MainActivity, "composition.first.entry")
            }
            // Stage 6: wait for the persisted settings before composing the
            // graph — otherwise the first frame would use default settings
            // (wrong theme, wrong start destination) and flash. While
            // loading, the branded launch screen is shown.
            // 0.2: after the settings arrive, the 1-second intro plays
            // once per cold start (Harper 2026-11-13) before the graph —
            // the intro screen itself owns the ~1 s timer and calls back.
            // rememberSaveable (not remember): a rotation during the
            // intro must not replay it.
            var introDone by rememberSaveable { mutableStateOf(false) }
            val settingsOrNull by app.container.userPreferences.settings
                .collectAsState(initial = null)
            // Plain local val (not a delegate) so the null check smart-casts.
            val settings = settingsOrNull
            if (settings == null || !introDone) {
                AvaTheme {
                    SplashScreen(
                        ready = settings != null,
                        onIntroFinished = { introDone = true },
                    )
                }
                return@setContent
            }
            val playback by app.container.playerRepository.state
                .collectAsState()

            // 0.7.2 song delete: the system consent dialog (API 30+)
            // launcher. Only the Activity can own an
            // ActivityResultLauncher, so it lives here; the five screens'
            // ViewModels reach it through SongDeleteCoordinator.
            val deleteLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartIntentSenderForResult(),
            ) { result ->
                SongDeleteCoordinator.onSystemDeleteResult(
                    result.resultCode == Activity.RESULT_OK,
                )
            }
            LaunchedEffect(Unit) {
                SongDeleteCoordinator.attachContext(applicationContext)
                SongDeleteCoordinator.attachLauncher { sender ->
                    deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
                }
            }

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
                // 0.5.7: restore once per process (see resumeRestored).
                if (!resumeRestored.compareAndSet(false, true)) return@LaunchedEffect
                // 0.5.2: settings arrived and the first screen composed —
                // startup succeeded; clear the crash flag for next launch.
                StartupTracer.mark(this@MainActivity, "activity.settings.first")
                StartupTracer.mark(this@MainActivity, "activity.settings.collect.start")
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
                appTheme = settings.appTheme,
                accent = settings.accent,
                customAccentArgb = settings.customAccentArgb,
            ) {
                // 0.7.0 motion: theme changes glide instead of snapping
                // (AnimatedThemeColors below) and buzz once to confirm.
                AnimatedThemeColors {
                    val haptics = rememberAvaHaptics()
                    var firstThemeSeen by remember { mutableStateOf(true) }
                    LaunchedEffect(
                        settings.themeMode,
                        settings.accent,
                        settings.customAccentArgb,
                        settings.appTheme,
                    ) {
                        // The first emission is just the stored theme
                        // arriving — not a change, so no buzz.
                        if (firstThemeSeen) {
                            firstThemeSeen = false
                            return@LaunchedEffect
                        }
                        haptics.themeChange()
                    }
                    val navController = rememberNavController()
                    val backStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = backStackEntry?.destination?.route
                    // Now Playing and Queue are full-screen: no bottom bar, no mini player.
                    val isFullScreenRoute = currentRoute == NowPlayingRoute.ROUTE ||
                        currentRoute == QueueRoute.ROUTE

                    // 0.1-fix1: ONE persistent backdrop behind everything.
                    // The Galaxy / seasonal sky used to live inside Home
                    // and Now Playing, tied to each destination's resume
                    // state — tapping an icon (e.g. Settings) dropped the
                    // destination below RESUMED and the Canvas left
                    // composition instantly, so the background vanished
                    // mid-transition while the outgoing screen was still
                    // fading out. The sky now lives here, behind the
                    // NavHost and outside every nav transition, and only
                    // the app going to the background pauses it (frozen
                    // frame, never blank — see SeasonalDecor).
                    Box(modifier = Modifier.fillMaxSize()) {
                            AppBackdrop(
                                theme = settings.appTheme,
                                effectsEnabled = settings.seasonalEffectsEnabled,
                                modifier = Modifier.fillMaxSize(),
                                amoled = settings.themeMode == ThemeMode.AMOLED,
                            )
                            Scaffold(
                                modifier = Modifier.fillMaxSize(),
                                // Transparent: the backdrop above paints the
                                // theme background; an opaque Scaffold would
                                // hide the sky (and the glass aurora) behind
                                // the bottom bar.
                                containerColor = Color.Transparent,
                                bottomBar = {
                                if (!isFullScreenRoute) {
                                    Column {
                                        if (playback.currentSong != null) {
                                            MiniPlayer(
                                                state = playback,
                                                positionMs = app.container.playerRepository.positionMs,
                                                onTogglePlayPause = { app.container.playerRepository.togglePlayPause() },
                                                onNext = { app.container.playerRepository.playNext() },
                                                onOpenNowPlaying = {
                                                    navController.navigate(NowPlayingRoute.ROUTE)
                                                },
                                            )
                                        }
                                        // 0.1-fix1 (Track C): on the
                                        // glass theme a 1dp hairline
                                        // above the bar, like iOS's tab
                                        // bar edge — the bar itself is
                                        // translucent (0.55) so the
                                        // aurora shows through.
                                        if (LocalAppThemeSpec.current.isGlass) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(1.dp)
                                                    .background(
                                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                                    ),
                                            )
                                        }
                                        NavigationBar(
                                            containerColor = if (LocalAppThemeSpec.current.isGlass) {
                                                MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f)
                                            } else {
                                                MaterialTheme.colorScheme.surfaceContainer
                                            },
                                        ) {
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
                    } // Box: the persistent backdrop wraps the Scaffold.
                }
            }
        }
        StartupTracer.mark(this, "activity.setContent.called")
    }

    /**
     * Safe-mode typeface: Vazirmatn, loaded straight from res/font. The
     * safe-mode screen is plain Views on purpose (no Compose, no Room,
     * no DataStore, no player) and a font-resource load touches none of
     * those — it is the same file Compose loads for the normal UI. If
     * the font ever fails to load, fall back to the system default
     * rather than crashing the screen that exists to report crashes.
     * (0.7.0 font audit: this was the last in-app system-font surface
     * outside the notification, which SystemUI owns.)
     */
    private fun avaTypeface(bold: Boolean): Typeface =
        runCatching {
            ResourcesCompat.getFont(
                this,
                if (bold) R.font.vazirmatn_bold else R.font.vazirmatn_regular,
            )
        }.getOrNull() ?: if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT

    /**
     * 0.5.2 safe mode: the previous run crashed during startup, so the
     * normal path (Compose, Room, ExoPlayer, DataStore) is skipped
     * entirely. Plain Views only: the startup trace is shown, can be
     * shared as text, and the user can retry a normal start (which
     * restarts the process so Application.onCreate runs again).
     */
    private fun showSafeModeScreen() {
        // 0.5.4: export the trace to its OWN file. The 0.5.2/0.5.3 code
        // exported it to ava-crash-log.txt, which DELETED the crash
        // report (with the stack trace) that the crash handler had just
        // written there — that is why two crash reports arrived with no
        // stack trace at all.
        val publicPath = StartupTracer.exportTrace(
            this,
            StartupTracer.buildReport(this, null, null),
        )
        val trace = StartupTracer.readTrace(this)
        // 0.5.4: the last Java crash log (with the stack trace) survives
        // in internal storage even when the public file is gone — show it
        // here so it can be read and shared from safe mode.
        val crashLog = CrashReporter.readLatest(this)

        val scroll = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }
        fun textView(text: String, sizeSp: Float, bold: Boolean = false): TextView =
            TextView(this).apply {
                this.text = text
                setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
                typeface = avaTypeface(bold)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { bottomMargin = 24 }
            }
        fun button(text: String, onClick: () -> Unit): Button =
            Button(this).apply {
                this.text = text
                typeface = avaTypeface(false)
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
                typeface = avaTypeface(false)
            },
        )
        layout.addView(textView(getString(R.string.safe_mode_crash_log_title), 16f, bold = true))
        layout.addView(
            textView(
                crashLog?.take(8000) ?: getString(R.string.safe_mode_no_crash_log),
                12f,
            ).apply {
                typeface = avaTypeface(false)
            },
        )
        if (crashLog != null) {
            layout.addView(button(getString(R.string.safe_mode_share_crash)) {
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Ava crash log")
                    putExtra(Intent.EXTRA_TEXT, crashLog)
                }
                startActivity(Intent.createChooser(share, getString(R.string.safe_mode_share_crash)))
            })
        }
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

/**
 * 0.7.0 motion: animated theme switching. [AvaTheme] swaps the colour
 * scheme instantly, which reads as a flash when the user picks a new
 * theme or accent — so this wrapper re-provides the scheme with every
 * colour gliding to its new value on a 350ms tween
 * ([AvaMotion.themeColorTween]). It sits inside [AvaTheme]'s content,
 * so it follows whatever scheme the theme round builds (including a
 * custom accent colour), and it deliberately only re-provides the
 * colours: typography and shapes keep coming from [AvaTheme] untouched.
 *
 * Corner radii are NOT animated: they come from the theme spec's
 * [Shapes] ([themeShapes]), and animating them would need the theme
 * spec to expose animatable values — shapes snap on a theme change
 * while the colours glide. The navigation state is untouched: the
 * [androidx.navigation.NavHostController] lives inside this wrapper's
 * content, so a theme change never resets the back stack.
 */
@Composable
private fun AnimatedThemeColors(content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val animated = scheme.copy(
        primary = animatedThemeColor(scheme.primary),
        onPrimary = animatedThemeColor(scheme.onPrimary),
        primaryContainer = animatedThemeColor(scheme.primaryContainer),
        onPrimaryContainer = animatedThemeColor(scheme.onPrimaryContainer),
        inversePrimary = animatedThemeColor(scheme.inversePrimary),
        secondary = animatedThemeColor(scheme.secondary),
        onSecondary = animatedThemeColor(scheme.onSecondary),
        secondaryContainer = animatedThemeColor(scheme.secondaryContainer),
        onSecondaryContainer = animatedThemeColor(scheme.onSecondaryContainer),
        tertiary = animatedThemeColor(scheme.tertiary),
        onTertiary = animatedThemeColor(scheme.onTertiary),
        tertiaryContainer = animatedThemeColor(scheme.tertiaryContainer),
        onTertiaryContainer = animatedThemeColor(scheme.onTertiaryContainer),
        background = animatedThemeColor(scheme.background),
        onBackground = animatedThemeColor(scheme.onBackground),
        surface = animatedThemeColor(scheme.surface),
        onSurface = animatedThemeColor(scheme.onSurface),
        surfaceVariant = animatedThemeColor(scheme.surfaceVariant),
        onSurfaceVariant = animatedThemeColor(scheme.onSurfaceVariant),
        surfaceTint = animatedThemeColor(scheme.surfaceTint),
        inverseSurface = animatedThemeColor(scheme.inverseSurface),
        inverseOnSurface = animatedThemeColor(scheme.inverseOnSurface),
        error = animatedThemeColor(scheme.error),
        onError = animatedThemeColor(scheme.onError),
        errorContainer = animatedThemeColor(scheme.errorContainer),
        onErrorContainer = animatedThemeColor(scheme.onErrorContainer),
        outline = animatedThemeColor(scheme.outline),
        outlineVariant = animatedThemeColor(scheme.outlineVariant),
        scrim = animatedThemeColor(scheme.scrim),
        surfaceBright = animatedThemeColor(scheme.surfaceBright),
        surfaceDim = animatedThemeColor(scheme.surfaceDim),
        surfaceContainer = animatedThemeColor(scheme.surfaceContainer),
        surfaceContainerHigh = animatedThemeColor(scheme.surfaceContainerHigh),
        surfaceContainerHighest = animatedThemeColor(scheme.surfaceContainerHighest),
        surfaceContainerLow = animatedThemeColor(scheme.surfaceContainerLow),
        surfaceContainerLowest = animatedThemeColor(scheme.surfaceContainerLowest),
    )
    MaterialTheme(colorScheme = animated, content = content)
}

/** One theme colour, gliding to its target on the theme-change tween. */
@Composable
private fun animatedThemeColor(target: Color): Color =
    animateColorAsState(
        targetValue = target,
        animationSpec = AvaMotion.themeColorTween,
        label = "theme-color",
    ).value
