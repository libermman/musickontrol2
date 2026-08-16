package com.dnc1981.musickontrol.audio

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import android.util.Log
import kotlin.math.pow

class AudioNormalizerProcessor(private val context: Context) {

    private var normalizationEnabled = cargarNormalizacionEnabled()
    private var normalizationLevel = cargarNivelNormalizacion()
    private var autoDetectEnabled = cargarAutoDetectEnabled()

    private var detectedLevel = -12f

    // ✅ LISTENER PARA CAMBIOS
    private var listener: NormalizationListener? = null

    // ✅ CARGAR PREFERENCIAS
    private fun cargarNormalizacionEnabled(): Boolean {
        val prefs = context.getSharedPreferences("musickontrol_settings", Context.MODE_PRIVATE)
        val value = prefs.getBoolean("normalization_enabled", false)
        Log.d("AudioNormalizer", "📂 Cargando normalization_enabled: $value")
        return value
    }

    private fun cargarNivelNormalizacion(): Float {
        val prefs = context.getSharedPreferences("musickontrol_settings", Context.MODE_PRIVATE)
        val value = prefs.getFloat("normalization_level", -12f)
        Log.d("AudioNormalizer", "📂 Cargando normalization_level: ${String.format("%.1f", value)}dB")
        return value
    }

    private fun cargarAutoDetectEnabled(): Boolean {
        val prefs = context.getSharedPreferences("musickontrol_settings", Context.MODE_PRIVATE)
        val value = prefs.getBoolean("normalization_auto_detect", true)
        Log.d("AudioNormalizer", "📂 Cargando normalization_auto_detect: $value")
        return value
    }

    // ✅ RECARGAR TODAS LAS PREFERENCIAS (NUEVO)
    private fun recargarPreferencias() {
        normalizationEnabled = cargarNormalizacionEnabled()
        normalizationLevel = cargarNivelNormalizacion()
        autoDetectEnabled = cargarAutoDetectEnabled()
        Log.d("AudioNormalizer", "🔄 Preferencias recargadas - Habilitada: $normalizationEnabled, Nivel: ${String.format("%.1f", normalizationLevel)}dB, AutoDetect: $autoDetectEnabled")
    }

    // ✅ GUARDAR PREFERENCIAS
    fun guardarNormalizacionEnabled(enabled: Boolean) {
        val prefs = context.getSharedPreferences("musickontrol_settings", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("normalization_enabled", enabled).apply()
        normalizationEnabled = enabled
        Log.d("AudioNormalizer", "💾 Guardado normalization_enabled: $enabled")
        notificarCambio()
    }

    fun guardarNivelNormalizacion(level: Float) {
        val prefs = context.getSharedPreferences("musickontrol_settings", Context.MODE_PRIVATE)
        prefs.edit().putFloat("normalization_level", level).apply()
        normalizationLevel = level
        Log.d("AudioNormalizer", "💾 Guardado normalization_level: ${String.format("%.1f", level)}dB")
        notificarCambio()
    }

    fun guardarAutoDetectEnabled(enabled: Boolean) {
        val prefs = context.getSharedPreferences("musickontrol_settings", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("normalization_auto_detect", enabled).apply()
        autoDetectEnabled = enabled
        Log.d("AudioNormalizer", "💾 Guardado normalization_auto_detect: $enabled")
        notificarCambio()
    }

    // ✅ GETTERS
    fun isNormalizationEnabled(): Boolean = normalizationEnabled

    fun getNormalizationLevel(): Float = normalizationLevel

    fun getDetectedLevel(): Float = detectedLevel

    fun isAutoDetectEnabled(): Boolean = autoDetectEnabled

    // ✅ REGISTRAR LISTENER
    fun setListener(listener: NormalizationListener?) {
        this.listener = listener
        Log.d("AudioNormalizer", "📡 Listener registrado")
    }

    // ✅ NOTIFICAR CAMBIOS
    private fun notificarCambio() {
        listener?.onNormalizationChanged(normalizationEnabled, normalizationLevel, autoDetectEnabled)
        Log.d("AudioNormalizer", "📡 Cambio notificado al listener - Nivel: ${String.format("%.1f", normalizationLevel)}dB")
    }

    /**
     * ⭐ APLICAR NORMALIZACIÓN AL EXOPLAYER
     * Usa el volumen nativo de ExoPlayer para simular normalización
     */
    fun aplicarNormalizacion(exoPlayer: ExoPlayer) {
        // ✅ RECARGAR PREFERENCIAS ANTES DE APLICAR
        recargarPreferencias()

        Log.d("AudioNormalizer", "🔍 Aplicando normalización - Habilitada: $normalizationEnabled, Nivel: ${String.format("%.1f", normalizationLevel)}dB")

        if (!normalizationEnabled) {
            exoPlayer.volume = 1.0f
            Log.d("AudioNormalizer", "🔊 Normalización DESACTIVADA - Volumen: 1.0")
            return
        }

        try {
            // Convertir dB a ganancia lineal
            val targetLevel = if (autoDetectEnabled) {
                detectedLevel + normalizationLevel
            } else {
                normalizationLevel
            }

            // Convertir dB a factor de volumen (0.0 a 2.0)
            // -20dB = 0.1, -12dB = 0.251, -6dB = 0.501, 0dB = 1.0
            val volumeFactor = 10f.pow(targetLevel / 20f)
            val clampedVolume = volumeFactor.coerceIn(0.0f, 2.0f)

            exoPlayer.volume = clampedVolume

            Log.d(
                "AudioNormalizer",
                "✅ Normalización aplicada - Nivel: ${String.format("%.2f", targetLevel)} dB, Volumen: ${String.format("%.2f", clampedVolume)}"
            )

        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("AudioNormalizer", "❌ Error aplicando normalización: ${e.message}")
        }
    }

    /**
     * ⭐ ACTUALIZAR NORMALIZACIÓN EN TIEMPO REAL
     */
    fun actualizarNormalizacion(enabled: Boolean, level: Float, autoDetect: Boolean, exoPlayer: ExoPlayer? = null) {
        Log.d("AudioNormalizer", "🔄 Actualizando normalización - Habilitada: $enabled, Nivel: ${String.format("%.1f", level)}dB, AutoDetect: $autoDetect")

        normalizationEnabled = enabled
        normalizationLevel = level
        autoDetectEnabled = autoDetect

        guardarNormalizacionEnabled(enabled)
        guardarNivelNormalizacion(level)
        guardarAutoDetectEnabled(autoDetect)

        // Aplicar inmediatamente si hay player
        exoPlayer?.let {
            aplicarNormalizacion(it)
            Log.d("AudioNormalizer", "✅ Normalización actualizada y aplicada al player")
        }

        Log.d(
            "AudioNormalizer",
            "✅ Normalización actualizada - Habilitada: $enabled, Nivel: ${String.format("%.1f", level)} dB, Auto-detect: $autoDetect"
        )
    }
}
