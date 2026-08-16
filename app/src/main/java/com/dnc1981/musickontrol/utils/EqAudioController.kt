package com.dnc1981.musickontrol.utils

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.util.Log
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer

class EqAudioController(
    private val context: Context
) {

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null

    private var currentAudioSessionId: Int = C.AUDIO_SESSION_ID_UNSET

    var currentState: EqState = EqPreferences.loadEqState(context)
        private set

    fun attachToPlayer(exoPlayer: ExoPlayer?) {
        try {
            val sessionId = exoPlayer?.audioSessionId ?: C.AUDIO_SESSION_ID_UNSET
            attachToAudioSession(sessionId)
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("EqAudioController", "❌ Error en attachToPlayer: ${e.message}")
        }
    }

    fun attachToAudioSession(audioSessionId: Int) {
        try {
            if (audioSessionId == C.AUDIO_SESSION_ID_UNSET || audioSessionId <= 0) {
                Log.w("EqAudioController", "⚠️ Audio session ID inválido: $audioSessionId")
                return
            }

            if (
                audioSessionId == currentAudioSessionId &&
                equalizer != null &&
                bassBoost != null
            ) {
                applyState(currentState, save = false)
                return
            }

            releaseEffectsOnly()

            currentAudioSessionId = audioSessionId

            try {
                equalizer = Equalizer(0, audioSessionId).apply {
                    enabled = currentState.isEnabled
                }
                Log.d("EqAudioController", "✅ Equalizer inicializado")
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e("EqAudioController", "❌ Error inicializando Equalizer: ${e.message}")
                equalizer = null
            }

            try {
                bassBoost = BassBoost(0, audioSessionId).apply {
                    enabled = currentState.isEnabled && currentState.bassBoostStrength > 0f
                }
                Log.d("EqAudioController", "✅ BassBoost inicializado")
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e("EqAudioController", "❌ Error inicializando BassBoost: ${e.message}")
                bassBoost = null
            }

            try {
                loudnessEnhancer = LoudnessEnhancer(audioSessionId).apply {
                    enabled = currentState.isEnabled &&
                            (currentState.isBoost30Active || currentState.isBoost50Active)
                }
                Log.d("EqAudioController", "✅ LoudnessEnhancer inicializado")
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e("EqAudioController", "❌ Error inicializando LoudnessEnhancer: ${e.message}")
                loudnessEnhancer = null
            }

            applyState(currentState, save = false)
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("EqAudioController", "❌ Error en attachToAudioSession: ${e.message}")
        }
    }

    fun applyState(
        state: EqState,
        save: Boolean = true
    ) {
        try {
            currentState = normalizeState(state)

            if (save) {
                EqPreferences.saveEqState(context, currentState)
            }

            applyEqualizer()
            applyBassBoost()
            applyLoudnessEnhancer()
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("EqAudioController", "❌ Error en applyState: ${e.message}")
        }
    }

    fun reloadFromPreferences() {
        try {
            currentState = EqPreferences.loadEqState(context)
            applyState(currentState, save = false)
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("EqAudioController", "❌ Error en reloadFromPreferences: ${e.message}")
        }
    }

    private fun applyEqualizer() {
        val eq = equalizer ?: return

        try {
            eq.enabled = currentState.isEnabled

            if (!currentState.isEnabled) {
                return
            }

            val bandCount = eq.numberOfBands.toInt()
            val usableBands = minOf(5, bandCount, currentState.bands.size)

            val range = eq.bandLevelRange
            val minLevel = range[0].toInt()
            val maxLevel = range[1].toInt()

            for (i in 0 until usableBands) {
                val db = currentState.bands[i].coerceIn(-12f, 12f)

                val boostedDb = when {
                    currentState.isBoost50Active -> db * 1.35f
                    currentState.isBoost30Active -> db * 1.20f
                    else -> db
                }.coerceIn(-12f, 12f)

                val millibels = (boostedDb * 100f)
                    .toInt()
                    .coerceIn(minLevel, maxLevel)

                eq.setBandLevel(i.toShort(), millibels.toShort())
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("EqAudioController", "❌ Error en applyEqualizer: ${e.message}")
        }
    }

    private fun applyBassBoost() {
        val bb = bassBoost ?: return

        try {
            val enabled = currentState.isEnabled && currentState.bassBoostStrength > 0f
            bb.enabled = enabled

            if (!enabled) {
                return
            }

            if (bb.strengthSupported) {
                val baseStrength = currentState.bassBoostStrength.coerceIn(0f, 1000f)

                val multiplier = when {
                    currentState.isBoost50Active -> 1.35f
                    currentState.isBoost30Active -> 1.20f
                    else -> 1.0f
                }

                val boostedStrength = (baseStrength * multiplier)
                    .toInt()
                    .coerceIn(0, 1000)

                bb.setStrength(boostedStrength.toShort())
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("EqAudioController", "❌ Error en applyBassBoost: ${e.message}")
        }
    }

    private fun applyLoudnessEnhancer() {
        val enhancer = loudnessEnhancer ?: return

        try {
            val enabled = currentState.isEnabled &&
                    (currentState.isBoost30Active || currentState.isBoost50Active)

            enhancer.enabled = enabled

            if (!enabled) {
                enhancer.setTargetGain(0)
                return
            }

            val targetGainMb = when {
                currentState.isBoost50Active -> 450
                currentState.isBoost30Active -> 275
                else -> 0
            }

            enhancer.setTargetGain(targetGainMb)
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("EqAudioController", "❌ Error en applyLoudnessEnhancer: ${e.message}")
        }
    }

    private fun normalizeState(state: EqState): EqState {
        val safeBands = if (state.bands.size >= 5) {
            state.bands.take(5).map { it.coerceIn(-12f, 12f) }
        } else {
            val mutableBands = state.bands.toMutableList()

            while (mutableBands.size < 5) {
                mutableBands.add(0f)
            }

            mutableBands.take(5).map { it.coerceIn(-12f, 12f) }
        }

        val safeBassBoost = state.bassBoostStrength.coerceIn(0f, 1000f)

        return state.copy(
            bands = safeBands,
            bassBoostStrength = safeBassBoost
        )
    }

    private fun releaseEffectsOnly() {
        try {
            loudnessEnhancer?.enabled = false
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            equalizer?.enabled = false
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            bassBoost?.enabled = false
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            loudnessEnhancer?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            equalizer?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            bassBoost?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        loudnessEnhancer = null
        equalizer = null
        bassBoost = null
    }

    fun release() {
        try {
            releaseEffectsOnly()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        currentAudioSessionId = C.AUDIO_SESSION_ID_UNSET
    }
}
