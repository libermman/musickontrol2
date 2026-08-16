package com.dnc1981.musickontrol.ui

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf

enum class FontSizeScale(val label: String, val scale: Float) {
    PEQUEÑO("Pequeño", 0.8f),
    NORMAL("Normal", 1.0f),
    GRANDE("Grande", 1.2f),
    MUY_GRANDE("Muy Grande", 1.4f),
    GIGANTE("Gigante", 1.6f)
}

val LocalFontSizeScale = staticCompositionLocalOf<MutableState<Float>> {
    mutableStateOf(1.0f)
}