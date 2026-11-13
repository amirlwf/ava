package com.harper.ava.ui.navigation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.harper.ava.AvaApplication
import com.harper.ava.ui.common.AvaMotion
import com.harper.ava.ui.details.SongDetailsScreen
import com.harper.ava.ui.equalizer.EqualizerScreen
import com.harper.ava.ui.favorites.FavoritesScreen
import com.harper.ava.ui.home.HomeScreen
import com.harper.ava.ui.insights.InsightsScreen
import com.harper.ava.ui.onboarding.OnboardingScreen
import com.harper.ava.ui.library.LibraryScreen
import com.harper.ava.ui.lyrics.LyricsScreen
import com.harper.ava.ui.nowplaying.NowPlayingScreen
import com.harper.ava.ui.playlists.PlaylistDetailScreen
import com.harper.ava.ui.playlists.PlaylistsScreen
import com.harper.ava.ui.queue.QueueScreen
import com.harper.ava.ui.search.SearchScreen
import com.harper.ava.ui.sleep.SleepTimerScreen
import com.harper.ava.ui.settings.AboutScreen
import com.harper.ava.ui.settings.AppearanceScreen
import com.harper.ava.ui.settings.LanguageScreen
import com.harper.ava.ui.settings.PlaybackSettingsScreen
import com.harper.ava.ui.settings.SettingsScreen
import com.harper.ava.ui.settings.StorageScreen
import com.harper.ava.ui.update.UpdateBanner

/**
 * Shared-element plumbing (0.7.0, spec §13).
 *
 * The NavHost lives inside a [SharedTransitionLayout], and every
 * destination that shows song covers provides two composition locals:
 * the shared-transition scope and that destination's animated-visibility
 * scope. [com.harper.ava.ui.components.SongRow] and the Now Playing
 * cover read them and attach `sharedElement` with the same key
 * ([songCoverSharedKey]) — so a tapped cover flies from its row into
 * the big Now Playing cover (and back), on a spring bounds transform.
 *
 * Destinations that do not provide the locals (Queue, PlaylistDetail,
 * SongDetails) simply render plain rows: the locals default to null and
 * SongRow skips the shared-element modifier. No destination was added
 * or removed here — only transitions and the locals.
 *
 * Known limit: the key is the song id, so a song that appears twice on
 * Home (recently played AND recently added) shares one key; the flight
 * may start from the other occurrence. It never crashes — the transition
 * just picks one visible element.
 */
val LocalAvaSharedTransitionScope =
    compositionLocalOf<SharedTransitionScope?> { null }
val LocalAvaAnimatedVisibilityScope =
    compositionLocalOf<AnimatedVisibilityScope?> { null }

