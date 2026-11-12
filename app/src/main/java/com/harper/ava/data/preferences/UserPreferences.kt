package com.harper.ava.data.preferences

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.harper.ava.core.util.LocaleHelper
import com.harper.ava.core.util.StartupTracer
import com.harper.ava.core.util.clampPlaybackSpeed
import com.harper.ava.core.util.decodeQueueIds
import com.harper.ava.core.util.decodeRecentSearches
import com.harper.ava.core.util.encodeQueueIds
import com.harper.ava.core.util.encodeRecentSearches
import com.harper.ava.core.util.recordSearch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, DARK, LIGHT, AMOLED }

enum class Accent { VIOLET, ELECTRIC_BLUE, EMERALD, CRIMSON, AMBER, ROSE, MONOCHROME }

/**
 * 0.7.0: the eight full Ava themes (see `ui/theme/AppTheme.kt` for the
 * spec behind each one). A theme replaces the old [Accent] preset as the
 * colour source; the stored [Accent] value is kept for compatibility but
 * no longer drives any colour. Default VIOLET keeps the exact 0.6.0 look.
 */
enum class AppTheme { SPRING, SUMMER, AUTUMN, WINTER, EMERALD, VIOLET, OCEAN, SUNSET }

enum class AppLanguage { SYSTEM, FA, EN }

/**
 * Launcher-icon variant, applied with an activity-alias + PackageManager
 * switch (see [UserPreferences.setAppIcon]). DEFAULT is the real
 * MainActivity launcher entry; every other value is an activity-alias
 * whose `targetActivity` is MainActivity. Only preset variants exist:
 * Android has no supported way to install a user-uploaded image as a
 * launcher icon, so no custom-upload variant is offered.
 */
enum class AppIcon {
    DEFAULT, EMERALD, SUNSET, OCEAN, SPRING, MONO;

    /** Manifest `android:name` of the activity-alias, or null for DEFAULT. */
    val aliasName: String?
        get() = when (this) {
            DEFAULT -> null
            EMERALD -> ".LauncherEmerald"
            SUNSET -> ".LauncherSunset"
            OCEAN -> ".LauncherOcean"
            SPRING -> ".LauncherSpring"
            MONO -> ".LauncherMono"
        }
}

data class SettingsState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accent: Accent = Accent.VIOLET,
    /** 0.7.0: the chosen full theme (palette + gradient + shapes). */
    val appTheme: AppTheme = AppTheme.VIOLET,
    /** User-picked accent color (ARGB int) or null to use the [accent] palette. */
    val customAccentArgb: Int? = null,
    val language: AppLanguage = AppLanguage.SYSTEM,
    /** Launcher icon variant (activity-alias). DEFAULT = MainActivity itself. */
    val appIcon: AppIcon = AppIcon.DEFAULT,
    /** Default playback speed applied when playback starts (0.5x–2.0x). */
    val defaultSpeed: Float = 1.0f,
    /** Restore the last song + position (paused) when the app restarts. */
    val resumePlayback: Boolean = true,
    /** Fade the volume in on play and out on in-app pause. */
    val fadeEnabled: Boolean = false,
    /** Last played song id, for resume. Null when nothing was ever played. */
    val lastSongId: Long? = null,
    val lastPositionMs: Long = 0L,
    /** The full queue at checkpoint time (song ids, in order) — stage 5. */
    val lastQueueIds: List<Long> = emptyList(),
    /** Index of the last song inside [lastQueueIds]. */
    val lastQueueIndex: Int = 0,
    /** Sleep-timer deadline (epoch ms) or null when no timer is set. */
    val sleepTimerEndsAtMs: Long? = null,
    /** Equalizer: on/off, selected preset index (-1 = manual bands). */
    val eqEnabled: Boolean = false,
    val eqPresetIndex: Int = -1,
    /** Equalizer band levels in millibels, comma-separated in storage. */
    val eqBandLevels: List<Int> = emptyList(),
    /** SAF music-folder tree URIs picked by the user (one per line). */
    val musicFolderUris: List<String> = emptyList(),
    /** Recent search queries, most recent first (max 8) — stage 6. */
    val recentSearches: List<String> = emptyList(),
    /** First-run onboarding completed (stage 6). */
    val onboardingSeen: Boolean = false,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "ava_settings",
    // 0.5.4: a preferences file left half-written by an earlier crashed
    // run must not kill the app on the next launch — replace it with
    // empty preferences (defaults) instead of throwing CorruptionException.
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/**
 * User settings persisted with DataStore Preferences. Survives app restarts.
 *
 * One exception: the language is *also* mirrored into a tiny synchronous
 * SharedPreferences file (`ava_sync_prefs`). `attachBaseContext` cannot
 * suspend to read DataStore, so the mirror is what MainActivity reads to
 * apply the locale before any UI is inflated. The mirror is written in
 * the same [setLanguage] call (synchronous commit first, then the
 * DataStore edit), so the two can never disagree by more than one call.
 */
