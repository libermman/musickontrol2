package com.dnc1981.musickontrol.navigation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.dnc1981.musickontrol.R
import com.dnc1981.musickontrol.manager.FavoritesManager
import com.dnc1981.musickontrol.manager.FavoriteTrack
import com.dnc1981.musickontrol.manager.RepeatModeManager
import com.dnc1981.musickontrol.manager.RepeatMode
import com.dnc1981.musickontrol.audio.AdaptiveVolumeManager
import com.dnc1981.musickontrol.audio.AutoplayManager
import com.dnc1981.musickontrol.ui.LocalFontSizeScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun isRadioUri(uri: Uri?): Boolean {
    val value = uri?.toString().orEmpty()
    return value.startsWith("http://", ignoreCase = true) ||
            value.startsWith("https://", ignoreCase = true)
}

private fun isRadioString(value: String?): Boolean {
    val safeValue = value.orEmpty()
    return safeValue.startsWith("http://", ignoreCase = true) ||
            safeValue.startsWith("https://", ignoreCase = true)
}

private fun isHlsRadioUri(uri: Uri?): Boolean {
    return uri?.toString().orEmpty().contains(".m3u8", ignoreCase = true)
}

private fun isGenericRadioTitle(value: String?): Boolean {
    val safe = value.orEmpty().trim()
    return safe.isBlank() ||
            safe.equals("Radio Online", ignoreCase = true) ||
            safe.equals("Streaming", ignoreCase = true) ||
            safe.equals("Esperando metadata...", ignoreCase = true)
}

private fun isGenericRadioArtist(value: String?): Boolean {
    val safe = value.orEmpty().trim()
    return safe.isBlank() ||
            safe.equals("Radio Online", ignoreCase = true) ||
            safe.equals("Streaming", ignoreCase = true) ||
            safe.equals("Radio HLS", ignoreCase = true)
}

private fun isUsefulSongMetadata(title: String, artist: String): Boolean {
    val safeTitle = title.trim()
    val safeArtist = artist.trim()

    if (safeTitle.isBlank()) return false

    if (isGenericRadioTitle(safeTitle) && isGenericRadioArtist(safeArtist)) {
        return false
    }

    if (safeTitle.contains(" - ") && !safeTitle.equals("Radio Online", ignoreCase = true)) {
        return true
    }

    if (!isGenericRadioTitle(safeTitle) && !isGenericRadioArtist(safeArtist)) {
        return true
    }

    if (!isGenericRadioTitle(safeTitle) &&
        !safeTitle.equals("RockFM", ignoreCase = true) &&
        !safeTitle.equals("MariskalRock Radio", ignoreCase = true)
    ) {
        return true
    }

    return false
}

