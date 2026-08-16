package com.dnc1981.musickontrol.utils

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object EqPreferences {
    private const val PREFS_NAME = "eq_preferences"
    private const val KEY_EQ_STATE = "eq_state"

    fun saveEqState(context: Context, state: EqState) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val gson = Gson()
            val json = gson.toJson(state)
            prefs.edit().putString(KEY_EQ_STATE, json).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadEqState(context: Context): EqState {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString(KEY_EQ_STATE, null)

            if (json != null) {
                val gson = Gson()
                val type = object : TypeToken<EqState>() {}.type
                gson.fromJson(json, type)
            } else {
                EqState()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            EqState()
        }
    }

    fun clearEqState(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().remove(KEY_EQ_STATE).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}