package com.dnc1981.musickontrol.audio

interface NormalizationListener {
    fun onNormalizationChanged(enabled: Boolean, level: Float, autoDetect: Boolean)
}
