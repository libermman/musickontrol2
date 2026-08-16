package com.dnc1981.musickontrol.audio

import android.content.Context
import android.net.Uri
import android.util.Log

data class SavedAudio(
    val uri: Uri,
    val name: String,
    val type: String // "RADIO" o "USB"
)

class AutoplayManager(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "autoplay_prefs"
        private const val KEY_ENABLED = "autoplay_enabled"
        private const val KEY_LAST_URI = "last_audio_uri"
        private const val KEY_LAST_NAME = "last_audio_name"
        private const val KEY_LAST_TYPE = "last_audio_type"
        private const val KEY_TEST_MODE = "test_mode"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ✅ HABILITAR/DESHABILITAR AUTOPLAY
    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        Log.d("AutoplayManager", if (enabled) "✅ Autoplay ACTIVADO" else "❌ Autoplay DESACTIVADO")
    }

    fun isEnabled(): Boolean {
        val enabled = prefs.getBoolean(KEY_ENABLED, false)
        Log.d("AutoplayManager", if (enabled) "📢 Autoplay: ACTIVADO ✅" else "📢 Autoplay: DESACTIVADO ❌")
        return enabled
    }

    // ✅ GUARDAR AUDIO ACTUAL
    fun saveCurrentAudio(uri: Uri, name: String, type: String) {
        try {
            prefs.edit().apply {
                putString(KEY_LAST_URI, uri.toString())
                putString(KEY_LAST_NAME, name)
                putString(KEY_LAST_TYPE, type)
                apply()
            }
            Log.d("AutoplayManager", "💾 Audio guardado: $name ($type)")
        } catch (e: Exception) {
            Log.e("AutoplayManager", "❌ Error guardando audio: ${e.message}")
            e.printStackTrace()
        }
    }

    // ✅ OBTENER ÚLTIMO AUDIO GUARDADO
    fun getLastAudio(): SavedAudio? {
        return try {
            val uriString = prefs.getString(KEY_LAST_URI, null)
            val name = prefs.getString(KEY_LAST_NAME, null)
            val type = prefs.getString(KEY_LAST_TYPE, null)

            if (uriString != null && name != null && type != null) {
                val audio = SavedAudio(
                    uri = Uri.parse(uriString),
                    name = name,
                    type = type
                )
                Log.d("AutoplayManager", "✅ Audio recuperado: $name ($type)")
                audio
            } else {
                Log.d("AutoplayManager", "⚠️ No hay audio guardado")
                null
            }
        } catch (e: Exception) {
            Log.e("AutoplayManager", "❌ Error recuperando audio: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    // ✅ LIMPIAR DATOS GUARDADOS
    fun clearSavedAudio() {
        try {
            prefs.edit().apply {
                remove(KEY_LAST_URI)
                remove(KEY_LAST_NAME)
                remove(KEY_LAST_TYPE)
                apply()
            }
            Log.d("AutoplayManager", "🧹 Datos limpiados")
        } catch (e: Exception) {
            Log.e("AutoplayManager", "❌ Error limpiando datos: ${e.message}")
            e.printStackTrace()
        }
    }

    // ✅ TEST MODE - ACTIVAR/DESACTIVAR
    fun setTestMode(enabled: Boolean) {
        try {
            prefs.edit().putBoolean(KEY_TEST_MODE, enabled).apply()
            Log.d("AutoplayManager", if (enabled) "🧪 TEST MODE ACTIVADO" else "🧪 TEST MODE DESACTIVADO")
        } catch (e: Exception) {
            Log.e("AutoplayManager", "❌ Error en TEST MODE: ${e.message}")
            e.printStackTrace()
        }
    }

    // ✅ VERIFICAR SI TEST MODE ESTÁ ACTIVO
    fun isTestMode(): Boolean {
        val testMode = prefs.getBoolean(KEY_TEST_MODE, false)
        if (testMode) {
            Log.d("AutoplayManager", "🧪 TEST MODE DETECTADO - Activando autoplay")
        }
        return testMode
    }

    // ✅ OBTENER ESTADO COMPLETO (PARA DEBUG)
    fun getStatus(): String {
        val enabled = isEnabled()
        val testMode = isTestMode()
        val lastAudio = getLastAudio()

        return """
            ╔════════════════════════════════════╗
            ║     ESTADO DEL AUTOPLAY MANAGER    ║
            ╠════════════════════════════════════╣
            ║ Autoplay: ${if (enabled) "✅ ACTIVADO" else "❌ DESACTIVADO"}
            ║ Test Mode: ${if (testMode) "🧪 ACTIVO" else "⚪ INACTIVO"}
            ║ Último Audio: ${lastAudio?.name ?: "❌ NINGUNO"}
            ║ Tipo: ${lastAudio?.type ?: "N/A"}
            ║ URI: ${lastAudio?.uri?.toString()?.take(50) ?: "N/A"}...
            ╚════════════════════════════════════╝
        """.trimIndent()
    }

    // ✅ IMPRIMIR STATUS EN LOGCAT
    fun printStatus() {
        Log.d("AutoplayManager", getStatus())
    }
}
