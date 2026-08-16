package com.dnc1981.musickontrol.service

import android.os.Bundle
import android.util.Log
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.dnc1981.musickontrol.manager.CrossfadeManager
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

class MediaSessionManager(
    private val exoPlayer: ExoPlayer,
    private val crossfadeManager: CrossfadeManager
) : MediaSession.Callback {

    companion object {
        private const val TAG = "MediaSessionManager"
    }

    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo
    ): MediaSession.ConnectionResult {
        Log.d(TAG, "✅ Controlador conectado: ${controller.packageName}")
        return MediaSession.ConnectionResult.accept(
            /* availableSessionCommands = */ MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS,
            /* availablePlayerCommands = */ MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
        )
    }

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle
    ): ListenableFuture<SessionResult> {
        Log.d(TAG, "🎮 Comando personalizado recibido: ${customCommand.customAction}")

        when (customCommand.customAction) {
            "PLAY" -> {
                Log.d(TAG, "▶️ Play")
                exoPlayer.play()
            }
            "PAUSE" -> {
                Log.d(TAG, "⏸️ Pause")
                exoPlayer.pause()
            }
            "NEXT" -> {
                Log.d(TAG, "⏭️ Siguiente - Aplicando Crossfade")
                crossfadeManager.aplicarCrossfade(exoPlayer) {
                    exoPlayer.seekToNext()
                }
            }
            "PREVIOUS" -> {
                Log.d(TAG, "⏮️ Anterior - Aplicando Crossfade")
                crossfadeManager.aplicarCrossfade(exoPlayer) {
                    exoPlayer.seekToPrevious()
                }
            }
        }

        return Futures.immediateFuture(
            SessionResult(SessionResult.RESULT_SUCCESS)
        )
    }
}