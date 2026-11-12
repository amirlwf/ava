package com.harper.ava.data.update

import android.content.Context
import com.harper.ava.core.util.UPDATE_CHECK_COOLDOWN_MS
import com.harper.ava.data.preferences.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** What the in-app update banner shows (null = no banner). */
data class UpdateBannerState(val tag: String, val versionName: String, val htmlUrl: String)

/** Result of a manual "check now" from Settings. */
sealed interface ManualUpdateCheckState {
    data object Idle : ManualUpdateCheckState
    data object Checking : ManualUpdateCheckState
    data class Available(val versionName: String) : ManualUpdateCheckState
    data class UpToDate(val currentVersion: String) : ManualUpdateCheckState
    data object Error : ManualUpdateCheckState
}

/**
 * Owns the auto-update-check state (0.7.2).
 *
 * One shared instance lives in [com.harper.ava.AppContainer], so the
 * banner in the nav graph and the Settings screen read the same state.
 * Runs on its own scope (SupervisorJob + IO): the check never blocks
 * startup or the UI, and any failure just stays silent.
 *
 * Rules:
 * - Automatic check: only when auto-check is on, more than 24 h have
 *   passed since the last attempt, and the device is online. The
 *   timestamp is stamped after every real attempt (success or error),
 *   so a failing network does not retry on every launch; offline
 *   (no network at all) does not stamp anything.
 * - A version the user dismissed ("don't tell me again for this
 *   version") is never shown by the automatic check again — until the
 *   next release. A manual check still reports it.
 * - "Later" only hides the banner for this session.
 */
class UpdateManager(
    context: Context,
    private val userPreferences: UserPreferences,
    private val updateChecker: UpdateChecker = UpdateChecker(context.applicationContext),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _bannerState = MutableStateFlow<UpdateBannerState?>(null)
    val bannerState: StateFlow<UpdateBannerState?> = _bannerState.asStateFlow()

    private val _manualCheckState =
        MutableStateFlow<ManualUpdateCheckState>(ManualUpdateCheckState.Idle)
    val manualCheckState: StateFlow<ManualUpdateCheckState> = _manualCheckState.asStateFlow()

    /** Automatic check, called once per app start from the banner. No-op when not due. */
    fun checkIfDue() {
        scope.launch {
            val settings = userPreferences.settings.first()
            if (!settings.autoUpdateCheck) return@launch
            val now = System.currentTimeMillis()
            if (now - settings.lastUpdateCheckMs < UPDATE_CHECK_COOLDOWN_MS) return@launch
            if (!updateChecker.isOnline()) return@launch
            val result = runCheckAndStamp(now) ?: return@launch
            if (result is UpdateCheckResult.Available &&
                result.tag != settings.dismissedUpdateVersion
            ) {
                _bannerState.value = UpdateBannerState(result.tag, result.versionName, result.htmlUrl)
            }
        }
    }

    /** Manual "check now" from Settings: ignores the cooldown, reports the outcome. */
    fun checkNow() {
        scope.launch {
            _manualCheckState.value = ManualUpdateCheckState.Checking
            if (!updateChecker.isOnline()) {
                _manualCheckState.value = ManualUpdateCheckState.Error
                return@launch
            }
            val result = runCheckAndStamp(System.currentTimeMillis())
            _manualCheckState.value = when (result) {
                is UpdateCheckResult.Available -> {
                    _bannerState.value =
                        UpdateBannerState(result.tag, result.versionName, result.htmlUrl)
                    ManualUpdateCheckState.Available(result.versionName)
                }
                is UpdateCheckResult.UpToDate ->
                    ManualUpdateCheckState.UpToDate(result.currentVersion)

                else -> ManualUpdateCheckState.Error
            }
        }
    }

    /** "Later": hide the banner for this session only; nothing is persisted. */
    fun dismissForNow() {
        _bannerState.value = null
    }

    /** "Don't tell me again for this version": persist the tag, hide the banner. */
    fun dismissVersion(tag: String) {
        _bannerState.value = null
        scope.launch { userPreferences.setDismissedUpdateVersion(tag) }
    }

    /** Runs the network check and stamps the attempt time; null when offline mid-flight. */
    private suspend fun runCheckAndStamp(nowMs: Long): UpdateCheckResult? {
        val result = updateChecker.check()
        userPreferences.setLastUpdateCheckMs(nowMs)
        return result
    }
}
