package com.harper.ava.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.harper.ava.AvaApplication
import com.harper.ava.ui.details.SongDetailsScreen
import com.harper.ava.ui.equalizer.EqualizerScreen
import com.harper.ava.ui.favorites.FavoritesScreen
import com.harper.ava.ui.home.HomeScreen
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

@Composable
fun AvaNavGraph(
    app: AvaApplication,
    navController: NavHostController,
    modifier: Modifier = Modifier,
    startDestination: String = Destination.Home.route,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable(OnboardingRoute.ROUTE) {
            OnboardingScreen(app) {
                navController.navigate(Destination.Home.route) {
                    popUpTo(OnboardingRoute.ROUTE) { inclusive = true }
                }
            }
        }
        composable(Destination.Home.route) {
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
        composable(FavoritesRoute.ROUTE) {
            FavoritesScreen(
                app,
                onBack = { navController.popBackStack() },
                onOpenSongDetails = { songId ->
                    navController.navigate(SongDetailsRoute.create(songId))
                },
            )
        }
        composable(Destination.Library.route) {
            LibraryScreen(app) { songId ->
                navController.navigate(SongDetailsRoute.create(songId))
            }
        }
        composable(Destination.Search.route) {
            SearchScreen(app) { songId ->
                navController.navigate(SongDetailsRoute.create(songId))
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
        composable(NowPlayingRoute.ROUTE) {
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
            QueueScreen(app) { navController.popBackStack() }
        }
        composable(SettingsRoute.ROUTE) {
            SettingsScreen(
                app = app,
                onBack = { navController.popBackStack() },
                onOpenAppearance = { navController.navigate(AppearanceRoute.ROUTE) },
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
