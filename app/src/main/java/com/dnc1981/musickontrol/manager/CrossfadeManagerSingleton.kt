package com.dnc1981.musickontrol.manager

import android.content.Context
import android.util.Log

object CrossfadeManagerSingleton {
    private var instance: CrossfadeManager? = null
    private const val TAG = "CrossfadeManagerSingleton"

    fun getInstance(context: Context): CrossfadeManager {
        if (instance == null) {
            Log.d(TAG, "✅ Creando nueva instancia de CrossfadeManager (SINGLETON)")
            instance = CrossfadeManager(context.applicationContext)
            instance?.cargarCrossfadeDuration()
        } else {
            Log.d(TAG, "♻️ Reutilizando instancia existente de CrossfadeManager")
        }
        return instance!!
    }

    fun destroy() {
        Log.d(TAG, "🧹 Destruyendo Singleton")
        instance?.destroy()
        instance = null
    }
}
