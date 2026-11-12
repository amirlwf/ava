package com.harper.ava.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.vector.ImageVector
import com.harper.ava.R

sealed class Destination(val route: String, val labelRes: Int, val icon: ImageVector) {
    data object Home : Destination("home", R.string.nav_home, Icons.Filled.Home)
    data object Library : Destination("library", R.string.nav_library, Icons.Filled.LibraryMusic)
    data object Search : Destination("search", R.string.nav_search, Icons.Filled.Search)
    data object Playlists : Destination("playlists", R.string.nav_playlists, Icons.AutoMirrored.Filled.QueueMusic)

    companion object {
        val bottomBar: List<Destination> = listOf(Home, Library, Search, Playlists)
    }
}

/** Full-screen destinations (not in the bottom bar). */
object NowPlayingRoute {
    const val ROUTE = "now_playing"
}

object QueueRoute {
    const val ROUTE = "queue"
}

object PlaylistDetailRoute {
    const val ROUTE = "playlist/{playlistId}"
    const val ARG_ID = "playlistId"
    fun create(playlistId: Long): String = "playlist/$playlistId"
}

/** Settings hub + sub-screens (stage 4). Not in the bottom bar. */
object SettingsRoute {
    const val ROUTE = "settings"
}

object AppearanceRoute {
    const val ROUTE = "settings/appearance"
}

object LanguageRoute {
    const val ROUTE = "settings/language"
}

object PlaybackSettingsRoute {
    const val ROUTE = "settings/playback"
}

object StorageRoute {
    const val ROUTE = "settings/storage"
}

object AboutRoute {
    const val ROUTE = "settings/about"
}

/** Listening insights screen (0.7.0). Not in the bottom bar. */
object InsightsRoute {
    const val ROUTE = "insights"
}

/** Favorites screen (stage 6). Not in the bottom bar. */
object FavoritesRoute {
    const val ROUTE = "favorites"
}

/** First-run onboarding (stage 6). Start destination until seen. */
object OnboardingRoute {
    const val ROUTE = "onboarding"
}

/** Advanced screens (stage 5). Not in the bottom bar. */
object SleepTimerRoute {
    const val ROUTE = "sleep_timer"
}

object EqualizerRoute {
    const val ROUTE = "equalizer"
}

object LyricsRoute {
    const val ROUTE = "lyrics"
}

object SongDetailsRoute {
    const val ROUTE = "song_details/{songId}"
    const val ARG_ID = "songId"
    fun create(songId: Long): String = "song_details/$songId"
}