class UserPreferences(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ACCENT = stringPreferencesKey("accent")
        val APP_THEME = stringPreferencesKey("app_theme")
        val CUSTOM_ACCENT_ARGB = longPreferencesKey("custom_accent_argb")
        val LANGUAGE = stringPreferencesKey("language")
        val APP_ICON = stringPreferencesKey("app_icon")
        val DEFAULT_SPEED = floatPreferencesKey("default_speed")
        val RESUME_PLAYBACK = booleanPreferencesKey("resume_playback")
        val FADE_ENABLED = booleanPreferencesKey("fade_enabled")
        val LAST_SONG_ID = longPreferencesKey("last_song_id")
        val LAST_POSITION_MS = longPreferencesKey("last_position_ms")
        val LAST_QUEUE_IDS = stringPreferencesKey("last_queue_ids")
        val LAST_QUEUE_INDEX = longPreferencesKey("last_queue_index")
        val SLEEP_TIMER_ENDS_AT = longPreferencesKey("sleep_timer_ends_at")
        val EQ_ENABLED = booleanPreferencesKey("eq_enabled")
        val EQ_PRESET_INDEX = longPreferencesKey("eq_preset_index")
        val EQ_BAND_LEVELS = stringPreferencesKey("eq_band_levels")
        val MUSIC_FOLDER_URIS = stringPreferencesKey("music_folder_uris")
        val RECENT_SEARCHES = stringPreferencesKey("recent_searches")
        val ONBOARDING_SEEN = booleanPreferencesKey("onboarding_seen")
    }

    val settings: Flow<SettingsState> = context.dataStore.data
        .catch { e ->
            // 0.5.4: belt and braces — any read error (not just a corrupt
            // file) is recorded in the startup trace and the app starts
            // with default settings instead of dying before the first frame.
            StartupTracer.mark(context, "settings.flow.error ${e::class.simpleName}: ${e.message}")
            emit(emptyPreferences())
        }
        .map { prefs ->
        SettingsState(
            themeMode = prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            accent = prefs[Keys.ACCENT]?.let { runCatching { Accent.valueOf(it) }.getOrNull() }
                ?: Accent.VIOLET,
            appTheme = prefs[Keys.APP_THEME]?.let { runCatching { AppTheme.valueOf(it) }.getOrNull() }
                ?: AppTheme.VIOLET,
            customAccentArgb = prefs[Keys.CUSTOM_ACCENT_ARGB]?.toInt(),
            language = prefs[Keys.LANGUAGE]?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() }
                ?: AppLanguage.SYSTEM,
            appIcon = prefs[Keys.APP_ICON]?.let { runCatching { AppIcon.valueOf(it) }.getOrNull() }
                ?: AppIcon.DEFAULT,
            defaultSpeed = prefs[Keys.DEFAULT_SPEED]?.let { clampPlaybackSpeed(it) } ?: 1.0f,
            resumePlayback = prefs[Keys.RESUME_PLAYBACK] ?: true,
            fadeEnabled = prefs[Keys.FADE_ENABLED] ?: false,
            lastSongId = prefs[Keys.LAST_SONG_ID],
            lastPositionMs = prefs[Keys.LAST_POSITION_MS]?.coerceAtLeast(0L) ?: 0L,
            lastQueueIds = decodeQueueIds(prefs[Keys.LAST_QUEUE_IDS]),
            lastQueueIndex = prefs[Keys.LAST_QUEUE_INDEX]?.toInt()?.coerceAtLeast(0) ?: 0,
            sleepTimerEndsAtMs = prefs[Keys.SLEEP_TIMER_ENDS_AT]?.takeIf { it > 0L },
            eqEnabled = prefs[Keys.EQ_ENABLED] ?: false,
            eqPresetIndex = prefs[Keys.EQ_PRESET_INDEX]?.toInt() ?: -1,
            eqBandLevels = prefs[Keys.EQ_BAND_LEVELS]
                ?.split(",")?.mapNotNull { it.trim().toIntOrNull() } ?: emptyList(),
            musicFolderUris = prefs[Keys.MUSIC_FOLDER_URIS]
                ?.split("\n")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList(),
            recentSearches = decodeRecentSearches(prefs[Keys.RECENT_SEARCHES]),
            onboardingSeen = prefs[Keys.ONBOARDING_SEEN] ?: false,
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    /** 0.7.0: persists the chosen full theme; applies app-wide on next recomposition. */
    suspend fun setAppTheme(theme: AppTheme) {
        context.dataStore.edit { it[Keys.APP_THEME] = theme.name }
    }

    suspend fun setAccent(accent: Accent) {
        context.dataStore.edit {
            it[Keys.ACCENT] = accent.name
            it.remove(Keys.CUSTOM_ACCENT_ARGB)
        }
    }

    /** Persists a user-picked accent color; it overrides the palette until a preset is chosen. */
    suspend fun setCustomAccent(argb: Int) {
        context.dataStore.edit { it[Keys.CUSTOM_ACCENT_ARGB] = argb.toLong() }
    }

    /** Clears a custom color and goes back to the selected palette. */
    suspend fun clearCustomAccent() {
        context.dataStore.edit { it.remove(Keys.CUSTOM_ACCENT_ARGB) }
    }

    suspend fun setLanguage(language: AppLanguage) {
        // Synchronous mirror first: MainActivity.attachBaseContext reads it
        // on the next launch (or recreate) before DataStore is available.
        context.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(SYNC_LANGUAGE_KEY, language.name)
            .commit()
        context.dataStore.edit { it[Keys.LANGUAGE] = language.name }
        LocaleHelper.updateSystemAppLocale(context, language)
    }

    /**
     * Persists the launcher-icon choice and switches the launcher entry
     * to match. Standard activity-alias pattern: exactly one component
     * carries the MAIN/LAUNCHER filter at a time — the chosen alias, or
     * MainActivity itself for DEFAULT. The alias's targetActivity is
     * MainActivity, so disabling MainActivity as a launcher entry does
     * not stop the alias from launching it. DONT_KILL_APP keeps the app
     * alive while the user is still in Settings; the launcher usually
     * picks the new icon up within seconds.
     */
    suspend fun setAppIcon(icon: AppIcon) {
        context.dataStore.edit { it[Keys.APP_ICON] = icon.name }
        applyAppIconComponent(icon)
    }

    /** PackageManager half of [setAppIcon]: enables one launcher entry, disables the rest. */
    fun applyAppIconComponent(icon: AppIcon) {
        val pm = context.packageManager
        val packageName = context.packageName
        for (entry in AppIcon.entries) {
            val alias = entry.aliasName ?: continue
            val state = if (entry == icon) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            pm.setComponentEnabledSetting(
                ComponentName(packageName, packageName + alias),
                state,
                PackageManager.DONT_KILL_APP,
            )
        }
        pm.setComponentEnabledSetting(
            ComponentName(packageName, "$packageName.MainActivity"),
            if (icon == AppIcon.DEFAULT) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            },
            PackageManager.DONT_KILL_APP,
        )
    }

    suspend fun setDefaultSpeed(speed: Float) {
        context.dataStore.edit { it[Keys.DEFAULT_SPEED] = clampPlaybackSpeed(speed) }
    }

    suspend fun setResumePlayback(enabled: Boolean) {
        context.dataStore.edit { it[Keys.RESUME_PLAYBACK] = enabled }
    }

    suspend fun setFadeEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.FADE_ENABLED] = enabled }
    }

    /**
     * Checkpoint for resume: called on track change / pause / stop and on
     * queue changes. Cheap; overwrites itself. The whole queue (ids +
     * index) is stored, so a restart restores the queue, not just one song.
     */
    suspend fun setLastPlayback(
        songId: Long?,
        positionMs: Long,
        queueIds: List<Long> = emptyList(),
        queueIndex: Int = 0,
    ) {
        context.dataStore.edit {
            if (songId == null) {
                it.remove(Keys.LAST_SONG_ID)
                it.remove(Keys.LAST_POSITION_MS)
                it.remove(Keys.LAST_QUEUE_IDS)
                it.remove(Keys.LAST_QUEUE_INDEX)
            } else {
                it[Keys.LAST_SONG_ID] = songId
                it[Keys.LAST_POSITION_MS] = positionMs.coerceAtLeast(0L)
                it[Keys.LAST_QUEUE_IDS] = encodeQueueIds(queueIds)
                it[Keys.LAST_QUEUE_INDEX] = queueIndex.coerceAtLeast(0).toLong()
            }
        }
    }

    /** Persists the sleep-timer deadline (or clears it when null). */
    suspend fun setSleepTimerEndsAt(endsAtMs: Long?) {
        context.dataStore.edit {
            if (endsAtMs == null) it.remove(Keys.SLEEP_TIMER_ENDS_AT)
            else it[Keys.SLEEP_TIMER_ENDS_AT] = endsAtMs
        }
    }

    suspend fun setEqEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.EQ_ENABLED] = enabled }
    }

    suspend fun setEqPreset(presetIndex: Int, bandLevels: List<Int>) {
        context.dataStore.edit {
            it[Keys.EQ_PRESET_INDEX] = presetIndex.toLong()
            it[Keys.EQ_BAND_LEVELS] = bandLevels.joinToString(",")
        }
    }

    suspend fun setEqBandLevels(bandLevels: List<Int>) {
        context.dataStore.edit {
            it[Keys.EQ_PRESET_INDEX] = -1L // manual bands: no preset
            it[Keys.EQ_BAND_LEVELS] = bandLevels.joinToString(",")
        }
    }

    /** Records a completed search (user opened a result); typing alone does not. */
    suspend fun recordSearchQuery(query: String) {
        context.dataStore.edit { prefs ->
            val current = decodeRecentSearches(prefs[Keys.RECENT_SEARCHES])
            prefs[Keys.RECENT_SEARCHES] = encodeRecentSearches(recordSearch(current, query))
        }
    }

    suspend fun removeSearchQuery(query: String) {
        context.dataStore.edit { prefs ->
            val current = decodeRecentSearches(prefs[Keys.RECENT_SEARCHES])
            prefs[Keys.RECENT_SEARCHES] = encodeRecentSearches(
                current.filter { it.trim().lowercase() != query.trim().lowercase() },
            )
        }
    }

    suspend fun clearSearchHistory() {
        context.dataStore.edit { it.remove(Keys.RECENT_SEARCHES) }
    }

    /** Marks the first-run onboarding as completed (shown once). */
    suspend fun setOnboardingSeen() {
        context.dataStore.edit { it[Keys.ONBOARDING_SEEN] = true }
    }

    suspend fun addMusicFolder(treeUri: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.MUSIC_FOLDER_URIS]
                ?.split("\n")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
            if (treeUri !in current) {
                prefs[Keys.MUSIC_FOLDER_URIS] = (current + treeUri).joinToString("\n")
            }
        }
    }

    suspend fun removeMusicFolder(treeUri: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.MUSIC_FOLDER_URIS]
                ?.split("\n")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
            prefs[Keys.MUSIC_FOLDER_URIS] = (current - treeUri).joinToString("\n")
        }
    }

    companion object {
        const val SYNC_PREFS_NAME = "ava_sync_prefs"
        const val SYNC_LANGUAGE_KEY = "language"

        /** Synchronous language read for `attachBaseContext` (DataStore cannot be read there). */
        fun readLanguageSync(context: Context): AppLanguage {
            val raw = context.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
                .getString(SYNC_LANGUAGE_KEY, null)
            return raw?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() }
                ?: AppLanguage.SYSTEM
        }
    }
}
