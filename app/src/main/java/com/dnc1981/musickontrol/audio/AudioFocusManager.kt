package com.dnc1981.musickontrol.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.media3.common.Player

class AudioFocusManager(
    private val context: Context,
    private val exoPlayer: Player
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus: Boolean = false
    private var wasPlayingBeforeFocusLoss = false
    private var previousVolume = 1f

    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.d("AudioFocus", "✅ AUDIOFOCUS_GAIN")
                hasAudioFocus = true
                exoPlayer.volume = previousVolume

                if (wasPlayingBeforeFocusLoss && !exoPlayer.isPlaying) {
                    exoPlayer.play()
                }
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Log.d("AudioFocus", "⚠️ AUDIOFOCUS_LOSS_TRANSIENT")
                wasPlayingBeforeFocusLoss = exoPlayer.isPlaying
                if (exoPlayer.isPlaying) {
                    exoPlayer.pause()
                }
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Log.d("AudioFocus", "⚠️ AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK")
                if (exoPlayer.isPlaying) {
                    previousVolume = exoPlayer.volume
                    exoPlayer.volume = 0.3f
                }
            }

            AudioManager.AUDIOFOCUS_LOSS -> {
                Log.d("AudioFocus", "❌ AUDIOFOCUS_LOSS")
                hasAudioFocus = false
                wasPlayingBeforeFocusLoss = false
                if (exoPlayer.isPlaying) {
                    exoPlayer.pause()
                }
            }
        }
    }

    fun requestAudioFocus(): Boolean {
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributes)
                .setOnAudioFocusChangeListener(audioFocusListener)
                .setWillPauseWhenDucked(false)
                .build()

            audioManager.requestAudioFocus(audioFocusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                audioFocusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }

        hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        Log.d("AudioFocus", "🎧 requestAudioFocus() -> $result | granted=$hasAudioFocus")
        return hasAudioFocus
    }

    fun hasFocus(): Boolean = hasAudioFocus

    fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let {
                audioManager.abandonAudioFocusRequest(it)
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusListener)
        }
        hasAudioFocus = false
        Log.d("AudioFocus", "🔇 Audio focus abandonado")
    }

    fun release() {
        abandonAudioFocus()
    }
}
