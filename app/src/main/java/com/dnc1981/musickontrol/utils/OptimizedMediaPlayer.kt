package com.dnc1981.musickontrol.utils

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

object OptimizedMediaPlayer {

    fun createOptimizedPlayer(context: Context): ExoPlayer {
        // ✅ CONFIGURACIÓN OPTIMIZADA PARA RESPUESTA RÁPIDA
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(2000)  // Reducido de 4000
            .setReadTimeoutMs(2000)     // Reducido de 4000
            .setUserAgent("Mozilla/5.0 (Linux; Android 10) Mobile")

        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(dataSourceFactory)

        // ✅ LOAD CONTROL OPTIMIZADO
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                500,    // minBufferMs - Reducido de 2500
                5000,   // maxBufferMs - Reducido de 15000
                250,    // bufferForPlaybackMs - Reducido de 500
                500     // bufferForPlaybackAfterRebufferMs - Reducido de 1000
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, true)
            .build().apply {
                playWhenReady = true
            }
    }

    fun playMediaFast(
        exoPlayer: ExoPlayer,
        uri: Uri,
        autoPlay: Boolean = true
    ) {
        // ✅ REPRODUCCIÓN INMEDIATA SIN DELAY
        try {
            val mediaItem = MediaItem.fromUri(uri)
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            if (autoPlay) {
                exoPlayer.play()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun playPlaylistFast(
        exoPlayer: ExoPlayer,
        uris: List<Uri>,
        startIndex: Int = 0,
        autoPlay: Boolean = true
    ) {
        // ✅ REPRODUCCIÓN DE LISTA RÁPIDA
        try {
            val mediaItems = uris.map { MediaItem.fromUri(it) }
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            exoPlayer.setMediaItems(mediaItems)
            exoPlayer.seekTo(startIndex, 0)
            exoPlayer.prepare()
            if (autoPlay) {
                exoPlayer.play()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}