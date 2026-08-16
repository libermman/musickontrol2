package com.dnc1981.musickontrol.utils

data class EqState(
    val isEnabled: Boolean = true,
    val presetName: String = "Normal",
    val bands: List<Float> = listOf(0f, 0f, 0f, 0f, 0f),
    val bassBoostStrength: Float = 0f,
    val isBoost30Active: Boolean = false,
    val isBoost50Active: Boolean = false
)