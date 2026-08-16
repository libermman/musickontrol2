package com.dnc1981.musickontrol.audio

import android.content.Context
import android.media.AudioManager
import android.util.Log
import kotlinx.coroutines.*

/**
 * Gestor de volumen adaptativo basado en velocidad del vehículo
 * Tabla optimizada con curva suave y suavizado de cambios
 */
class AdaptiveVolumeManager(
    private val context: Context,
    private val audioManager: AudioManager
) {

    private val TAG = "AdaptiveVolume"
    private var scope = CoroutineScope(Dispatchers.Main + Job())

    // 📊 TABLA OPTIMIZADA - Curva suave y profesional
    private val volumeThresholds = listOf(
        Pair(0f, 15f) to 30,        // 0-15 km/h → 30% (Detenido/Callejendos)
        Pair(15f, 30f) to 40,       // 15-30 km/h → 40%
        Pair(30f, 45f) to 50,       // 30-45 km/h → 50%
        Pair(45f, 60f) to 60,       // 45-60 km/h → 60%
        Pair(60f, 80f) to 70,       // 60-80 km/h → 70%
        Pair(80f, 100f) to 85,      // 80-100 km/h → 85%
        Pair(100f, Float.MAX_VALUE) to 100  // 100+ km/h → 100%
    )

    private var isEnabled = false
    private var currentSensitivity = VolumeSensitivity.NORMAL

    // 🔄 SUAVIZADO (Smoothing)
    private val volumeHistory = mutableListOf<Int>()
    private val maxHistorySize = 3  // Media móvil de 3 valores
    private var lastVolumeApplied = 30
    private var lastVolumeUpdateTime = 0L
    private val minUpdateIntervalMs = 500L  // Mínimo intervalo entre actualizaciones

    init {
        // Cargar estado desde SharedPreferences
        val prefs = context.getSharedPreferences("musickontrol_settings", Context.MODE_PRIVATE)
        isEnabled = prefs.getBoolean("adaptive_volume_enabled", false)

        val sensitivityIndex = prefs.getInt("adaptive_volume_sensitivity", 1)
        currentSensitivity = VolumeSensitivity.values()[sensitivityIndex]

        Log.d(TAG, "✅ AdaptiveVolumeManager inicializado")
        Log.d(TAG, "   Estado: ${if (isEnabled) "ACTIVADO" else "DESACTIVADO"}")
        Log.d(TAG, "   Sensibilidad: $currentSensitivity")
    }

    /**
     * Calcula el volumen recomendado basado en velocidad
     */
    private fun calculateVolumeForSpeed(speedKmh: Float): Int {
        val threshold = volumeThresholds.find { (range, _) ->
            speedKmh >= range.first && speedKmh < range.second
        }
        return threshold?.second ?: 100
    }

    /**
     * Aplica suavizado (media móvil) según la sensibilidad
     */
    private fun applySmoothing(targetVolume: Int): Int {
        volumeHistory.add(targetVolume)

        // Mantener el tamaño máximo del historial
        if (volumeHistory.size > maxHistorySize) {
            volumeHistory.removeAt(0)
        }

        // Calcular media móvil
        val smoothedVolume = when (currentSensitivity) {
            VolumeSensitivity.SUAVE -> {
                // Media de todos los valores (más suave)
                volumeHistory.average().toInt()
            }
            VolumeSensitivity.NORMAL -> {
                // Media ponderada (más reciente = más peso)
                if (volumeHistory.size == 1) {
                    volumeHistory[0]
                } else {
                    val weights = (1..volumeHistory.size).toList()
                    val weighted = volumeHistory.zip(weights).sumOf { (vol, weight) -> vol * weight }
                    weighted / weights.sum()
                }
            }
            VolumeSensitivity.AGRESIVO -> {
                // Sin suavizado, valor directo
                targetVolume
            }
        }

        return smoothedVolume.coerceIn(30, 100)
    }

    /**
     * Aplica volumen adaptativo (respeta el estado isEnabled)
     */
    fun applyAdaptiveVolume(speedKmh: Float) {
        if (!isEnabled) return

        val currentTime = System.currentTimeMillis()

        // Respetar intervalo mínimo entre actualizaciones
        if (currentTime - lastVolumeUpdateTime < minUpdateIntervalMs) {
            return
        }

        val targetVolume = calculateVolumeForSpeed(speedKmh)
        val smoothedVolume = applySmoothing(targetVolume)

        // Solo aplicar si hay cambio significativo
        if (smoothedVolume != lastVolumeApplied) {
            setVolumePercent(smoothedVolume)
            lastVolumeApplied = smoothedVolume
            lastVolumeUpdateTime = currentTime

            Log.d(TAG, "🚗 Velocidad: ${String.format("%.1f", speedKmh)} km/h → Volumen: $smoothedVolume% (Sensibilidad: $currentSensitivity)")
        }
    }

    /**
     * Aplica volumen FORZADO (para debug, ignora isEnabled)
     */
    fun applyAdaptiveVolumeDebug(speedKmh: Float) {
        val targetVolume = calculateVolumeForSpeed(speedKmh)
        val smoothedVolume = applySmoothing(targetVolume)
        setVolumePercent(smoothedVolume)
        Log.d(TAG, "🧪 DEBUG: Velocidad: ${String.format("%.1f", speedKmh)} km/h → Volumen FORZADO: $smoothedVolume%")
    }

    /**
     * Establece volumen como porcentaje (0-100)
     */
    private fun setVolumePercent(percent: Int) {
        val clampedPercent = percent.coerceIn(30, 100)  // Mínimo 30%, máximo 100%
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val volumeLevel = (maxVolume * clampedPercent) / 100

        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volumeLevel, 0)
        Log.d(TAG, "🔊 Volumen: $clampedPercent% (Nivel: $volumeLevel/$maxVolume)")
    }

    /**
     * Cambia la sensibilidad
     */
    fun setSensitivity(sensitivity: VolumeSensitivity) {
        currentSensitivity = sensitivity
        volumeHistory.clear()  // Limpiar historial al cambiar sensibilidad

        // Guardar en SharedPreferences
        val prefs = context.getSharedPreferences("musickontrol_settings", Context.MODE_PRIVATE)
        prefs.edit().putInt("adaptive_volume_sensitivity", sensitivity.ordinal).apply()

        Log.d(TAG, "📢 Sensibilidad cambiada a: $sensitivity")
    }

    /**
     * Obtiene la sensibilidad actual
     */
    fun getSensitivity(): VolumeSensitivity = currentSensitivity

    /**
     * Habilita/deshabilita volumen adaptativo
     */
    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        volumeHistory.clear()  // Limpiar historial al desactivar

        // Guardar en SharedPreferences
        val prefs = context.getSharedPreferences("musickontrol_settings", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("adaptive_volume_enabled", enabled).apply()

        Log.d(TAG, "📢 Volumen adaptativo: ${if (enabled) "ACTIVADO ✅" else "DESACTIVADO ❌"}")
    }

    /**
     * Obtiene estado actual
     */
    fun isEnabled(): Boolean = isEnabled

    /**
     * Limpia recursos
     */
    fun cleanup() {
        scope.cancel()
        volumeHistory.clear()
        Log.d(TAG, "🧹 AdaptiveVolumeManager limpiado")
    }
}