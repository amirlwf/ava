package com.harper.ava.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import coil3.SingletonImageLoader
import com.harper.ava.data.media.SafFolderInfo
import com.harper.ava.data.media.SafFolderScanner
import com.harper.ava.data.media.ScanResult
import com.harper.ava.data.preferences.Accent
import com.harper.ava.data.preferences.AppLanguage
import com.harper.ava.data.preferences.SettingsState
import com.harper.ava.data.preferences.ThemeMode
import com.harper.ava.data.preferences.UserPreferences
import com.harper.ava.data.repository.MusicRepository
import com.harper.ava.data.repository.PlayerRepository
import com.harper.ava.ui.scan.ScanUiState
import com.harper.ava.ui.scan.toUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** All settings screens read and write through this one ViewModel. */
class SettingsViewModel(
    private val context: Context,
    private val userPreferences: UserPreferences,
    private val musicRepository: MusicRepository,
    private val playerRepository: PlayerRepository,
    /** Only the Storage screen needs the SAF folder scanner; null elsewhere. */
    private val safFolderScanner: SafFolderScanner? = null,
) : ViewModel() {

    val settings: StateFlow<SettingsState> = userPreferences.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsState())

    // -- appearance / language / playback (all DataStore-backed) -----------

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { userPreferences.setThemeMode(mode) }
    }

    fun setAccent(accent: Accent) {
        viewModelScope.launch { userPreferences.setAccent(accent) }
    }

    fun setCustomAccent(argb: Int) {
        viewModelScope.launch { userPreferences.setCustomAccent(argb) }
    }

    fun clearCustomAccent() {
        viewModelScope.launch { userPreferences.clearCustomAccent() }
    }

    /**
     * Changes the language and runs [onApplied] only after the choice is
     * persisted (including the synchronous mirror). The caller recreates
     * the Activity in [onApplied] so resources reload in the new language.
     */
    fun setLanguage(language: AppLanguage, onApplied: () -> Unit = {}) {
        viewModelScope.launch {
            userPreferences.setLanguage(language)
            onApplied()
        }
    }

    fun setDefaultSpeed(speed: Float) {
        viewModelScope.launch {
            userPreferences.setDefaultSpeed(speed)
            playerRepository.setPlaybackSpeed(speed)
        }
    }

    fun setResumePlayback(enabled: Boolean) {
        viewModelScope.launch { userPreferences.setResumePlayback(enabled) }
    }

    fun setFadeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.setFadeEnabled(enabled)
            playerRepository.setFadeEnabled(enabled)
        }
    }

    // -- storage & library -------------------------------------------------

    private val _storageStats = MutableStateFlow(MusicRepository.LibraryStats())
    val storageStats: StateFlow<MusicRepository.LibraryStats> = _storageStats.asStateFlow()

    private val _cacheSizeBytes = MutableStateFlow(0L)
    val cacheSizeBytes: StateFlow<Long> = _cacheSizeBytes.asStateFlow()

    /** Bytes freed by the last cache clear, shown once as feedback. Null = no clear yet. */
    private val _cacheFreedBytes = MutableStateFlow<Long?>(null)
    val cacheFreedBytes: StateFlow<Long?> = _cacheFreedBytes.asStateFlow()

    private val _scanState = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val scanState: StateFlow<ScanUiState> = _scanState.asStateFlow()

    // -- SAF music folders (stage 5) ----------------------------------------

    private val _musicFolders = MutableStateFlow<List<SafFolderInfo>>(emptyList())
    val musicFolders: StateFlow<List<SafFolderInfo>> = _musicFolders.asStateFlow()

    init {
        refreshStorage()
        refreshMusicFolders()
    }

    /** Re-inspects every picked folder (counts audio files, detects lost access). */
    fun refreshMusicFolders() {
        val scanner = safFolderScanner ?: return
        viewModelScope.launch {
            val uris = userPreferences.settings.first().musicFolderUris
            _musicFolders.value = uris.map { scanner.inspectFolder(it) }
        }
    }

    /** Persists a freshly picked folder (takes a persistable read permission first). */
    fun pickMusicFolder(treeUri: Uri) {
        viewModelScope.launch {
            SafFolderScanner.takePersistablePermission(context, treeUri)
            userPreferences.addMusicFolder(treeUri.toString())
            refreshMusicFolders()
        }
    }

    fun removeMusicFolder(treeUri: String) {
        viewModelScope.launch {
            userPreferences.removeMusicFolder(treeUri)
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    Uri.parse(treeUri),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            refreshMusicFolders()
        }
    }

    fun onScanStarted() {
        _scanState.value = ScanUiState.Scanning
    }

    fun onScanResult(result: ScanResult) {
        _scanState.value = result.toUiState()
        refreshStorage()
    }

    fun refreshStorage() {
        viewModelScope.launch {
            _storageStats.value = musicRepository.libraryStats()
            _cacheSizeBytes.value = computeCacheSize()
        }
    }

    /** Clears Coil's cover-art disk cache plus the app cache dir, then reports what was freed. */
    fun clearCoverCache() {
        viewModelScope.launch {
            val before = computeCacheSize()
            withContext(Dispatchers.IO) {
                runCatching { SingletonImageLoader.get(context).diskCache?.clear() }
                context.cacheDir?.walkBottomUp()?.forEach { file ->
                    if (file.isFile) runCatching { file.delete() }
                }
            }
            val after = computeCacheSize()
            _cacheFreedBytes.value = (before - after).coerceAtLeast(0L)
            _cacheSizeBytes.value = after
        }
    }

    private suspend fun computeCacheSize(): Long = withContext(Dispatchers.IO) {
        var total = 0L
        context.cacheDir?.walkBottomUp()?.forEach { file ->
            if (file.isFile) total += runCatching { file.length() }.getOrDefault(0L)
        }
        total
    }

    companion object {
        fun factory(
            context: Context,
            prefs: UserPreferences,
            music: MusicRepository,
            player: PlayerRepository,
            safFolders: SafFolderScanner? = null,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SettingsViewModel(
                        context.applicationContext,
                        prefs,
                        music,
                        player,
                        safFolders,
                    ) as T
            }
    }
}
