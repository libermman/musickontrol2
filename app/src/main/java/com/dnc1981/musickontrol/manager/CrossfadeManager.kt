package com.dnc1981.musickontrol.manager

import android.content.Context
import android.util.Log
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.*

class CrossfadeManager(private val context: Context) {

    companion object {
        private const val TAG = "CrossfadeManager"
        private const val DEFAULT_CROSSFADE_DURATION = 2000L // 2 segundos
    }

    private var crossfadeDuration = DEFAULT_CROSSFADE_DURATION
    private var crossfadeJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    fun cargarCrossfadeDuration(): Long {
        val prefs = context.getSharedPreferences("musickontrol_settings", Context.MODE_PRIVATE)
        // ✅ CORREGIDO: Leer como LONG directamente
        val durationMs = prefs.getLong("crossfade_duration", DEFAULT_CROSSFADE_DURATION)
        Log.d(TAG, "📊 Crossfade Duration cargado: ${durationMs}ms (${durationMs / 1000f}s)")
        crossfadeDuration = durationMs
        return durationMs
    }

    fun guardarCrossfadeDuration(duration: Long) {
        val prefs = context.getSharedPreferences("musickontrol_settings", Context.MODE_PRIVATE)
        prefs.edit().putLong("crossfade_duration", duration).apply()
        crossfadeDuration = duration
        Log.d(TAG, "💾 Crossfade Duration guardado: ${duration}ms (${duration / 1000f}s)")
        Log.d(TAG, "🔑 Variable interna actualizada: crossfadeDuration = $crossfadeDuration")
    }

    /**
     * ⭐ APLICAR CROSSFADE ENTRE CANCIONES
     *
     * Esto hace que:
     * - Canción actual: Fade OUT (volumen 1.0 → 0.0)
     * - Siguiente: Fade IN (volumen 0.0 → 1.0)
     * - Duración configurable (0-5 segundos)
     * - Se ejecuta cuando cambia la canción
     */
    fun aplicarCrossfade(
        exoPlayer: ExoPlayer,
        durationMs: Long? = null,  // ✅ CAMBIO: Ahora es nullable
        onComplete: () -> Unit = {}
    ) {
        // ✅ CRÍTICO: Usar el valor pasado O el valor actual guardado
        val finalDuration = durationMs ?: crossfadeDuration

        Log.d(TAG, "🔍 DEBUG aplicarCrossfade:")
        Log.d(TAG, "   - durationMs pasado: $durationMs")
        Log.d(TAG, "   - crossfadeDuration variable: $crossfadeDuration")
        Log.d(TAG, "   - finalDuration a usar: $finalDuration")

        // Cancelar crossfade anterior si existe
        crossfadeJob?.cancel()

        if (finalDuration <= 0) {
            Log.d(TAG, "⏭️ Crossfade desactivado (duración = 0)")
            exoPlayer.volume = 1.0f
            onComplete()
            return
        }

        Log.d(TAG, "🎵 Iniciando crossfade: ${finalDuration}ms (${finalDuration / 1000f}s)")

        crossfadeJob = scope.launch {
            try {
                val steps = 50 // Pasos de fade
                val stepDuration = finalDuration / steps

                Log.d(TAG, "📊 Steps: $steps, Step Duration: ${stepDuration}ms, Total: ${finalDuration}ms")

                // Fade OUT → Fade IN
                for (i in 0..steps) {
                    if (!isActive) {
                        Log.d(TAG, "⚠️ Crossfade cancelado en step $i")
                        break
                    }

                    val progress = i.toFloat() / steps

                    // Fade OUT: 1.0 → 0.0
                    val fadeOutVolume = 1.0f - progress

                    // Fade IN: 0.0 → 1.0
                    val fadeInVolume = progress

                    // Aplicar volumen suavemente
                    exoPlayer.volume = fadeOutVolume

                    if (i < steps) {
                        delay(stepDuration)
                    }
                }

                // Restaurar volumen a 1.0
                exoPlayer.volume = 1.0f

                Log.d(TAG, "✅ Crossfade completado (${finalDuration}ms)")
                onComplete()
            } catch (e: CancellationException) {
                Log.d(TAG, "⚠️ Crossfade cancelado por usuario")
                exoPlayer.volume = 1.0f
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error en crossfade: ${e.message}", e)
                exoPlayer.volume = 1.0f
            }
        }
    }

    fun cancelarCrossfade() {
        crossfadeJob?.cancel()
        Log.d(TAG, "⏹️ Crossfade cancelado")
    }

    fun destroy() {
        crossfadeJob?.cancel()
        scope.cancel()
        Log.d(TAG, "🧹 CrossfadeManager destruido")
    }

    // ✅ NUEVA FUNCIÓN: Obtener duración actual
    fun obtenerCrossfadeDuration(): Long {
        return crossfadeDuration
    }
}