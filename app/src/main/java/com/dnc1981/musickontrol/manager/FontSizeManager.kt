package com.dnc1981.musickontrol.manager

import android.content.Context
import android.content.SharedPreferences
import com.dnc1981.musickontrol.navigation.FontSizeScale

class FontSizeManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "musickontrol_settings",
        Context.MODE_PRIVATE
    )

    fun loadFontSizeScale(): FontSizeScale {
        // ✅ CORREGIDO: Leer como String (no como Float)
        val savedScale = prefs.getString("font_size_scale", "NORMAL") ?: "NORMAL"
        return try {
            FontSizeScale.valueOf(savedScale)
        } catch (e: Exception) {
            FontSizeScale.NORMAL
        }
    }

    fun saveFontSizeScale(scale: FontSizeScale) {
        // ✅ Guardar como String (nombre del enum)
        prefs.edit().putString("font_size_scale", scale.name).apply()
    }

    // ✅ NUEVO: Método para limpiar datos corruptos
    fun clearCorruptedData() {
        try {
            prefs.edit().remove("font_size_scale").apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}