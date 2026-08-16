package com.dnc1981.musickontrol.manager

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer

class GaplessPlaybackManager(private val context: Context) {

    fun cargarGaplessEnabled(): Boolean {
        val prefs = context.getSharedPreferences("musickontrol_settings", Context.MODE_PRIVATE)
        return prefs.getBoolean("gapless_enabled", false)
    }

    fun guardarGaplessEnabled(enabled: Boolean) {
        val prefs = context.getSharedPreferences("musickontrol_settings", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("gapless_enabled", enabled).apply()
    }

    /**
     * ⭐ APLICAR GAPLESS PLAYBACK AL EXOPLAYER
     *
     * Esto hace que ExoPlayer:
     * - Salte silencios automáticamente
     * - Reproduzca sin pausas entre canciones
     * - Mantenga continuidad en transiciones
     */
    fun aplicarGaplessPlayback(exoPlayer: ExoPlayer, enabled: Boolean) {
        try {
            // ✅ SKIP SILENCE: Salta automáticamente silencios
            exoPlayer.setSkipSilenceEnabled(enabled)

            // ✅ LOG
            if (enabled) {
                android.util.Log.d("GaplessPlayback", "✅ Gapless Playback ACTIVADO")
            } else {
                android.util.Log.d("GaplessPlayback", "❌ Gapless Playback DESACTIVADO")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            android.util.Log.e("GaplessPlayback", "Error al aplicar gapless: ${e.message}")
        }
    }
}