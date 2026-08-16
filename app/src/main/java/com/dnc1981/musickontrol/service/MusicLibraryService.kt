package com.dnc1981.musickontrol.service

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.dnc1981.musickontrol.manager.ExoPlayerManager

class MusicLibraryService : MediaLibraryService() {

    private var mediaLibrarySession: MediaLibrarySession? = null
    private var player: ExoPlayer? = null

    override fun onCreate() {
        super.onCreate()

        // Utilizamos el mismo ExoPlayer compartido por la aplicación.
        player = ExoPlayerManager.getInstance(this)

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        player?.setAudioAttributes(audioAttributes, true)

        val librarySessionCallback = object : MediaLibrarySession.Callback {
        }

        player?.let { exoPlayer ->
            mediaLibrarySession = MediaLibrarySession.Builder(
                this,
                exoPlayer,
                librarySessionCallback
            ).build()
        }
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaLibrarySession? {
        return mediaLibrarySession
    }

    override fun onDestroy() {
        mediaLibrarySession?.release()
        mediaLibrarySession = null

        // IMPORTANTE:
        // No liberamos aquí el ExoPlayer compartido porque también
        // lo utiliza la interfaz principal de MusicKontrol.
        player = null

        super.onDestroy()
    }
}