private suspend fun extractMetadataFast(
    context: android.content.Context,
    uri: Uri,
    audioName: String
): CachedTrackMetadata = withContext(Dispatchers.IO) {
    var retriever: MediaMetadataRetriever? = null

    try {
        val scheme = uri.scheme

        if (scheme == null || (scheme != "file" && scheme != "content")) {
            return@withContext CachedTrackMetadata(
                title = audioName.ifBlank { "Archivo USB" },
                artist = "Desconocido",
                album = "Desconocido",
                artwork = null
            )
        }

        retriever = MediaMetadataRetriever()

        try {
            retriever.setDataSource(context, uri)
        } catch (e: Exception) {
            return@withContext CachedTrackMetadata(
                title = audioName.ifBlank { "Sin título" },
                artist = "Desconocido",
                album = "Desconocido",
                artwork = null
            )
        }

        val titleMeta = try {
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }

        val artistMeta = try {
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }

        val albumMeta = try {
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }

        val artwork = try {
            val artBytes = retriever.embeddedPicture

            if (artBytes != null && artBytes.isNotEmpty()) {
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }

                BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size, options)

                val sampleSize = when {
                    options.outWidth > 1200 || options.outHeight > 1200 -> 4
                    options.outWidth > 700 || options.outHeight > 700 -> 2
                    else -> 1
                }

                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.RGB_565
                }

                val bitmap = BitmapFactory.decodeByteArray(
                    artBytes,
                    0,
                    artBytes.size,
                    decodeOptions
                )

                if (bitmap != null && (bitmap.width > 500 || bitmap.height > 500)) {
                    Bitmap.createScaledBitmap(bitmap, 400, 400, true)
                } else {
                    bitmap
                }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }

        CachedTrackMetadata(
            title = titleMeta ?: audioName.ifBlank { "Sin título" },
            artist = artistMeta ?: "Desconocido",
            album = albumMeta ?: "Desconocido",
            artwork = artwork
        )
    } catch (e: Throwable) {
        e.printStackTrace()

        CachedTrackMetadata(
            title = audioName.ifBlank { "Sin título" },
            artist = "Desconocido",
            album = "Desconocido",
            artwork = null
        )
    } finally {
        try {
            retriever?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

private fun formatMillis(millis: Long): String {
    if (millis < 0L) return "0:00"

    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60

    return String.format("%d:%02d", minutes, seconds)
}
@Composable
fun AhoraSuenaScreen(
    audioUri: Uri?,
    audioName: String,
    exoPlayer: ExoPlayer,
    playlist: List<ElementoUsb>,
    currentIndex: Int,
    metadataReloadKey: Int,
    isShuffleActive: Boolean,
    onShuffleGlobal: () -> Unit,
    onPreviousFolder: () -> Unit,
    onNextFolder: () -> Unit,
    onFavorite: () -> Unit,
    adaptiveVolumeManager: AdaptiveVolumeManager? = null,
    autoplayManager: AutoplayManager? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val fontSizeScale = LocalFontSizeScale.current.value

    val deepGreen = Color(0xFF0E501A)
    val neonCyan = Color(0xFF00FFFF)

    val isRadioStream = isRadioUri(audioUri)

    val favoritesManager = remember { FavoritesManager(context) }
    val repeatModeManager = remember { RepeatModeManager(context) }

    var isPlaying by remember {
        mutableStateOf(exoPlayer.isPlaying)
    }

    var trackTitle by remember {
        mutableStateOf("")
    }

    var trackArtist by remember {
        mutableStateOf("")
    }

    var trackAlbum by remember {
        mutableStateOf("")
    }

    var albumArtBitmap by remember {
        mutableStateOf<Bitmap?>(null)
    }

    var isLiked by remember {
        mutableStateOf(false)
    }

    var currentRepeatMode by remember {
        mutableStateOf(repeatModeManager.getRepeatMode())
    }

    var currentPosition by remember {
        mutableStateOf(0L)
    }

    var duration by remember {
        mutableStateOf(0L)
    }

    var isSeekingByUser by remember {
        mutableStateOf(false)
    }

    fun applyRepeatMode(mode: RepeatMode) {
        exoPlayer.repeatMode = when (mode) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.DISCO -> Player.REPEAT_MODE_ALL
            RepeatMode.CANCION -> Player.REPEAT_MODE_ONE
        }
    }

    LaunchedEffect(Unit) {
        applyRepeatMode(currentRepeatMode)
    }

    LaunchedEffect(audioUri) {
        isLiked = audioUri?.let { favoritesManager.isFavorite(it) } ?: false
    }

    LaunchedEffect(audioUri, audioName, isRadioStream) {
        if (audioUri != null && autoplayManager != null) {
            val type = if (isRadioStream) "RADIO" else "USB"
            autoplayManager.saveCurrentAudio(audioUri, audioName, type)
            android.util.Log.d("Autoplay", "💾 Audio guardado: $audioName ($type)")
        }
    }

    // ✅ CARGAR METADATA CON CACHÉ MEJORADO
    LaunchedEffect(audioUri, audioName, metadataReloadKey) {
        val uriToLoad = audioUri

        trackTitle = ""
        trackArtist = ""
        trackAlbum = ""
        albumArtBitmap = null

        if (uriToLoad == null) {
            trackTitle = "Sin pista"
            trackArtist = "-"
            trackAlbum = "-"
            duration = 0L
            return@LaunchedEffect
        }

        // ✅ SI ES RADIO, OBTENER METADATA DEL PLAYER
        if (isRadioUri(uriToLoad)) {
            val currentMetadata = exoPlayer.currentMediaItem?.mediaMetadata

            val titleFromPlayer =
                currentMetadata?.title?.toString()
                    ?: currentMetadata?.displayTitle?.toString()
                    ?: ""

            val artistFromPlayer =
                currentMetadata?.artist?.toString()
                    ?: ""

            val albumFromPlayer =
                currentMetadata?.albumTitle?.toString()
                    ?: ""

            trackTitle = if (!isGenericRadioTitle(titleFromPlayer)) {
                titleFromPlayer
            } else {
                audioName.ifBlank { "Radio Online" }
            }

            trackArtist = if (!isGenericRadioArtist(artistFromPlayer)) {
                artistFromPlayer
            } else {
                if (isHlsRadioUri(uriToLoad)) "Radio HLS" else "Streaming"
            }

            trackAlbum = if (albumFromPlayer.isNotBlank() &&
                !albumFromPlayer.equals("Radio Online", ignoreCase = true)
            ) {
                albumFromPlayer
            } else {
                if (isHlsRadioUri(uriToLoad)) "Streaming HLS" else "Radio Online"
            }

            albumArtBitmap = null
            duration = 0L
            return@LaunchedEffect
        }

        // ✅ SI ES USB, USAR CACHÉ O EXTRAER METADATA
        val cacheKey = uriToLoad.toString()
        val shouldForceRefresh = AhoraSuenaMetadataCache.shouldRefresh()

        val cached = if (!shouldForceRefresh) {
            AhoraSuenaMetadataCache.get(cacheKey, metadataReloadKey)
        } else {
            null
        }

        if (cached != null) {
            trackTitle = cached.title
            trackArtist = cached.artist
            trackAlbum = cached.album
            albumArtBitmap = cached.artwork
            return@LaunchedEffect
        }

        trackTitle = audioName.ifBlank { "Reproduciendo..." }
        trackArtist = "Desconocido"
        trackAlbum = "Desconocido"
        albumArtBitmap = null

        val loadedMetadata = withContext(Dispatchers.IO) {
            extractMetadataFast(context, uriToLoad, audioName)
        }

        trackTitle = loadedMetadata.title
        trackArtist = loadedMetadata.artist
        trackAlbum = loadedMetadata.album
        albumArtBitmap = loadedMetadata.artwork

        AhoraSuenaMetadataCache.put(cacheKey, metadataReloadKey, loadedMetadata)
    }

    // ✅ ACTUALIZAR POSICIÓN EN TIEMPO REAL
    LaunchedEffect(isPlaying, audioUri) {
        while (isPlaying) {
            try {
                if (!isSeekingByUser) {
                    currentPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
                }

                duration = if (exoPlayer.duration > 0L) {
                    exoPlayer.duration
                } else {
                    0L
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            delay(100L)
        }
    }

    // ✅ MONITOREAR METADATA DE RADIO EN TIEMPO REAL
    LaunchedEffect(exoPlayer, isRadioStream, audioUri, audioName) {
        if (!isRadioStream) return@LaunchedEffect

        while (true) {
            delay(500L)

            try {
                val currentUriString = exoPlayer.currentMediaItem
                    ?.localConfiguration
                    ?.uri
                    ?.toString()
                    .orEmpty()

                if (!isRadioString(currentUriString)) {
                    continue
                }

                val mediaMetadata = exoPlayer.currentMediaItem?.mediaMetadata ?: continue

                val rawTitle = mediaMetadata.title?.toString()
                    ?: mediaMetadata.displayTitle?.toString()
                    ?: ""

                val rawArtist = mediaMetadata.artist?.toString() ?: ""
                val rawAlbum = mediaMetadata.albumTitle?.toString() ?: ""

                android.util.Log.d(
                    "RadioMetadata",
                    "📻 Metadata detectado: Artist='$rawArtist' Title='$rawTitle'"
                )

                if (!isUsefulSongMetadata(rawTitle, rawArtist)) {
                    val fallbackTitle = audioName.ifBlank { "Radio Online" }
                    val fallbackArtist = if (isHlsRadioUri(audioUri)) "Radio HLS" else "Streaming"
                    val fallbackAlbum = if (isHlsRadioUri(audioUri)) "Streaming HLS" else "Radio Online"

                    if (trackTitle.isBlank() ||
                        trackTitle.equals("Esperando metadata...", ignoreCase = true) ||
                        trackTitle.equals("Radio Online", ignoreCase = true)
                    ) {
                        trackTitle = fallbackTitle
                    }

                    if (trackArtist.isBlank() ||
                        trackArtist.equals("Radio Online", ignoreCase = true) ||
                        trackArtist.equals("Streaming", ignoreCase = true)
                    ) {
                        trackArtist = fallbackArtist
                    }

                    if (trackAlbum.isBlank() ||
                        trackAlbum.equals("Streaming", ignoreCase = true) ||
                        trackAlbum.equals("Radio Online", ignoreCase = true)
                    ) {
                        trackAlbum = fallbackAlbum
                    }

                    continue
                }

                var finalTitle = rawTitle.trim()
                var finalArtist = rawArtist.trim()

                if (finalTitle.contains(" - ") && isGenericRadioArtist(finalArtist)) {
                    val parts = finalTitle.split(" - ", limit = 2)

                    if (parts.size == 2 &&
                        parts[0].isNotBlank() &&
                        parts[1].isNotBlank()
                    ) {
                        finalArtist = parts[0].trim()
                        finalTitle = parts[1].trim()
                    }
                }

                val finalAlbum = if (rawAlbum.isNotBlank() &&
                    !rawAlbum.equals("Radio Online", ignoreCase = true) &&
                    !rawAlbum.equals("Streaming", ignoreCase = true)
                ) {
                    rawAlbum
                } else {
                    if (isHlsRadioUri(audioUri)) "Streaming HLS" else "Radio Online"
                }

                if (trackTitle != finalTitle || trackArtist != finalArtist || trackAlbum != finalAlbum) {
                    trackTitle = finalTitle
                    trackArtist = finalArtist
                    trackAlbum = finalAlbum

                    android.util.Log.d(
                        "RadioMetadata",
                        "✅ Actualizado canción real: $trackArtist - $trackTitle"
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // ✅ LISTENER DEL PLAYER
    DisposableEffect(exoPlayer, audioUri) {
        val listener = object : Player.Listener {

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    duration = if (exoPlayer.duration > 0L) {
                        exoPlayer.duration
                    } else {
                        0L
                    }

                    currentPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
                }

                if (playbackState == Player.STATE_ENDED) {
                    val uri = exoPlayer.currentMediaItem
                        ?.localConfiguration
                        ?.uri
                        ?.toString()
                        .orEmpty()

                    if (!isRadioString(uri) && playlist.isEmpty()) {
                        trackTitle = "Sin pista"
                        trackArtist = "-"
                        trackAlbum = "-"
                        albumArtBitmap = null
                        currentPosition = 0L
                        duration = 0L
                    }
                }
            }

            override fun onMediaItemTransition(
                mediaItem: MediaItem?,
                reason: Int
            ) {
                val uriString = mediaItem
                    ?.localConfiguration
                    ?.uri
                    ?.toString()
                    .orEmpty()

                val transitionIsRadio = isRadioString(uriString)

                if (transitionIsRadio) {
                    albumArtBitmap = null

                    val metadataTitle =
                        mediaItem?.mediaMetadata?.title?.toString()
                            ?: mediaItem?.mediaMetadata?.displayTitle?.toString()
                            ?: ""

                    val metadataArtist =
                        mediaItem?.mediaMetadata?.artist?.toString()
                            ?: ""

                    val metadataAlbum =
                        mediaItem?.mediaMetadata?.albumTitle?.toString()
                            ?: ""

                    trackTitle = if (!isGenericRadioTitle(metadataTitle)) {
                        metadataTitle
                    } else {
                        audioName.ifBlank { "Radio Online" }
                    }

                    trackArtist = if (!isGenericRadioArtist(metadataArtist)) {
                        metadataArtist
                    } else {
                        if (isHlsRadioUri(audioUri)) "Radio HLS" else "Streaming"
                    }

                    trackAlbum = if (metadataAlbum.isNotBlank() &&
                        !metadataAlbum.equals("Radio Online", ignoreCase = true) &&
                        !metadataAlbum.equals("Streaming", ignoreCase = true)
                    ) {
                        metadataAlbum
                    } else {
                        if (isHlsRadioUri(audioUri)) "Streaming HLS" else "Radio Online"
                    }

                    duration = 0L
                    currentPosition = 0L
                    return
                }

                duration = if (exoPlayer.duration > 0L) {
                    exoPlayer.duration
                } else {
                    0L
                }

                currentPosition = 0L
            }

            override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
                val currentUriString = exoPlayer.currentMediaItem
                    ?.localConfiguration
                    ?.uri
                    ?.toString()
                    .orEmpty()

                val metadataIsRadio = isRadioString(currentUriString)

                if (!metadataIsRadio) {
                    return
                }

                val rawTitle =
                    mediaMetadata.title?.toString()
                        ?: mediaMetadata.displayTitle?.toString()
                        ?: ""

                val rawArtist =
                    mediaMetadata.artist?.toString()
                        ?: ""

                val rawAlbum =
                    mediaMetadata.albumTitle?.toString()
                        ?: ""

                albumArtBitmap = null

                android.util.Log.d(
                    "RadioMetadata",
                    "✅ onMediaMetadataChanged RAW: Artist='$rawArtist' Title='$rawTitle' Album='$rawAlbum'"
                )

                if (!isUsefulSongMetadata(rawTitle, rawArtist)) {
                    val fallbackTitle = audioName.ifBlank { "Radio Online" }
                    val fallbackArtist = if (isHlsRadioUri(audioUri)) "Radio HLS" else "Streaming"
                    val fallbackAlbum = if (isHlsRadioUri(audioUri)) "Streaming HLS" else "Radio Online"

                    if (trackTitle.isBlank() ||
                        trackTitle.equals("Esperando metadata...", ignoreCase = true) ||
                        trackTitle.equals("Radio Online", ignoreCase = true)
                    ) {
                        trackTitle = fallbackTitle
                    }

                    if (trackArtist.isBlank() ||
                        trackArtist.equals("Radio Online", ignoreCase = true) ||
                        trackArtist.equals("Streaming", ignoreCase = true)
                    ) {
                        trackArtist = fallbackArtist
                    }

                    if (trackAlbum.isBlank() ||
                        trackAlbum.equals("Streaming", ignoreCase = true) ||
                        trackAlbum.equals("Radio Online", ignoreCase = true)
                    ) {
                        trackAlbum = fallbackAlbum
                    }

                    android.util.Log.d(
                        "RadioMetadata",
                        "⚠️ Metadata genérica ignorada. Manteniendo: $trackArtist - $trackTitle"
                    )

                    duration = 0L
                    return
                }

                var finalTitle = rawTitle.trim()
                var finalArtist = rawArtist.trim()

                if (finalTitle.contains(" - ") && isGenericRadioArtist(finalArtist)) {
                    val parts = finalTitle.split(" - ", limit = 2)

                    if (parts.size == 2 &&
                        parts[0].isNotBlank() &&
                        parts[1].isNotBlank()
                    ) {
                        finalArtist = parts[0].trim()
                        finalTitle = parts[1].trim()
                    }
                }

                val finalAlbum = if (rawAlbum.isNotBlank() &&
                    !rawAlbum.equals("Radio Online", ignoreCase = true) &&
                    !rawAlbum.equals("Streaming", ignoreCase = true)
                ) {
                    rawAlbum
                } else {
                    if (isHlsRadioUri(audioUri)) "Streaming HLS" else "Radio Online"
                }

                trackTitle = finalTitle
                trackArtist = finalArtist.ifBlank {
                    if (isHlsRadioUri(audioUri)) "Radio HLS" else "Streaming"
                }
                trackAlbum = finalAlbum

                android.util.Log.d(
                    "RadioMetadata",
                    "✅ onMediaMetadataChanged aplicado: $trackArtist - $trackTitle"
                )

                duration = 0L
            }
        }

        exoPlayer.addListener(listener)

        isPlaying = exoPlayer.isPlaying
        duration = if (exoPlayer.duration > 0L) {
            exoPlayer.duration
        } else {
            0L
        }

        currentPosition = exoPlayer.currentPosition.coerceAtLeast(0L)

        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    val formattedCurrent = remember(currentPosition) {
        formatMillis(currentPosition)
    }

    val formattedRemaining = remember(currentPosition, duration, isRadioStream) {
        if (isRadioStream || duration <= 0L) {
            "--:--"
        } else {
            val remaining = (duration - currentPosition).coerceAtLeast(0L)
            "-" + formatMillis(remaining)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = (8 * fontSizeScale).dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy((32 * fontSizeScale).dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Box(
                modifier = Modifier
                    .size((370 * fontSizeScale).dp)
                    .background(Color(0xFF2B2B2B), RoundedCornerShape((16 * fontSizeScale).dp))
                    .clip(RoundedCornerShape((16 * fontSizeScale).dp)),
                contentAlignment = Alignment.Center
            ) {
                if (isRadioStream) {
                    Image(
                        painter = painterResource(id = R.drawable.radio_cover),
                        contentDescription = "Carátula Radio Online",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else if (albumArtBitmap != null) {
                    Image(
                        bitmap = albumArtBitmap!!.asImageBitmap(),
                        contentDescription = "Carátula ID3",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = "USB",
                        color = Color(0xFF6E6E6E),
                        fontSize = (52 * fontSizeScale).sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center
            ) {
                if (isRadioStream) {
                    Text(
                        text = "EMISORA: $audioName",
                        color = Color(0xFF00FFFF),
                        fontSize = (26 * fontSizeScale).sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height((14 * fontSizeScale).dp))
                }

                Text(
                    text = "ARTISTA: $trackArtist",
                    color = Color.White,
                    fontSize = (26 * fontSizeScale).sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height((14 * fontSizeScale).dp))

                Text(
                    text = "DISCO: $trackAlbum",
                    color = Color.White,
                    fontSize = (26 * fontSizeScale).sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height((14 * fontSizeScale).dp))

                Text(
                    text = "CANCION: $trackTitle",
                    color = Color.White,
                    fontSize = (26 * fontSizeScale).sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height((28 * fontSizeScale).dp))

                ClickableSeekBar(
                    currentPosition = currentPosition,
                    duration = duration,
                    enabled = !isRadioStream && duration > 0L,
                    onSeekPreview = { newPosition ->
                        isSeekingByUser = true
                        currentPosition = newPosition.coerceIn(0L, duration)
                    },
                    onSeekFinished = { newPosition ->
                        val safePosition = newPosition.coerceIn(0L, duration)
                        currentPosition = safePosition
                        exoPlayer.seekTo(safePosition)
                        isSeekingByUser = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    fontSizeScale = fontSizeScale
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = (4 * fontSizeScale).dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (isRadioStream) "LIVE" else formattedCurrent,
                        color = Color.Gray,
                        fontSize = (15 * fontSizeScale).sp
                    )

                    Text(
                        text = formattedRemaining,
                        color = Color.Gray,
                        fontSize = (15 * fontSizeScale).sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height((12 * fontSizeScale).dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = (4 * fontSizeScale).dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {

            ControlMediaButton(
                backgroundColor = deepGreen,
                defaultBorderColor = neonCyan,
                hasTemporaryEffect = true,
                fontSizeScale = fontSizeScale,
                onClick = {
                    if (audioUri != null) {
                        if (isLiked) {
                            favoritesManager.removeFavorite(audioUri)
                            isLiked = false
                            Toast.makeText(
                                context,
                                "Eliminado de favoritos",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            favoritesManager.addFavorite(
                                FavoriteTrack(
                                    uri = audioUri.toString(),
                                    title = trackTitle,
                                    artist = trackArtist,
                                    album = trackAlbum
                                )
                            )
                            isLiked = true
                            Toast.makeText(
                                context,
                                "Añadido a favoritos",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Default.ThumbUp,
                    contentDescription = "Like",
                    tint = if (isLiked) Color.Green else Color.White,
                    modifier = Modifier.size((36 * fontSizeScale).dp)
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy((10 * fontSizeScale).dp),
                verticalAlignment = Alignment.CenterVertically
            ) {

                ControlMediaButton(
                    backgroundColor = deepGreen,
                    defaultBorderColor = neonCyan,
                    isActive = currentRepeatMode != RepeatMode.OFF,
                    fontSizeScale = fontSizeScale,
                    onClick = {
                        if (!isRadioStream) {
                            currentRepeatMode = repeatModeManager.toggleRepeatMode()
                            applyRepeatMode(currentRepeatMode)

                            val modeName = when (currentRepeatMode) {
                                RepeatMode.OFF -> "Repetición: OFF"
                                RepeatMode.DISCO -> "Repetición: DISCO"
                                RepeatMode.CANCION -> "Repetición: CANCIÓN"
                            }
                            Toast.makeText(context, modeName, Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text(
                        text = when (currentRepeatMode) {
                            RepeatMode.OFF -> "OFF"
                            RepeatMode.DISCO -> "DISCO"
                            RepeatMode.CANCION -> "CANCIÓN"
                        },
                        color = if (isRadioStream) Color.Gray else Color.White,
                        fontSize = (12 * fontSizeScale).sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                ControlMediaButton(
                    backgroundColor = deepGreen,
                    defaultBorderColor = neonCyan,
                    isActive = isShuffleActive,
                    fontSizeScale = fontSizeScale,
                    onClick = {
                        if (!isRadioStream) {
                            onShuffleGlobal()
                        }
                    }
                ) {
                    DiceButtom3D(
                        isActive = isShuffleActive,
                        isDisabled = isRadioStream,
                        fontSizeScale = fontSizeScale
                    )
                }

                ControlMediaButton(
                    backgroundColor = deepGreen,
                    defaultBorderColor = neonCyan,
                    hasTemporaryEffect = true,
                    fontSizeScale = fontSizeScale,
                    onClick = {
                        if (!isRadioStream) {
                            onPreviousFolder()
                        }
                    }
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size((36 * fontSizeScale).dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = "Carpeta anterior",
                            tint = if (isRadioStream) Color.Gray else Color.White,
                            modifier = Modifier.fillMaxSize()
                        )

                        Box(
                            modifier = Modifier
                                .size((14 * fontSizeScale).dp)
                                .align(Alignment.Center)
                                .offset(x = (8 * fontSizeScale).dp, y = (-2 * fontSizeScale).dp)
                                .background(Color.White, RoundedCornerShape((2 * fontSizeScale).dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .width((8 * fontSizeScale).dp)
                                    .height((2 * fontSizeScale).dp)
                                    .background(deepGreen)
                            )
                        }
                    }
                }

                ControlMediaButton(
                    backgroundColor = deepGreen,
                    defaultBorderColor = neonCyan,
                    hasTemporaryEffect = true,
                    fontSizeScale = fontSizeScale,
                    onClick = {
                        if (exoPlayer.hasPreviousMediaItem()) {
                            exoPlayer.seekToPreviousMediaItem()
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Anterior",
                        tint = Color.White,
                        modifier = Modifier.size((36 * fontSizeScale).dp)
                    )
                }

                ControlMediaButton(
                    backgroundColor = deepGreen,
                    defaultBorderColor = neonCyan,
                    isActive = isPlaying,
                    fontSizeScale = fontSizeScale,
                    onClick = {
                        if (exoPlayer.isPlaying) {
                            exoPlayer.pause()
                        } else {
                            exoPlayer.play()
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (isPlaying) {
                            Icons.Default.Pause
                        } else {
                            Icons.Default.PlayArrow
                        },
                        contentDescription = "Play/Pausa",
                        tint = Color.White,
                        modifier = Modifier.size((36 * fontSizeScale).dp)
                    )
                }

                ControlMediaButton(
                    backgroundColor = deepGreen,
                    defaultBorderColor = neonCyan,
                    hasTemporaryEffect = true,
                    fontSizeScale = fontSizeScale,
                    onClick = {
                        if (exoPlayer.hasNextMediaItem()) {
                            exoPlayer.seekToNextMediaItem()
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Siguiente",
                        tint = Color.White,
                        modifier = Modifier.size((36 * fontSizeScale).dp)
                    )
                }

                ControlMediaButton(
                    backgroundColor = deepGreen,
                    defaultBorderColor = neonCyan,
                    hasTemporaryEffect = true,
                    fontSizeScale = fontSizeScale,
                    onClick = {
                        if (!isRadioStream) {
                            onNextFolder()
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.CreateNewFolder,
                        contentDescription = "Carpeta siguiente",
                        tint = if (isRadioStream) Color.Gray else Color.White,
                        modifier = Modifier.size((36 * fontSizeScale).dp)
                    )
                }

                ControlMediaButton(
                    backgroundColor = deepGreen,
                    defaultBorderColor = neonCyan,
                    isActive = false,
                    fontSizeScale = fontSizeScale,
                    onClick = {
                        onFavorite()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Favorito",
                        tint = Color.Red,
                        modifier = Modifier.size((36 * fontSizeScale).dp)
                    )
                }
            }
        }
    }
}


@Composable
fun ClickableSeekBar(
    currentPosition: Long,
    duration: Long,
    enabled: Boolean,
    onSeekPreview: (Long) -> Unit,
    onSeekFinished: (Long) -> Unit,
    modifier: Modifier = Modifier,
    fontSizeScale: Float = 1f
) {
    val cyan = Color(0xFF00FFFF)

    var previewPosition by remember(currentPosition, duration) {
        mutableStateOf(currentPosition)
    }

    Box(
        modifier = modifier
            .height((46 * fontSizeScale).dp)
            .pointerInput(enabled, duration) {
                if (!enabled || duration <= 0L) {
                    return@pointerInput
                }

                detectTapGestures { offset ->
                    val width = size.width.toFloat().coerceAtLeast(1f)
                    val percent = (offset.x / width).coerceIn(0f, 1f)
                    val newPosition = (duration.toFloat() * percent).toLong()

                    previewPosition = newPosition
                    onSeekPreview(newPosition)
                    onSeekFinished(newPosition)
                }
            }
            .pointerInput(enabled, duration) {
                if (!enabled || duration <= 0L) {
                    return@pointerInput
                }

                detectDragGestures(
                    onDragStart = { offset ->
                        val width = size.width.toFloat().coerceAtLeast(1f)
                        val percent = (offset.x / width).coerceIn(0f, 1f)
                        val newPosition = (duration.toFloat() * percent).toLong()

                        previewPosition = newPosition
                        onSeekPreview(newPosition)
                    },
                    onDrag = { change, _ ->
                        val width = size.width.toFloat().coerceAtLeast(1f)
                        val percent = (change.position.x / width).coerceIn(0f, 1f)
                        val newPosition = (duration.toFloat() * percent).toLong()

                        previewPosition = newPosition
                        onSeekPreview(newPosition)
                        change.consume()
                    },
                    onDragEnd = {
                        onSeekFinished(previewPosition.coerceIn(0L, duration))
                    },
                    onDragCancel = {
                        onSeekFinished(previewPosition.coerceIn(0L, duration))
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height((32 * fontSizeScale).dp)
        ) {
            val trackHeight = (8 * fontSizeScale).dp.toPx()
            val thumbRadius = (12 * fontSizeScale).dp.toPx()
            val centerY = size.height / 2f

            val safeDuration = duration.coerceAtLeast(1L)
            val safePosition = currentPosition.coerceIn(0L, safeDuration)

            val progress = if (enabled) {
                (safePosition.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }

            val activeWidth = size.width * progress

            // ✅ BARRA DE FONDO (gris)
            drawRoundRect(
                color = if (enabled) Color.White else Color.Gray,
                topLeft = Offset(0f, centerY - trackHeight / 2f),
                size = Size(size.width, trackHeight),
                cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f)
            )

            // ✅ BARRA DE PROGRESO (cyan)
            drawRoundRect(
                color = if (enabled) cyan else Color.DarkGray,
                topLeft = Offset(0f, centerY - trackHeight / 2f),
                size = Size(activeWidth, trackHeight),
                cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f)
            )

            // ✅ THUMB (círculo)
            val thumbX = activeWidth.coerceIn(thumbRadius, size.width - thumbRadius)

            drawCircle(
                color = if (enabled) cyan else Color.DarkGray,
                radius = thumbRadius,
                center = Offset(thumbX, centerY)
            )
        }
    }
}

@Composable
fun ControlMediaButton(
    backgroundColor: Color,
    defaultBorderColor: Color,
    isActive: Boolean = false,
    hasTemporaryEffect: Boolean = false,
    fontSizeScale: Float = 1f,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    var isTemporaryPressed by remember {
        mutableStateOf(false)
    }

    val coroutineScope = rememberCoroutineScope()
    val orangeColor = Color(0xFFFFA500)

    val borderColor = when {
        isTemporaryPressed -> orangeColor
        isActive -> orangeColor
        else -> defaultBorderColor
    }

    Box(
        modifier = Modifier
            .width((92 * fontSizeScale).dp)
            .height((76 * fontSizeScale).dp)
            .clip(RoundedCornerShape((16 * fontSizeScale).dp))
            .background(backgroundColor)
            .border((6 * fontSizeScale).dp, borderColor, RoundedCornerShape((16 * fontSizeScale).dp))
            .clickable {
                if (hasTemporaryEffect) {
                    isTemporaryPressed = true

                    coroutineScope.launch {
                        delay(250)
                        isTemporaryPressed = false
                    }
                }

                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
fun DiceButtom3D(
    isActive: Boolean = false,
    isDisabled: Boolean = false,
    fontSizeScale: Float = 1f
) {
    val diceColor = when {
        isDisabled -> Color.Gray
        isActive -> Color(0xFFFFA500)
        else -> Color.White
    }

    Canvas(
        modifier = Modifier.size((36 * fontSizeScale).dp)
    ) {
        val size = this.size.minDimension
        val offset = (this.size.width - size) / 2f

        // ✅ CARA FRONTAL (principal)
        drawRect(
            color = diceColor,
            topLeft = Offset(offset + size * 0.1f, offset + size * 0.2f),
            size = Size(size * 0.7f, size * 0.7f)
        )

        // ✅ CARA SUPERIOR (sombreada)
        drawRect(
            color = diceColor.copy(alpha = 0.7f),
            topLeft = Offset(offset + size * 0.15f, offset + size * 0.1f),
            size = Size(size * 0.7f, size * 0.15f)
        )

        // ✅ CARA DERECHA (sombreada)
        drawRect(
            color = diceColor.copy(alpha = 0.5f),
            topLeft = Offset(offset + size * 0.8f, offset + size * 0.2f),
            size = Size(size * 0.15f, size * 0.7f)
        )

        // ✅ PUNTOS DEL DADO (1-6 aleatorio visual)
        val dotRadius = size * 0.06f
        val dotColor = Color(0xFF0E501A) // Verde oscuro de fondo

        // Patrón de 6 puntos (como un dado real)
        val dotPositions = listOf(
            Offset(offset + size * 0.25f, offset + size * 0.35f), // Superior izquierda
            Offset(offset + size * 0.75f, offset + size * 0.35f), // Superior derecha
            Offset(offset + size * 0.25f, offset + size * 0.55f), // Centro izquierda
            Offset(offset + size * 0.75f, offset + size * 0.55f), // Centro derecha
            Offset(offset + size * 0.25f, offset + size * 0.75f), // Inferior izquierda
            Offset(offset + size * 0.75f, offset + size * 0.75f)  // Inferior derecha
        )

        // Dibujar puntos
        dotPositions.forEach { position ->
            drawCircle(
                color = dotColor,
                radius = dotRadius,
                center = position
            )
        }

        // ✅ BORDE 3D
        drawRect(
            color = diceColor.copy(alpha = 0.3f),
            topLeft = Offset(offset + size * 0.1f, offset + size * 0.2f),
            size = Size(size * 0.7f, size * 0.7f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
        )
    }
}
