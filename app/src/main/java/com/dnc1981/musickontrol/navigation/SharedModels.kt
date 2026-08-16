package com.dnc1981.musickontrol.navigation

import android.net.Uri

data class RadioStation(
    val name: String,
    val url: String
)

data class ElementoUsb(
    val name: String,
    val uri: Uri,
    val isDirectory: Boolean
)

// ✅ NUEVO: ESCALA DE FUENTE
enum class FontSizeScale(val scale: Float, val label: String) {
    PEQUEÑO(0.8f, "Pequeño"),
    NORMAL(1.0f, "Normal"),
    GRANDE(1.2f, "Grande"),
    MUY_GRANDE(1.5f, "Muy Grande")
}