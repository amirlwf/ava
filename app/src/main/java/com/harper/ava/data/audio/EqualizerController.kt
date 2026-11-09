package com.harper.ava.data.audio

import android.media.audiofx.Equalizer

/**
 * Thin wrapper around Android's [Equalizer] audio effect, attached to
 * ExoPlayer's audio session.
 *
 * Honesty rule (master spec §12): if the device/OS does not support the
 * effect, this reports `supported = false` with a reason — it never fakes
 * bands or presets. Every call is guarded: the effect constructor throws
 * (RuntimeException / UnsupportedOperationException / IllegalArgumentException)
 * on devices without an equalizer, and preset/band reads can throw too.
 */
data class EqualizerSupport(
    val supported: Boolean,
    /** String-resource key for the failure reason, or null when supported. */
    val reasonKey: String? = null,
    val bandCount: Int = 0,
    /** Center frequency of each band, in Hz. */
    val centerFreqsHz: List<Int> = emptyList(),
    /** Level range in dB (from the effect's millibel range). */
    val minLevelDb: Float = 0f,
    val maxLevelDb: Float = 0f,
    val presets: List<String> = emptyList(),
)

class EqualizerController {

    private var equalizer: Equalizer? = null
    private var attachedSessionId: Int = -1

    /**
     * Attaches to [audioSessionId] (0 = no active session yet) and applies
     * the requested state. Returns what the device really supports.
     * Safe to call repeatedly: re-attaches only when the session changed.
     */
    fun ensure(
        audioSessionId: Int,
        enabled: Boolean,
        presetIndex: Int,
        bandLevelsMillibels: List<Int>,
    ): EqualizerSupport {
        if (audioSessionId <= 0) {
            return EqualizerSupport(supported = false, reasonKey = "eq_reason_no_session")
        }
        val effect = try {
            if (equalizer == null || attachedSessionId != audioSessionId) {
                equalizer?.release()
                Equalizer(0, audioSessionId).also {
                    equalizer = it
                    attachedSessionId = audioSessionId
                }
            } else {
                equalizer!!
            }
        } catch (e: Exception) {
            equalizer = null
            attachedSessionId = -1
            return EqualizerSupport(supported = false, reasonKey = "eq_reason_unsupported")
        }
        return try {
            val bandCount = effect.numberOfBands.toInt()
            val range = effect.bandLevelRange
            val minDb = range[0] / 100f
            val maxDb = range[1] / 100f
            val freqs = (0 until bandCount).map { effect.getCenterFreq(it.toShort()) / 1000 }
            val presets = (0 until effect.numberOfPresets).mapNotNull { index ->
                runCatching { effect.getPresetName(index.toShort()) }.getOrNull()
            }
            effect.enabled = enabled
            if (enabled) {
                if (presetIndex in presets.indices) {
                    effect.usePreset(presetIndex.toShort())
                } else {
                    bandLevelsMillibels.forEachIndexed { band, level ->
                        if (band in 0 until bandCount) {
                            effect.setBandLevel(band.toShort(), level.toShort())
                        }
                    }
                }
            }
            EqualizerSupport(
                supported = true,
                bandCount = bandCount,
                centerFreqsHz = freqs,
                minLevelDb = minDb,
                maxLevelDb = maxDb,
                presets = presets,
            )
        } catch (e: Exception) {
            EqualizerSupport(supported = false, reasonKey = "eq_reason_unsupported")
        }
    }

    /** Current band levels in millibels (empty when no effect is attached). */
    fun currentBandLevelsMillibels(): List<Int> {
        val effect = equalizer ?: return emptyList()
        return try {
            (0 until effect.numberOfBands).map { effect.getBandLevel(it.toShort()).toInt() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun setEnabled(enabled: Boolean) {
        runCatching { equalizer?.enabled = enabled }
    }

    fun release() {
        runCatching { equalizer?.release() }
        equalizer = null
        attachedSessionId = -1
    }
}