/** Shared-element key for a song's cover — the same on both ends. */
fun songCoverSharedKey(songId: Long): String = "song-cover-$songId"

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AvaNavGraph(
    app: AvaApplication,
    navController: NavHostController,
    modifier: Modifier = Modifier,
    startDestination: String = Destination.Home.route,
) {
    SharedTransitionLayout {
        val sharedScope = this
        // 0.7.2: the update banner sits above the NavHost, so it is
        // visible on every screen until dismissed. The NavHost takes the
        // remaining space; the banner itself triggers the auto-check.
        Column(modifier = modifier) {
            UpdateBanner(app)
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.weight(1f),
            // Default destination transition (0.1): a short fade with a
            // gentle vertical rise — the screen drifts up 24dp while
            // fading in. Vertical only, on purpose: a horizontal slide
            // would fight the RTL layout direction (a slide that reads
            // as "forward" in LTR reads as "back" in RTL); a rise reads
            // the same in both directions. Now Playing keeps its
            // fade-only transition below (shared-element destination).
            enterTransition = {
                fadeIn(AvaMotion.navEnterFade) +
                    slideInVertically(
                        initialOffsetY = { it / 24 },
                        animationSpec = AvaMotion.navEnterSlide,
                    )
            },
            exitTransition = {
                fadeOut(AvaMotion.navExitFade) +
                    slideOutVertically(
                        targetOffsetY = { it / 12 },
                        animationSpec = AvaMotion.navExitSlide,
                    )
            },
            popEnterTransition = {
                fadeIn(AvaMotion.navEnterFade) +
                    slideInVertically(
                        initialOffsetY = { it / 24 },
                        animationSpec = AvaMotion.navEnterSlide,
                    )
            },
            popExitTransition = {
                fadeOut(AvaMotion.navExitFade) +
                    slideOutVertically(
                        targetOffsetY = { it / 12 },
                        animationSpec = AvaMotion.navExitSlide,
                    )
            },
        ) {
            composable(OnboardingRoute.ROUTE) {
                OnboardingScreen(app) {
                    navController.navigate(Destination.Home.route) {
                        popUpTo(OnboardingRoute.ROUTE) { inclusive = true }
                    }
                }
            }
            composable(Destination.Home.route) {
                CompositionLocalProvider(
                    LocalAvaSharedTransitionScope provides sharedScope,
                    LocalAvaAnimatedVisibilityScope provides this,
                ) {
                    HomeScreen(
                        app,
                        onOpenPlaylist = { playlistId ->
                            navController.navigate(PlaylistDetailRoute.create(playlistId))
                        },
                        onOpenSettings = { navController.navigate(SettingsRoute.ROUTE) },
                        onOpenSongDetails = { songId ->
                            navController.navigate(SongDetailsRoute.create(songId))
                        },
                        onOpenFavorites = { navController.navigate(FavoritesRoute.ROUTE) },
                    )
                }
            }
            composable(FavoritesRoute.ROUTE) {
                CompositionLocalProvider(
                    LocalAvaSharedTransitionScope provides sharedScope,
                    LocalAvaAnimatedVisibilityScope provides this,
                ) {
                    FavoritesScreen(
                        app,
                        onBack = { navController.popBackStack() },
                        onOpenSongDetails = { songId ->
                            navController.navigate(SongDetailsRoute.create(songId))
                        },
                    )
                }
            }
            composable(InsightsRoute.ROUTE) {
                InsightsScreen(app) { navController.popBackStack() }
            }
            composable(Destination.Library.route) {
                CompositionLocalProvider(
                    LocalAvaSharedTransitionScope provides sharedScope,
                    LocalAvaAnimatedVisibilityScope provides this,
                ) {
                    LibraryScreen(app) { songId ->
                        navController.navigate(SongDetailsRoute.create(songId))
                    }
                }
            }
            composable(Destination.Search.route) {
                CompositionLocalProvider(
                    LocalAvaSharedTransitionScope provides sharedScope,
                    LocalAvaAnimatedVisibilityScope provides this,
                ) {
                    SearchScreen(app) { songId ->
                        navController.navigate(SongDetailsRoute.create(songId))
                    }
                }
            }
            composable(Destination.Playlists.route) {
                PlaylistsScreen(app) { playlistId ->
                    navController.navigate(PlaylistDetailRoute.create(playlistId))
                }
            }
            composable(
                route = PlaylistDetailRoute.ROUTE,
                arguments = listOf(navArgument(PlaylistDetailRoute.ARG_ID) { type = NavType.LongType }),
            ) { backStackEntry ->
                val playlistId = backStackEntry.arguments?.getLong(PlaylistDetailRoute.ARG_ID) ?: 0L
                PlaylistDetailScreen(
                    app,
                    playlistId,
                    onBack = { navController.popBackStack() },
                    onOpenSongDetails = { songId ->
                        navController.navigate(SongDetailsRoute.create(songId))
                    },
                )
            }
            composable(
                route = NowPlayingRoute.ROUTE,
                // Fade only: the shared-element cover does the moving
                // here — a whole-screen scale on top would fight it.
                enterTransition = { fadeIn(AvaMotion.navEnterFade) },
                exitTransition = { fadeOut(AvaMotion.navExitFade) },
                popEnterTransition = { fadeIn(AvaMotion.navEnterFade) },
                popExitTransition = { fadeOut(AvaMotion.navExitFade) },
            ) {
                CompositionLocalProvider(
                    LocalAvaSharedTransitionScope provides sharedScope,
                    LocalAvaAnimatedVisibilityScope provides this,
                ) {
                    NowPlayingScreen(
                        app = app,
                        onBack = { navController.popBackStack() },
                        onOpenQueue = { navController.navigate(QueueRoute.ROUTE) },
                        onOpenSleepTimer = { navController.navigate(SleepTimerRoute.ROUTE) },
                        onOpenEqualizer = { navController.navigate(EqualizerRoute.ROUTE) },
                        onOpenLyrics = { navController.navigate(LyricsRoute.ROUTE) },
                        onOpenSongDetails = { songId ->
                            navController.navigate(SongDetailsRoute.create(songId))
                        },
                    )
                }
            }
            composable(SleepTimerRoute.ROUTE) {
                SleepTimerScreen(app) { navController.popBackStack() }
            }
            composable(EqualizerRoute.ROUTE) {
                EqualizerScreen(app) { navController.popBackStack() }
            }
            composable(LyricsRoute.ROUTE) {
                LyricsScreen(app) { navController.popBackStack() }
            }
            composable(
                route = SongDetailsRoute.ROUTE,
                arguments = listOf(navArgument(SongDetailsRoute.ARG_ID) { type = NavType.LongType }),
            ) { backStackEntry ->
                val songId = backStackEntry.arguments?.getLong(SongDetailsRoute.ARG_ID) ?: 0L
                SongDetailsScreen(app, songId) { navController.popBackStack() }
            }
            composable(QueueRoute.ROUTE) {
                QueueScreen(
                    app,
                    onBack = { navController.popBackStack() },
                    onOpenNowPlaying = { navController.navigate(NowPlayingRoute.ROUTE) },
                )
            }
            composable(SettingsRoute.ROUTE) {
                SettingsScreen(
                    app = app,
                    onBack = { navController.popBackStack() },
                    onOpenAppearance = { navController.navigate(AppearanceRoute.ROUTE) },
                    onOpenInsights = { navController.navigate(InsightsRoute.ROUTE) },
                    onOpenLanguage = { navController.navigate(LanguageRoute.ROUTE) },
                    onOpenPlayback = { navController.navigate(PlaybackSettingsRoute.ROUTE) },
                    onOpenStorage = { navController.navigate(StorageRoute.ROUTE) },
                    onOpenAbout = { navController.navigate(AboutRoute.ROUTE) },
                )
            }
            composable(AppearanceRoute.ROUTE) {
                AppearanceScreen(app) { navController.popBackStack() }
            }
            composable(LanguageRoute.ROUTE) {
                LanguageScreen(app) { navController.popBackStack() }
            }
            composable(PlaybackSettingsRoute.ROUTE) {
                PlaybackSettingsScreen(app) { navController.popBackStack() }
            }
            composable(StorageRoute.ROUTE) {
                StorageScreen(app) { navController.popBackStack() }
            }
            composable(AboutRoute.ROUTE) {
                AboutScreen { navController.popBackStack() }
            }
            }
        }
    }
}
