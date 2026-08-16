package com.dnc1981.musickontrol.audio

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class NightModeColors(
    val backgroundColor: Color,
    val textColor: Color,
    val cardBg: Color,
    val deepGreen: Color,
    val neonCyan: Color
)

class NightModeManager(private val context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

    private val _isNightMode = MutableStateFlow(false)
    val isNightMode: StateFlow<Boolean> = _isNightMode

    private val _currentLux = MutableStateFlow(0f)
    val currentLux: StateFlow<Float> = _currentLux

    private val _hasSensor = MutableStateFlow(lightSensor != null)
    val hasSensor: StateFlow<Boolean> = _hasSensor

    private var luxThreshold = 50f
    private var isEnabled = true
    private var isListening = false
    private var isManualMode = false

    init {
        cargarPreferencias()
        Log.d("NightMode", if (lightSensor != null) "✅ Sensor de luz DISPONIBLE" else "⚠️ Sensor de luz NO disponible - Modo manual")
    }

    private fun cargarPreferencias() {
        val prefs = context.getSharedPreferences("musickontrol_settings", Context.MODE_PRIVATE)
        isEnabled = prefs.getBoolean("night_mode_enabled", true)
        luxThreshold = prefs.getFloat("night_mode_threshold", 50f)
        isManualMode = prefs.getBoolean("night_mode_manual", false)
        Log.d("NightMode", "✅ Preferencias cargadas: enabled=$isEnabled, threshold=$luxThreshold, manual=$isManualMode")
    }

    private fun guardarPreferencias() {
        val prefs = context.getSharedPreferences("musickontrol_settings", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("night_mode_enabled", isEnabled)
            .putFloat("night_mode_threshold", luxThreshold)
            .putBoolean("night_mode_manual", isManualMode)
            .apply()
    }

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        guardarPreferencias()

        if (enabled && lightSensor != null && !isListening) {
            startListening()
        } else if (!enabled && isListening) {
            stopListening()
            _isNightMode.value = false
        }

        Log.d("NightMode", if (enabled) "🌙 Modo Noche ACTIVADO" else "☀️ Modo Noche DESACTIVADO")
    }

    fun setThreshold(threshold: Float) {
        luxThreshold = threshold.coerceIn(10f, 200f)
        guardarPreferencias()
        Log.d("NightMode", "📊 Umbral actualizado: $luxThreshold lux")
    }

    // 🌙 NUEVO: Modo manual cuando NO hay sensor
    fun setManualNightMode(enabled: Boolean) {
        if (lightSensor == null) {
            isManualMode = enabled
            _isNightMode.value = enabled
            guardarPreferencias()
            Log.d("NightMode", if (enabled) "🌙 Modo Noche MANUAL ACTIVADO" else "☀️ Modo Día MANUAL ACTIVADO")
        }
    }

    fun startListening() {
        if (lightSensor == null) {
            Log.w("NightMode", "⚠️ Sensor de luz no disponible - Usando modo manual")
            return
        }

        if (isListening) return

        sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL)
        isListening = true
        Log.d("NightMode", "👂 Escuchando sensor de luz...")
    }

    fun stopListening() {
        if (!isListening) return

        sensorManager.unregisterListener(this)
        isListening = false
        Log.d("NightMode", "🔇 Sensor de luz detenido")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || !isEnabled || lightSensor == null) return

        val lux = event.values[0]
        _currentLux.value = lux

        val shouldBeNightMode = lux < luxThreshold

        if (shouldBeNightMode != _isNightMode.value) {
            _isNightMode.value = shouldBeNightMode
            Log.d("NightMode", if (shouldBeNightMode) "🌙 NOCHE ($lux lux)" else "☀️ DÍA ($lux lux)")
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun getDayColors(): NightModeColors = NightModeColors(
        backgroundColor = Color.Black,
        textColor = Color.White,
        cardBg = Color(0xFF1E1E1E),
        deepGreen = Color(0xFF0E501A),
        neonCyan = Color(0xFF00FFFF)
    )

    fun getNightColors(): NightModeColors = NightModeColors(
        backgroundColor = Color(0xFF0A0A0A),
        textColor = Color(0xFFE0E0E0),
        cardBg = Color(0xFF121212),
        deepGreen = Color(0xFF1B3A1B),
        neonCyan = Color(0xFF00D9D9)
    )

    fun release() {
        stopListening()
    }
}