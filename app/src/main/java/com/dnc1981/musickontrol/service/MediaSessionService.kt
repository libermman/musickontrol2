package com.dnc1981.musickontrol.service

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.dnc1981.musickontrol.audio.AudioNormalizerProcessor
import com.dnc1981.musickontrol.audio.NormalizationListener
import com.dnc1981.musickontrol.manager.GaplessPlaybackManager
import com.dnc1981.musickontrol.manager.CrossfadeManagerSingleton
import com.dnc1981.musickontrol.manager.ExoPlayerManager


class MediaSessionService : MediaSessionService(), SharedPreferences.OnSharedPreferenceChangeListener {

    companion object {
        private const val TAG = "MediaSessionService"
    }

    private var mediaSession: MediaSession? = null
    private var exoPlayer: ExoPlayer? = null
    private lateinit var gaplessManager: GaplessPlaybackManager
    private lateinit var audioNormalizer: AudioNormalizerProcessor
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "🎵 MediaSessionService creado")

        try {
            // ✅ INICIALIZAR SHARED PREFERENCES LISTENER
            sharedPreferences = getSharedPreferences("musickontrol_settings", Context.MODE_PRIVATE)
            sharedPreferences.registerOnSharedPreferenceChangeListener(this)
            Log.d(TAG, "✅ SharedPreferences Listener registrado")

            // ✅ INICIALIZAR MANAGERS
            gaplessManager = GaplessPlaybackManager(this)
            audioNormalizer = AudioNormalizerProcessor(this)
            Log.d(TAG, "✅ AudioNormalizer inicializado")

            // ✅ USAR SINGLETON PARA CROSSFADE
            val crossfadeManager = CrossfadeManagerSingleton.getInstance(this)
            Log.d(TAG, "✅ CrossfadeManager Singleton obtenido")

            // Inicializar ExoPlayer
            exoPlayer = ExoPlayerManager.getInstance(this)
            Log.d(TAG, "✅ ExoPlayer inicializado")

            // ✅ APLICAR GAPLESS PLAYBACK
            val gaplessEnabled = gaplessManager.cargarGaplessEnabled()
            gaplessManager.aplicarGaplessPlayback(exoPlayer!!, gaplessEnabled)
            Log.d(TAG, "🎵 Gapless: ${if (gaplessEnabled) "ACTIVADO" else "DESACTIVADO"}")

            // ✅ CARGAR CROSSFADE
            val crossfadeDuration = crossfadeManager.cargarCrossfadeDuration()
            Log.d(TAG, "🎵 Crossfade cargado: ${crossfadeDuration}ms")

            // ✅ APLICAR NORMALIZACIÓN AL INICIAR
            audioNormalizer.aplicarNormalizacion(exoPlayer!!)
            Log.d(TAG, "🔊 Normalización aplicada al iniciar")

            // ✅ REGISTRAR LISTENER PARA CAMBIOS DE NORMALIZACIÓN
            audioNormalizer.setListener(object : NormalizationListener {
                override fun onNormalizationChanged(enabled: Boolean, level: Float, autoDetect: Boolean) {
                    Log.d(TAG, "📡 Cambio de normalización detectado desde listener - Aplicando...")
                    Log.d(TAG, "🔊 Normalización: Habilitada=$enabled, Nivel=${String.format("%.2f", level)}dB, AutoDetect=$autoDetect")
                    audioNormalizer.aplicarNormalizacion(exoPlayer!!)
                }
            })

            // ✅ AGREGAR LISTENER PARA DETECTAR CAMBIO DE CANCIÓN
            exoPlayer!!.addListener(object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    super.onMediaItemTransition(mediaItem, reason)
                    Log.d(TAG, "🎵 Cambio de canción detectado")
                    Log.d(TAG, "📊 Razón: $reason")

                    // ✅ APLICAR NORMALIZACIÓN EN CADA CANCIÓN
                    audioNormalizer.aplicarNormalizacion(exoPlayer!!)
                    Log.d(TAG, "🔊 Normalización aplicada en cambio de canción")

                    // ✅ APLICAR CROSSFADE EN CAMBIOS MANUALES (SEEK)
                    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK) {
                        Log.d(TAG, "⏭️ Usuario cambió manualmente - Aplicando Crossfade")
                        crossfadeManager.aplicarCrossfade(exoPlayer!!) {
                            Log.d(TAG, "✅ Crossfade completado")
                        }
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    super.onPlaybackStateChanged(playbackState)
                    when (playbackState) {
                        Player.STATE_READY -> Log.d(TAG, "▶️ Estado: READY")
                        Player.STATE_BUFFERING -> Log.d(TAG, "⏳ Estado: BUFFERING")
                        Player.STATE_ENDED -> Log.d(TAG, "⏹️ Estado: ENDED")
                        Player.STATE_IDLE -> Log.d(TAG, "⏸️ Estado: IDLE")
                    }
                }
            })

            // Crear MediaSession
            mediaSession = MediaSession.Builder(this, exoPlayer!!)
                .setCallback(MediaSessionManager(exoPlayer!!, crossfadeManager))
                .build()

            Log.d(TAG, "✅ MediaSession inicializada")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error al crear MediaSession: ${e.message}", e)
            e.printStackTrace()
        }
    }

    // ✅ ESCUCHAR CAMBIOS EN SHAREDPREFERENCES
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        Log.d(TAG, "📡 SharedPreference cambió: $key")

        when (key) {
            "normalization_enabled", "normalization_level", "normalization_auto_detect" -> {
                Log.d(TAG, "🔊 Cambio de normalización detectado - Aplicando...")
                if (exoPlayer != null) {
                    audioNormalizer.aplicarNormalizacion(exoPlayer!!)
                }
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        Log.d(TAG, "📱 Controlador conectado: ${controllerInfo.packageName}")
        return mediaSession
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "❌ MediaSessionService destruido")

        try {
            // ✅ DESREGISTRAR LISTENER
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)

            mediaSession?.release()
            exoPlayer?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error al liberar recursos: ${e.message}")
        }
    }
}
