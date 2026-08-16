package com.dnc1981.musickontrol.manager

import android.content.Context
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
object ExoPlayerManager {
    private const val TAG = "ExoPlayerManager"
    private var exoPlayer: ExoPlayer? = null

    fun getInstance(context: Context): ExoPlayer {
        if (exoPlayer == null) {
            Log.d(TAG, "🎵 Creando nueva instancia de ExoPlayer (SINGLETON)")

            val audioAttributes = AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build()

            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    /* minBufferMs = */ 1000,
                    /* maxBufferMs = */ 5000,
                    /* bufferForPlaybackMs = */ 250,
                    /* bufferForPlaybackAfterRebufferMs = */ 500
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()

            val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent("MusicKontrol/1.1.1 (Android Automotive)")
                .setAllowCrossProtocolRedirects(true)

            val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
            val mediaSourceFactory = DefaultMediaSourceFactory(context)
                .setDataSourceFactory(dataSourceFactory)

            exoPlayer = ExoPlayer.Builder(context)
                .setLoadControl(loadControl)
                .setMediaSourceFactory(mediaSourceFactory)
                .build().apply {
                    setAudioAttributes(audioAttributes, true)
                    playWhenReady = false
                    repeatMode = androidx.media3.common.Player.REPEAT_MODE_OFF
                    volume = 1f
                }

            Log.d(TAG, "✅ ExoPlayer inicializado correctamente")
        }

        return exoPlayer!!
    }

    fun release() {
        Log.d(TAG, "❌ Liberando ExoPlayer")
        exoPlayer?.release()
        exoPlayer = null
    }

    fun isInitialized(): Boolean = exoPlayer != null
}