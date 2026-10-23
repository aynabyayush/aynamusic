package com.ayush.aynamusic.playback

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class EqualizerBand(
    val index: Int,
    val centerFrequencyHz: Int,
    val minLevelMb: Short,
    val maxLevelMb: Short,
    val levelMb: Short
)

data class EqualizerState(
    val available: Boolean = false,
    val enabled: Boolean = false,
    val bands: List<EqualizerBand> = emptyList(),
    val presets: List<String> = emptyList(),
    val selectedPreset: Int = -1,
    val bassBoost: Int = 0,
    val error: String? = null
)

/**
 * One audio effect chain for the app's single ExoPlayer audio session.
 * Effects are opt-in, since they add a small CPU cost.
 */
object EqualizerManager {
    private const val PREFS = "ayna_equalizer"
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var appContext: Context? = null
    private var currentSessionId: Int = 0

    private val _state = MutableStateFlow(EqualizerState())
    val state: StateFlow<EqualizerState> = _state.asStateFlow()

    fun attach(context: Context, audioSessionId: Int) {
        if (audioSessionId == 0 || audioSessionId == currentSessionId) return
        release()
        appContext = context.applicationContext
        currentSessionId = audioSessionId
        runCatching {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val eq = Equalizer(0, audioSessionId)
            val range = eq.bandLevelRange
            val bands = buildList {
                for (i in 0 until eq.numberOfBands.toInt()) {
                    val level = eq.getBandLevel(i.toShort())
                    add(
                        EqualizerBand(
                            index = i,
                            centerFrequencyHz = eq.getCenterFreq(i.toShort()) / 1000,
                            minLevelMb = range[0],
                            maxLevelMb = range[1],
                            levelMb = level
                        )
                    )
                }
            }
            val presets = buildList {
                for (i in 0 until eq.numberOfPresets.toInt()) {
                    add(eq.getPresetName(i.toShort()))
                }
            }
            bands.forEach { band ->
                val saved = prefs.getInt("band_${band.index}", band.levelMb.toInt())
                eq.setBandLevel(band.index.toShort(), saved.toShort())
            }
            val enabled = prefs.getBoolean("enabled", false)
            eq.enabled = enabled
            equalizer = eq

            val boost = runCatching {
                BassBoost(0, audioSessionId).apply {
                    val strength = prefs.getInt("bass", 0).coerceIn(0, 1000)
                    setStrength(strength.toShort())
                    this.enabled = strength > 0
                }
            }.getOrNull()
            bassBoost = boost
            _state.value = EqualizerState(
                available = true,
                enabled = enabled,
                bands = bands.map { band ->
                    band.copy(levelMb = prefs.getInt("band_${band.index}", band.levelMb.toInt()).toShort())
                },
                presets = presets,
                selectedPreset = -1,
                bassBoost = prefs.getInt("bass", 0)
            )
        }.onFailure {
            release()
            _state.value = EqualizerState(
                available = false,
                error = "Audio effects aren't available for this output"
            )
        }
    }

    fun setEnabled(enabled: Boolean) {
        val eq = equalizer ?: return
        runCatching { eq.enabled = enabled }
        runCatching {
            bassBoost?.let { boost ->
                boost.enabled = enabled && _state.value.bassBoost > 0
            }
        }
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.edit()?.putBoolean("enabled", enabled)?.apply()
        _state.value = _state.value.copy(enabled = enabled)
    }

    fun setBandLevel(index: Int, levelMb: Int) {
        val eq = equalizer ?: return
        val band = _state.value.bands.firstOrNull { it.index == index } ?: return
        val clamped = levelMb.coerceIn(band.minLevelMb.toInt(), band.maxLevelMb.toInt())
        runCatching { eq.setBandLevel(index.toShort(), clamped.toShort()) }
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.edit()?.putInt("band_$index", clamped)?.apply()
        _state.value = _state.value.copy(
            bands = _state.value.bands.map {
                if (it.index == index) it.copy(levelMb = clamped.toShort()) else it
            },
            selectedPreset = -1
        )
    }

    fun applyPreset(preset: Int) {
        val eq = equalizer ?: return
        runCatching {
            eq.usePreset(preset.toShort())
            val levels = _state.value.bands.map { band ->
                band.copy(levelMb = eq.getBandLevel(band.index.toShort()))
            }
            val prefs = appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            levels.forEach { band -> prefs?.edit()?.putInt("band_${band.index}", band.levelMb.toInt())?.apply() }
            _state.value = _state.value.copy(
                bands = levels,
                selectedPreset = preset
            )
            if (!eq.enabled) setEnabled(true)
        }
    }

    fun setBassBoost(strength: Int) {
        val boost = bassBoost ?: return
        val clamped = strength.coerceIn(0, 1000)
        runCatching {
            boost.setStrength(clamped.toShort())
            boost.enabled = clamped > 0
        }
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.edit()?.putInt("bass", clamped)?.apply()
        _state.value = _state.value.copy(bassBoost = clamped, enabled = true)
        equalizer?.let { if (!it.enabled) setEnabled(true) }
    }

    fun reset() {
        _state.value.bands.forEach { band -> setBandLevel(band.index, 0) }
        setBassBoost(0)
        _state.value = _state.value.copy(selectedPreset = -1)
    }

    fun detach() {
        release()
        _state.value = EqualizerState()
    }

    private fun release() {
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        equalizer = null
        bassBoost = null
        currentSessionId = 0
    }
}
