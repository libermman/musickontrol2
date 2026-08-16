package com.dnc1981.musickontrol.manager

import android.content.Context
import android.content.SharedPreferences

class RepeatModeManager(context: Context) {
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        "musickontrol_repeat",
        Context.MODE_PRIVATE
    )

    private val repeatModeKey = "repeat_mode"

    fun getRepeatMode(): RepeatMode {
        val modeName = sharedPreferences.getString(repeatModeKey, RepeatMode.OFF.name)

        return try {
            RepeatMode.valueOf(modeName ?: RepeatMode.OFF.name)
        } catch (e: Exception) {
            RepeatMode.OFF
        }
    }

    fun setRepeatMode(mode: RepeatMode) {
        sharedPreferences.edit()
            .putString(repeatModeKey, mode.name)
            .apply()
    }

    fun toggleRepeatMode(): RepeatMode {
        val nextMode = when (getRepeatMode()) {
            RepeatMode.OFF -> RepeatMode.DISCO
            RepeatMode.DISCO -> RepeatMode.CANCION
            RepeatMode.CANCION -> RepeatMode.OFF
        }

        setRepeatMode(nextMode)
        return nextMode
    }

    fun getRepeatModeDisplayName(): String {
        return when (getRepeatMode()) {
            RepeatMode.OFF -> "OFF"
            RepeatMode.DISCO -> "DISCO"
            RepeatMode.CANCION -> "CANCIÓN"
        }
    }
}
