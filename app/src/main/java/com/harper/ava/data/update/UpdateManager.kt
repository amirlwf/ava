package com.harper.ava.data.update

import android.content.Context
import com.harper.ava.core.util.isUpdateCheckDue
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
 * - Automatic check: on every entry into the app (0.2-fix, Track
 *   3 — cold start AND return from background, triggered from
 *   MainActivity's RESUMED lifecycle), when auto-check is on and
 *   the device is online. The only throttle is a 5-minute
 *   anti-hammer guard ([UPDATE_CHECK_MIN_INTERVAL_MS]) against
 *   rapid re-entries — not a cooldown on the feature. The
 *   timestamp is stamped after every real attempt (success or
 *   error), so a failing network does not retry on every entry;
 *   offline (no network at all) does not stamp anything.
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

    /**
     * Automatic check, called on every entry into the app from
     * MainActivity (cold start + return from background). No-op
     * when auto-check is off, when the 5-minute anti-hammer guard
     * has not elapsed since the last attempt, or when offline.
     * Silent when there is no update — the banner only appears when
     * a newer release exists and the user has not dismissed it.
     */
    fun checkOnAppEntry() {
        scope.launch {
            val settings = userPreferences.settings.first()
            if (!settings.autoUpdateCheck) return@launch
            val now = System.currentTimeMillis()
            if (!isUpdateCheckDue(now, settings.lastUpdateCheckMs)) return@launch
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
