package com.dnc1981.musickontrol.navigation

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.documentfile.provider.DocumentFile
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.dnc1981.musickontrol.audio.AdaptiveVolumeManager
import com.dnc1981.musickontrol.audio.AudioFocusManager
import com.dnc1981.musickontrol.audio.AudioNormalizerProcessor
import com.dnc1981.musickontrol.audio.AutoplayManager
import com.dnc1981.musickontrol.audio.NightModeManager
import com.dnc1981.musickontrol.manager.ExoPlayerManager
import com.dnc1981.musickontrol.manager.FavoritesManager
import com.dnc1981.musickontrol.manager.GaplessPlaybackManager
import com.dnc1981.musickontrol.ui.LocalFontSizeScale
import com.dnc1981.musickontrol.utils.EqAudioController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

private data class FolderNavigationResult(
    val folderPath: String?,
    val songs: List<String>
)

private data class TrackMetadata(
    val title: String,
    val artist: String,
    val album: String
)

internal fun cargarRadioStationsGuardadas(context: Context): List<RadioStation> {
    val prefs = context.getSharedPreferences("musickontrol_radios", Context.MODE_PRIVATE)
    val raw = prefs.getString("saved_stations", null) ?: return emptyList()
    if (raw.isBlank()) return emptyList()
    return raw.split(";;;").mapNotNull { entry ->
        val parts = entry.split("|||")
        if (parts.size == 2) RadioStation(parts[0], parts[1]) else null
    }
}

internal fun guardarRadioStations(context: Context, stations: List<RadioStation>) {
    val prefs = context.getSharedPreferences("musickontrol_radios", Context.MODE_PRIVATE)
    val raw = stations.joinToString(";;;") { "${it.name}|||${it.url}" }
    prefs.edit().putString("saved_stations", raw).apply()
}

private fun isHttpRadioUri(uri: Uri?): Boolean {
    val value = uri?.toString().orEmpty()
    return value.startsWith("http://", ignoreCase = true) || value.startsWith("https://", ignoreCase = true)
}

private fun isHttpRadioString(value: String?): Boolean {
    val safeValue = value.orEmpty()
    return safeValue.startsWith("http://", ignoreCase = true) || safeValue.startsWith("https://", ignoreCase = true)
}

private fun cargarAudioFocusEnabled(context: Context): Boolean {
    val prefs = context.getSharedPreferences("musickontrol_settings", Context.MODE_PRIVATE)
    return prefs.getBoolean("audio_focus_enabled", true)
}

private suspend fun extraerMetadataAudio(context: Context, uri: Uri): TrackMetadata {
    return withContext(Dispatchers.IO) {
        var retriever: MediaMetadataRetriever? = null
        try {
            retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)

            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() } ?: "Desconocido"

            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?.takeIf { it.isNotBlank() } ?: "Artista desconocido"

            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?.takeIf { it.isNotBlank() } ?: "Álbum desconocido"

            Log.d("MetadataExtract", "✅ Metadata extraída: $title - $artist ($album)")

            TrackMetadata(title, artist, album)
        } catch (e: Exception) {
            Log.e("MetadataExtract", "⚠️ Error extrayendo metadata: ${e.message}")
            TrackMetadata("Desconocido", "Artista desconocido", "Álbum desconocido")
        } finally {
            try {
                retriever?.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

private fun buildAudioMediaItem(elemento: ElementoUsb, metadata: TrackMetadata? = null): MediaItem {
    return MediaItem.Builder()
        .setUri(elemento.uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(metadata?.title ?: elemento.name)
                .setDisplayTitle(metadata?.title ?: elemento.name)
                .setArtist(metadata?.artist ?: "Desconocido")
                .setAlbumTitle(metadata?.album ?: "Desconocido")
                .build()
        )
        .build()
}

private fun buildRadioMediaItem(
    finalUri: Uri,
    radioName: String,
    radioArtist: String,
    radioAlbum: String,
    finalUrl: String
): MediaItem {
    val mediaItemBuilder = MediaItem.Builder()
        .setUri(finalUri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(radioName)
                .setDisplayTitle(radioName)
                .setArtist(radioArtist)
                .setAlbumTitle(radioAlbum)
                .build()
        )
    if (finalUrl.contains(".m3u8", ignoreCase = true)) {
        mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
    }
    return mediaItemBuilder.build()
}

private suspend fun resolverUrlRadio(rawUri: Uri): String {
    var urlString = rawUri.toString().trim()
    if (!urlString.startsWith("http://", ignoreCase = true) &&
        !urlString.startsWith("https://", ignoreCase = true)
    ) {
        urlString = "https://$urlString"
    }
    var finalUrl = urlString
    Log.d("ResolverURL", "🔍 URL inicial: $urlString")

    if (urlString.contains(".m3u", ignoreCase = true) &&
        !urlString.contains(".m3u8", ignoreCase = true)
    ) {
        Log.d("ResolverURL", "📋 Detectado .M3U")
        finalUrl = withContext(Dispatchers.IO) {
            try {
                val conn = URL(urlString).openConnection() as HttpURLConnection
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                var foundLine = urlString
                reader.forEachLine { line ->
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                        foundLine = if (!trimmed.startsWith("http://", ignoreCase = true) &&
                            !trimmed.startsWith("https://", ignoreCase = true)
                        ) "https://$trimmed" else trimmed
                        return@forEachLine
                    }
                }
                reader.close()
                conn.disconnect()
                foundLine
            } catch (e: Exception) {
                Log.e("ResolverURL", "❌ Error: ${e.message}")
                urlString
            }
        }
    }

    if (finalUrl.contains(".pls", ignoreCase = true)) {
        finalUrl = withContext(Dispatchers.IO) {
            try {
                val conn = URL(finalUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                var foundUrl = finalUrl
                reader.forEachLine { line ->
                    val trimmed = line.trim()
                    if (trimmed.startsWith("File", ignoreCase = true) && trimmed.contains("=")) {
                        val urlPart = trimmed.substringAfter("=").trim()
                        if (urlPart.startsWith("http://", ignoreCase = true) ||
                            urlPart.startsWith("https://", ignoreCase = true)
                        ) {
                            foundUrl = urlPart
                            return@forEachLine
                        }
                    }
                }
                reader.close()
                conn.disconnect()
                foundUrl
            } catch (e: Exception) {
                finalUrl
            }
        }
    }

    return finalUrl
}

private suspend fun extraerMetadataHls(urlString: String): Pair<String, String> {
    return withContext(Dispatchers.IO) {
        try {
            fun resolverUrlRelativa(baseUrl: String, relativeOrAbsolute: String): String {
                val value = relativeOrAbsolute.trim()
                if (value.startsWith("http://", ignoreCase = true) ||
                    value.startsWith("https://", ignoreCase = true)
                ) return value
                val base = URL(baseUrl)
                return URL(base, value).toString()
            }

            fun leerTextoDesdeUrl(targetUrl: String): String {
                val url = URL(targetUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36")
                connection.setRequestProperty("Accept", "application/vnd.apple.mpegurl, application/x-mpegURL, text/plain, */*")
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.connect()
                return try {
                    connection.inputStream.bufferedReader().use { it.readText() }
                } finally {
                    connection.disconnect()
                }
            }

            fun separarArtistaCancion(valor: String): Pair<String, String> {
                val limpio = valor
                    .replace(Regex("\\s+"), " ")
                    .trim()
                    .removePrefix("-")
                    .trim()

                if (limpio.isBlank()) return Pair("", "")

                val separadores = listOf(" - ", " – ", " — ", " | ")
                for (separador in separadores) {
                    val partes = limpio.split(separador, limit = 2)
                    if (partes.size == 2 && partes[0].isNotBlank() && partes[1].isNotBlank()) {
                        return Pair(partes[1].trim(), partes[0].trim())
                    }
                }

                return Pair(limpio, "")
            }

            fun parsearMetadataDesdeContenido(content: String): Pair<String, String> {
                val lines = content
                    .replace("\r", "")
                    .split("\n")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }

                var title = ""
                var artist = ""

                for (line in lines) {
                    if (line.startsWith("#EXTINF", ignoreCase = true)) {
                        val metadata = line.substringAfter(",", "").trim()
                        if (metadata.isNotEmpty() &&
                            !metadata.endsWith(".aac", ignoreCase = true) &&
                            !metadata.endsWith(".mp3", ignoreCase = true) &&
                            !metadata.endsWith(".ts", ignoreCase = true) &&
                            !metadata.contains("segment", ignoreCase = true) &&
                            !metadata.contains("chunk", ignoreCase = true)
                        ) {
                            val parsed = separarArtistaCancion(metadata)
                            title = parsed.first
                            if (parsed.second.isNotBlank()) artist = parsed.second
                        }
                    }

                    if (line.startsWith("#EXT-X-TITLE", ignoreCase = true)) {
                        val titlePart = line.substringAfter(":").trim().removeSurrounding("\"")
                        if (titlePart.isNotEmpty()) {
                            val parsed = separarArtistaCancion(titlePart)
                            title = parsed.first
                            if (parsed.second.isNotBlank()) artist = parsed.second
                        }
                    }

                    if (line.startsWith("#EXT-X-ARTIST", ignoreCase = true)) {
                        val artistPart = line.substringAfter(":").trim().removeSurrounding("\"")
                        if (artistPart.isNotEmpty()) artist = artistPart
                    }

                    if (line.contains("StreamTitle=", ignoreCase = true)) {
                        val match = Regex("StreamTitle=['\\\"]([^'\\\"]+)['\\\"]", RegexOption.IGNORE_CASE).find(line)
                        if (match != null) {
                            val parsed = separarArtistaCancion(match.groupValues[1])
                            title = parsed.first
                            if (parsed.second.isNotBlank()) artist = parsed.second
                        }
                    }
                }

                return Pair(title, artist)
            }

            val masterContent = leerTextoDesdeUrl(urlString)
            var result = parsearMetadataDesdeContenido(masterContent)
            if (result.first.isNotBlank() && result.second.isNotBlank()) return@withContext result

            val masterLines = masterContent
                .replace("\r", "")
                .split("\n")
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            val childPlaylists = masterLines.filter { line ->
                !line.startsWith("#") && line.contains(".m3u8", ignoreCase = true)
            }

            for (childPlaylistLine in childPlaylists) {
                try {
                    val childUrl = resolverUrlRelativa(urlString, childPlaylistLine)
                    val childContent = leerTextoDesdeUrl(childUrl)
                    result = parsearMetadataDesdeContenido(childContent)
                    if (result.first.isNotBlank() && result.second.isNotBlank()) {
                        return@withContext result
                    }
                } catch (e: Exception) {
                    Log.d("RadioMetadata", "⚠️ No se pudo leer playlist hija: ${e.message}")
                }
            }

            // Rock FM publica actualmente la canción que está sonando en su playlist pública.
            // El stream HLS puede no incluir artista/canción como texto dentro del .m3u8.
            if (urlString.contains("rockfm-cope.flumotion.com", ignoreCase = true)) {
                val rockFmMetadata = extraerMetadataRockFmOnline()
                if (rockFmMetadata.first.isNotBlank()) {
                    return@withContext rockFmMetadata
                }
            }

            result
        } catch (e: Exception) {
            Log.e("RadioMetadata", "❌ Error HLS: ${e.message}")
            Pair("", "")
        }
    }
}

private fun extraerMetadataRockFmOnline(): Pair<String, String> {
    return try {
        val url = URL("https://onlineradiobox.com/es/rockfm/playlist/?lang=es")
        val connection = url.openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36")
        connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        connection.connect()

        val html = try {
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }

        val text = html
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</(div|p|li|tr|td|span|a|h[1-6])>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]+>"), "")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")

        val lines = text
            .replace("\r", "")
            .split("\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val trackRegex = Regex("^\\d{1,2}:\\d{2}\\s*\\|\\s*(.+)$")
        val trackLine = lines.firstOrNull { trackRegex.matches(it) } ?: return Pair("", "")
        val track = trackRegex.find(trackLine)?.groupValues?.getOrNull(1)?.trim().orEmpty()

        if (track.isBlank()) return Pair("", "")

        val parts = track.split(" - ", limit = 2)
        if (parts.size == 2) {
            Pair(parts[1].trim(), parts[0].trim())
        } else {
            Pair(track, "")
        }
    } catch (e: Exception) {
        Log.d("RadioMetadata", "⚠️ Rock FM metadata online no disponible: ${e.message}")
        Pair("", "")
    }
}

private suspend fun extraerMetadataIcy(urlString: String): Pair<String, String> {
    return withContext(Dispatchers.IO) {
        try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36")
            connection.setRequestProperty("Icy-MetaData", "1")
            connection.setRequestProperty("Connection", "close")
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.connect()

            val icyName = connection.getHeaderField("icy-name") ?: ""
            val icyGenre = connection.getHeaderField("icy-genre") ?: ""
            val icyMetaInt = connection.getHeaderField("icy-metaint")?.toIntOrNull() ?: 0

            if (icyMetaInt > 0) {
                val inputStream = connection.inputStream.buffered()
                try {
                    // ICY: primero llegan exactamente icy-metaint bytes de audio.
                    // Después llega 1 byte con el tamaño de la metadata en bloques de 16 bytes.
                    var audioRemaining = icyMetaInt
                    val audioBuffer = ByteArray(8192)
                    while (audioRemaining > 0) {
                        val read = inputStream.read(audioBuffer, 0, minOf(audioBuffer.size, audioRemaining))
                        if (read < 0) break
                        audioRemaining -= read
                    }

                    val metadataLengthByte = inputStream.read()
                    if (metadataLengthByte >= 0) {
                        val metadataLength = metadataLengthByte * 16
                        if (metadataLength > 0) {
                            val metadataBuffer = ByteArray(metadataLength)
                            var totalRead = 0
                            while (totalRead < metadataLength) {
                                val read = inputStream.read(metadataBuffer, totalRead, metadataLength - totalRead)
                                if (read < 0) break
                                totalRead += read
                            }

                            val metadataStr = String(metadataBuffer, 0, totalRead, Charsets.ISO_8859_1).trim('\u0000', ' ', '\n', '\r')
                            val titleMatch = Regex("StreamTitle=['\\\"]([^'\\\"]*)['\\\"]", RegexOption.IGNORE_CASE).find(metadataStr)
                            val streamTitle = titleMatch?.groupValues?.getOrNull(1).orEmpty().trim()

                            if (streamTitle.isNotBlank()) {
                                val parts = streamTitle.split(" - ", limit = 2)
                                return@withContext if (parts.size == 2) {
                                    Pair(parts[1].trim(), parts[0].trim())
                                } else {
                                    Pair(streamTitle, "")
                                }
                            }
                        }
                    }
                } finally {
                    inputStream.close()
                }
            }

            connection.disconnect()
            Pair(icyName, "")
        } catch (e: Exception) {
            Log.e("IcyMetadata", "❌ Error: ${e.message}")
            Pair("", "")
        }
    }
}

private fun limpiarNombreCarpeta(rutaCompleta: String): String {
    if (rutaCompleta.isEmpty()) return "Carpeta"
    val nombreLimpio = rutaCompleta.substringAfterLast("/")
    if (nombreLimpio.isEmpty()) return rutaCompleta
    return nombreLimpio
        .replace(Regex("[0-9]{10,}"), "")
        .replace(Regex("[a-f0-9]{8,}"), "")
        .trim()
        .ifEmpty { nombreLimpio }
}
@Composable
fun MainLayout(
    exportLauncher: ActivityResultLauncher<Uri?>? = null,
    importLauncher: ActivityResultLauncher<Array<String>>? = null,
    backupExportLauncher: ActivityResultLauncher<String>? = null,
    backupImportLauncher: ActivityResultLauncher<Array<String>>? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val fontSizeScale = LocalFontSizeScale.current.value

    val exoPlayer = remember {
        ExoPlayerManager.getInstance(context)
    }

    var currentScreen by remember { mutableStateOf("DIRECTORIO") }
    var rootUri by remember { mutableStateOf<Uri?>(null) }

    val navigationHistory = remember { mutableStateListOf<DocumentFile>() }
    val currentElements = remember { mutableStateListOf<ElementoUsb>() }

    var selectedAudioUri by remember { mutableStateOf<Uri?>(null) }
    var selectedAudioName by remember { mutableStateOf("Sin pista") }
    var metadataReloadKey by remember { mutableStateOf(0) }

    val currentPlaylist = remember { mutableStateOf<List<ElementoUsb>>(emptyList()) }
    var currentPlaylistIndex by remember { mutableStateOf(0) }
    var currentMediaItemIndex by remember { mutableStateOf(0) }
    var isPlaying by remember { mutableStateOf(false) }

    val musicDatabase = remember {
        com.dnc1981.musickontrol.database.MusicDatabaseHelper(context)
    }

    val eqAudioController = remember { EqAudioController(context) }

    var currentFolderPath by remember { mutableStateOf("") }
    var currentFolderName by remember { mutableStateOf("") }

    val radioStations = remember {
        mutableStateListOf<RadioStation>().apply {
            addAll(cargarRadioStationsGuardadas(context))
        }
    }

    var isCarMoving by remember { mutableStateOf(false) }
    var currentSpeed by remember { mutableStateOf(0f) }

    var isSwitchingFolder by remember { mutableStateOf(false) }

    var originalPlaylist by remember { mutableStateOf<List<ElementoUsb>>(emptyList()) }
    var isShuffleActive by remember { mutableStateOf(false) }
    var savedFolderPathBeforeShuffle by remember { mutableStateOf("") }
    var savedFolderNameBeforeShuffle by remember { mutableStateOf("") }

    var radioRetryCount by remember { mutableStateOf(0) }
    var lastRadioUri by remember { mutableStateOf<Uri?>(null) }
    var lastRadioName by remember { mutableStateOf("") }

    var hasAutoPlayedFirstRadio by remember { mutableStateOf(false) }

    val adaptiveVolumeManager = remember {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        AdaptiveVolumeManager(context, audioManager)
    }

    val autoplayManager = remember { AutoplayManager(context) }

    val nightModeManager = remember {
        NightModeManager(context)
    }

    val isNightMode by nightModeManager.isNightMode.collectAsState()

    var wasMoving by remember { mutableStateOf(false) }
    var hasAutoplayedOnStart by remember { mutableStateOf(false) }

    val httpDataSourceFactory = remember {
        DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(2000)
            .setReadTimeoutMs(2000)
            .setUserAgent("Mozilla/5.0 (Linux; Android 10) Mobile")
    }

    val dataSourceFactory = remember { DefaultDataSource.Factory(context, httpDataSourceFactory) }
    val mediaSourceFactory = remember { DefaultMediaSourceFactory(context).setDataSourceFactory(dataSourceFactory) }
    val loadControl = remember {
        DefaultLoadControl.Builder()
            .setBufferDurationsMs(500, 5000, 250, 500)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
    }
    val audioAttributes = remember {
        AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()
    }
    val gaplessManager = remember { GaplessPlaybackManager(context) }
    val audioNormalizer = remember { AudioNormalizerProcessor(context) }

    val colors = if (isNightMode) {
        nightModeManager.getNightColors()
    } else {
        nightModeManager.getDayColors()
    }

    // ✅ FUNCIÓN: LIMPIAR ESTADO USB
    fun limpiarEstadoUsb() {
        AhoraSuenaMetadataCache.clearForced()

        selectedAudioUri = null
        selectedAudioName = "Sin pista"
        currentPlaylist.value = emptyList()
        currentPlaylistIndex = 0
        currentFolderPath = ""
        currentFolderName = ""
        metadataReloadKey++
        isShuffleActive = false
        originalPlaylist = emptyList()
    }

    val audioFocusManager = remember { AudioFocusManager(context, exoPlayer) }
    // ✅ FUNCIÓN: REPRODUCIR PLAYLIST EN EXOPLAYER
    fun reproducirPlaylistEnExoPlayer(
        playlist: List<ElementoUsb>,
        startIndex: Int,
        cargarMetadata: Boolean = true
    ) {
        try {
            Log.d("ReproducirPlaylist", "🎵 Iniciando: ${playlist.size} items, índice $startIndex")

            coroutineScope.launch(Dispatchers.Main) {
                try {
                    if (playlist.isEmpty()) {
                        Toast.makeText(context, "Playlist vacía", Toast.LENGTH_SHORT).show()
                        return@launch
                    }

                    val safeIndex = startIndex.coerceIn(0, playlist.lastIndex)

                    exoPlayer.stop()
                    delay(50L)
                    exoPlayer.clearMediaItems()
                    delay(50L)

                    // ✅ PARA EL SHUFFLE GLOBAL NO ESPERAMOS A EXTRAER METADATA DE TODAS LAS CANCIONES
                    val itemsWithMetadata = withContext(Dispatchers.IO) {
                        if (!cargarMetadata) {
                            playlist.map { elemento ->
                                buildAudioMediaItem(elemento, null)
                            }
                        } else {
                            playlist.map { elemento ->
                                try {
                                    val metadata = extraerMetadataAudio(context, elemento.uri)
                                    buildAudioMediaItem(elemento, metadata)
                                } catch (e: Exception) {
                                    buildAudioMediaItem(elemento, null)
                                }
                            }
                        }
                    }

                    // ✅ ESTABLECER TODOS LOS ITEMS (SIN SUBLIST)
                    exoPlayer.setMediaItems(itemsWithMetadata, safeIndex, 0L)
                    delay(50L)

                    exoPlayer.prepare()
                    delay(100L)

                    // ✅ REPRODUCIR
                    exoPlayer.playWhenReady = true
                    exoPlayer.play()

                    eqAudioController.attachToPlayer(exoPlayer)
                    audioNormalizer.aplicarNormalizacion(exoPlayer)

                    Log.d("ReproducirPlaylist", "✅ REPRODUCIENDO - Índice: $safeIndex (${itemsWithMetadata.size}/${playlist.size})")

                } catch (e: Exception) {
                    e.printStackTrace()
                    Log.e("ReproducirPlaylist", "❌ Error: ${e.message}")
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("ReproducirPlaylist", "❌ Error general: ${e.message}")
        }
    }

    // ✅ FUNCIÓN: REPRODUCIR RADIO DESDE URI
    fun reproducirRadioDesdeUri(uri: Uri, name: String) {
        coroutineScope.launch {
            try {
                Log.d("RadioAutoplay", "🎙️ Iniciando radio: $name - URI: $uri")

                // Resolver únicamente playlists de red que necesiten resolución.
                // No descargamos el M3U8 completo ni esperamos metadata antes de
                // arrancar el audio: en AAOS eso puede bloquear o provocar que el
                // sistema cierre la Activity antes de que ExoPlayer empiece a sonar.
                val finalUrl = resolverUrlRadio(uri)
                val finalUri = Uri.parse(finalUrl)
                val isHls = finalUrl.contains(".m3u8", ignoreCase = true)

                AhoraSuenaMetadataCache.clearForced()
                isSwitchingFolder = false

                selectedAudioUri = finalUri
                selectedAudioName = name.ifBlank { "Radio Online" }
                currentPlaylist.value = emptyList()
                currentPlaylistIndex = 0
                currentFolderPath = ""
                currentFolderName = ""
                metadataReloadKey++
                isShuffleActive = false
                originalPlaylist = emptyList()
                currentScreen = "AHORA SUENA"

                val initialTitle = name.ifBlank { "Radio Online" }
                val initialArtist = if (isHls) "Radio HLS" else "Streaming"
                val initialAlbum = if (isHls) "Streaming HLS" else "Radio Online"

                val mediaItem = buildRadioMediaItem(
                    finalUri = finalUri,
                    radioName = initialTitle,
                    radioArtist = initialArtist,
                    radioAlbum = initialAlbum,
                    finalUrl = finalUrl
                )

                exoPlayer.stop()
                exoPlayer.clearMediaItems()
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
                exoPlayer.play()

                try {
                    eqAudioController.attachToPlayer(exoPlayer)
                } catch (e: Exception) {
                    Log.e("RadioAutoplay", "⚠️ EQ no disponible para radio: ${e.message}", e)
                }
                try {
                    audioNormalizer.aplicarNormalizacion(exoPlayer)
                } catch (e: Exception) {
                    Log.e("RadioAutoplay", "⚠️ Normalización no disponible para radio: ${e.message}", e)
                }

                selectedAudioName = initialTitle
                metadataReloadKey++
                autoplayManager.saveCurrentAudio(finalUri, initialTitle, "RADIO")

                Log.d("RadioAutoplay", "✅ Radio arrancada inmediatamente: $initialTitle - $finalUrl")

                // Metadata en segundo plano: no bloquea la reproducción.
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val (metadataTitle, metadataArtist) = if (isHls) {
                            extraerMetadataHls(finalUrl)
                        } else {
                            extraerMetadataIcy(finalUrl)
                        }

                        if (metadataTitle.isNotBlank()) {
                            withContext(Dispatchers.Main) {
                                selectedAudioName = metadataTitle
                                metadataReloadKey++
                                AhoraSuenaMetadataCache.put(
                                    finalUri.toString(),
                                    metadataReloadKey,
                                    CachedTrackMetadata(
                                        title = metadataTitle,
                                        artist = metadataArtist,
                                        album = if (isHls) "Streaming HLS" else "Radio Online",
                                        artwork = null
                                    )
                                )
                                Log.d("RadioMetadata", "✅ Metadata actualizada: $metadataTitle - $metadataArtist")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("RadioMetadata", "⚠️ Error obteniendo metadata en segundo plano: ${e.message}", e)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e("RadioAutoplay", "❌ Error: ${e.message}", e)
                Toast.makeText(context, "Error al preparar la radio: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ✅ FUNCIÓN: REPRODUCIR LISTA USB
    fun reproducirListaUsb(audioList: List<ElementoUsb>, clickedIndex: Int) {
        if (audioList.isEmpty()) return
        if (clickedIndex < 0 || clickedIndex >= audioList.size) return

        coroutineScope.launch(Dispatchers.Main) {
            try {
                val selectedItem = audioList[clickedIndex]
                val uriScheme = selectedItem.uri.scheme

                if (uriScheme == null || (uriScheme != "file" && uriScheme != "content")) {
                    Toast.makeText(context, "URI inválido: USB desconectado", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                Log.d("ReproducirListaUsb", "🎵 Reproduciendo: ${selectedItem.name} (índice: $clickedIndex)")

                currentScreen = "AHORA SUENA"
                AhoraSuenaMetadataCache.clearForced()
                isSwitchingFolder = false

                selectedAudioUri = selectedItem.uri
                selectedAudioName = selectedItem.name
                currentPlaylist.value = audioList
                currentPlaylistIndex = clickedIndex
                metadataReloadKey++
                isShuffleActive = false
                originalPlaylist = emptyList()

                reproducirPlaylistEnExoPlayer(playlist = audioList, startIndex = clickedIndex)

                Log.d("ReproducirListaUsb", "✅ Reproduciendo: ${selectedItem.name}")

                launch(Dispatchers.IO) {
                    try {
                        autoplayManager.saveCurrentAudio(selectedItem.uri, selectedItem.name, "USB")
                    } catch (e: Exception) {
                        Log.e("ReproducirListaUsb", "Error guardando: ${e.message}")
                    }
                }

                launch(Dispatchers.IO) {
                    try {
                        val folderPath = musicDatabase.getFolderPathBySongUri(selectedItem.uri.toString())
                        if (folderPath != null && folderPath.isNotEmpty()) {
                            currentFolderPath = folderPath
                            currentFolderName = limpiarNombreCarpeta(folderPath)

                            Log.d("ReproducirListaUsb", "✅ Carpeta indexada: $folderPath (Nombre: $currentFolderName)")
                        } else {
                            currentFolderPath = selectedItem.name.substringBeforeLast(".")
                            currentFolderName = selectedItem.name.substringBeforeLast(".")
                            Log.d("ReproducirListaUsb", "⚠️ Carpeta no indexada, usando: $currentFolderPath")
                        }
                    } catch (e: Exception) {
                        Log.e("ReproducirListaUsb", "❌ Error indexando: ${e.message}")
                        currentFolderPath = selectedItem.name.substringBeforeLast(".")
                        currentFolderName = selectedItem.name.substringBeforeLast(".")
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    // ✅ FUNCIÓN: CAMBIAR A CARPETA SIGUIENTE AUTOMÁTICO
    fun cambiarACarpetaSiguienteAutomatico() {
        Log.d("CambiarCarpetaAuto", "🔄 Intentando cambiar de carpeta automáticamente...")

        coroutineScope.launch(Dispatchers.Main) {
            try {
                if (currentPlaylist.value.isEmpty()) {
                    Log.d("CambiarCarpetaAuto", "⚠️ Playlist vacía")
                    limpiarEstadoUsb()
                    return@launch
                }

                if (currentFolderPath.isEmpty()) {
                    Log.d("CambiarCarpetaAuto", "⚠️ No hay carpeta actual")
                    return@launch
                }

                Log.d("CambiarCarpetaAuto", "📂 Carpeta actual: $currentFolderPath")

                val nextFolder = withContext(Dispatchers.IO) {
                    try {
                        val result = musicDatabase.getNextDiskFolder(currentFolderPath)
                        Log.d("CambiarCarpetaAuto", "🔍 BD retornó: $result")
                        result
                    } catch (e: Exception) {
                        Log.e("CambiarCarpetaAuto", "❌ Error BD: ${e.message}")
                        null
                    }
                }

                if (nextFolder != null && nextFolder.isNotEmpty()) {
                    Log.d("CambiarCarpetaAuto", "✅ Siguiente carpeta encontrada: $nextFolder")

                    val nextSongs = withContext(Dispatchers.IO) {
                        try {
                            val songs = musicDatabase.getSongs(nextFolder)
                            Log.d("CambiarCarpetaAuto", "🎵 Canciones en $nextFolder: ${songs.size}")
                            songs
                        } catch (e: Exception) {
                            Log.e("CambiarCarpetaAuto", "❌ Error cargando canciones: ${e.message}")
                            emptyList()
                        }
                    }

                    if (nextSongs.isNotEmpty()) {
                        val nextPlaylist = nextSongs.mapIndexed { index, uri ->
                            ElementoUsb(
                                name = "Canción ${index + 1}",
                                uri = Uri.parse(uri),
                                isDirectory = false
                            )
                        }

                        AhoraSuenaMetadataCache.clearForced()
                        currentPlaylist.value = nextPlaylist
                        currentPlaylistIndex = 0
                        selectedAudioUri = nextPlaylist[0].uri
                        selectedAudioName = nextPlaylist[0].name
                        currentFolderPath = nextFolder
                        currentFolderName = limpiarNombreCarpeta(nextFolder)
                        metadataReloadKey++
                        isShuffleActive = false
                        originalPlaylist = emptyList()
                        currentScreen = "AHORA SUENA"

                        Log.d("CambiarCarpetaAuto", "▶️ Reproduciendo: $nextFolder")
                        reproducirPlaylistEnExoPlayer(nextPlaylist, 0)

                    } else {
                        Log.d("CambiarCarpetaAuto", "⚠️ Carpeta vacía, reintentando...")
                        cambiarACarpetaSiguienteAutomatico()
                    }
                } else {
                    Log.d("CambiarCarpetaAuto", "⚠️ No hay más carpetas")
                    limpiarEstadoUsb()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                Log.e("CambiarCarpetaAuto", "❌ Error: ${e.message}")
            }
        }
    }

    // ✅ FUNCIÓN: CAMBIAR CARPETA RÁPIDO (MANUAL)
    fun cambiarCarpetaRapido(direccion: String) {
        if (isSwitchingFolder) {
            Log.d("CambiarCarpeta", "⏳ Ya se está cambiando de carpeta")
            return
        }

        coroutineScope.launch(Dispatchers.Main) {
            try {
                if (currentPlaylist.value.isEmpty()) {
                    Toast.makeText(context, "Sin playlist activa", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                if (currentFolderPath.isEmpty()) {
                    Toast.makeText(context, "Carpeta no identificada", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                if (currentFolderPath == "SHUFFLE_GLOBAL" || currentFolderPath == "FAVORITOS") {
                    Toast.makeText(
                        context,
                        "❌ Desactiva ${if (currentFolderPath == "SHUFFLE_GLOBAL") "shuffle" else "favoritos"} para cambiar de carpeta",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                isSwitchingFolder = true

                Log.d("CambiarCarpeta", "🔄 Buscando carpeta $direccion desde: $currentFolderPath")

                val nextFolder = withContext(Dispatchers.IO) {
                    try {
                        val folder = if (direccion == "NEXT") {
                            musicDatabase.getNextDiskFolder(currentFolderPath)
                        } else {
                            musicDatabase.getPreviousDiskFolder(currentFolderPath)
                        }

                        Log.d("CambiarCarpeta", "🔍 BD retornó carpeta: $folder")
                        folder
                    } catch (e: Exception) {
                        Log.e("CambiarCarpeta", "❌ Error BD: ${e.message}", e)
                        null
                    }
                }

                if (nextFolder != null && nextFolder.isNotEmpty()) {
                    val nextSongs = withContext(Dispatchers.IO) {
                        try {
                            val songs = musicDatabase.getSongs(nextFolder)
                            Log.d("CambiarCarpeta", "🎵 Carpeta $nextFolder tiene ${songs.size} canciones")
                            songs
                        } catch (e: Exception) {
                            Log.e("CambiarCarpeta", "⚠️ Error cargando canciones: ${e.message}")
                            emptyList()
                        }
                    }

                    if (nextSongs.isNotEmpty()) {
                        val nextPlaylist = nextSongs.mapIndexed { index, uri ->
                            ElementoUsb(
                                name = "Canción ${index + 1}",
                                uri = Uri.parse(uri),
                                isDirectory = false
                            )
                        }

                        AhoraSuenaMetadataCache.clearForced()
                        currentPlaylist.value = nextPlaylist
                        currentPlaylistIndex = 0
                        selectedAudioUri = nextPlaylist[0].uri
                        selectedAudioName = nextPlaylist[0].name

                        currentFolderPath = nextFolder
                        currentFolderName = limpiarNombreCarpeta(nextFolder)

                        metadataReloadKey++
                        isShuffleActive = false
                        originalPlaylist = emptyList()

                        reproducirPlaylistEnExoPlayer(playlist = nextPlaylist, startIndex = 0)

                        delay(500L)
                        isSwitchingFolder = false

                        Log.d("CambiarCarpeta", "✅ Cambio completado: $nextFolder")

                    } else {
                        isSwitchingFolder = false
                        Log.d("CambiarCarpeta", "⚠️ Carpeta vacía, reintentando...")
                        cambiarCarpetaRapido(direccion)
                    }
                } else {
                    isSwitchingFolder = false

                    val mensaje = if (direccion == "NEXT") {
                        "Ya estás en la última carpeta"
                    } else {
                        "Ya estás en la primera carpeta"
                    }

                    Toast.makeText(context, mensaje, Toast.LENGTH_SHORT).show()
                    Log.d("CambiarCarpeta", "⚠️ $mensaje")
                }

            } catch (e: Exception) {
                e.printStackTrace()
                isSwitchingFolder = false
                Toast.makeText(context, "Error al navegar: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("CambiarCarpeta", "❌ EXCEPCIÓN: ${e.message}", e)
            }
        }
    }
    // ✅ LAUNCHEDEFFECT: AUTOPLAY AL INICIAR
    LaunchedEffect(Unit) {
        nightModeManager.startListening()
    }

    LaunchedEffect(radioStations) {
        if (!hasAutoPlayedFirstRadio && radioStations.isNotEmpty() && selectedAudioUri == null) {
            hasAutoPlayedFirstRadio = true
            val firstStation = radioStations.firstOrNull()
            if (firstStation != null) {
                Log.d("AutoPlay", "🎙️ Auto-reproduciendo primera radio: ${firstStation.name}")
                reproducirRadioDesdeUri(Uri.parse(firstStation.url), firstStation.name)
            }
        }
    }

    LaunchedEffect(Unit) {
        Log.d("AppStartup", "🚀 App iniciada")

        if (autoplayManager.isTestMode()) {
            Log.d("AppStartup", "🧪 TEST MODE DETECTADO")

            delay(500)

            if (autoplayManager.isEnabled()) {
                val lastAudio = autoplayManager.getLastAudio()
                if (lastAudio != null) {
                    Log.d("AppStartup", "📢 Iniciando autoplay por TEST MODE")

                    Toast.makeText(
                        context,
                        "🚗 AUTOPLAY INICIADO (TEST)\n${lastAudio.name}",
                        Toast.LENGTH_LONG
                    ).show()

                    when (lastAudio.type) {
                        "RADIO" -> {
                            reproducirRadioDesdeUri(lastAudio.uri, lastAudio.name)
                        }
                        "USB" -> {
                            val audioList = listOf(ElementoUsb(name = lastAudio.name, uri = lastAudio.uri, isDirectory = false))
                            reproducirListaUsb(audioList, 0)
                        }
                    }

                    autoplayManager.setTestMode(false)
                }
            }
        }
    }

    // ✅ ACTUALIZACIÓN AUTOMÁTICA DE METADATA DE RADIO
    // No modifica la reproducción. Solo consulta periódicamente artista/canción
    // y actualiza la metadata del MediaItem que ya está sonando.
    LaunchedEffect(selectedAudioUri) {
        val radioUri = selectedAudioUri
        if (radioUri != null && isHttpRadioUri(radioUri)) {
            while (true) {
                try {
                    val radioUrl = radioUri.toString()
                    val metadata = withContext(Dispatchers.IO) {
                        if (radioUrl.contains(".m3u8", ignoreCase = true)) {
                            extraerMetadataHls(radioUrl)
                        } else {
                            extraerMetadataIcy(radioUrl)
                        }
                    }

                    val title = metadata.first.trim()
                    val artist = metadata.second.trim()

                    if (title.isNotBlank()) {
                        val currentIndex = exoPlayer.currentMediaItemIndex
                        val currentItem = exoPlayer.currentMediaItem

                        if (currentIndex >= 0 && currentItem != null) {
                            val currentTitle = currentItem.mediaMetadata.title?.toString().orEmpty()
                            val currentArtist = currentItem.mediaMetadata.artist?.toString().orEmpty()

                            if (currentTitle != title || currentArtist != artist) {
                                val updatedItem = buildRadioMediaItem(
                                    finalUri = radioUri,
                                    radioName = title,
                                    radioArtist = artist.ifBlank { currentArtist.ifBlank { "Radio HLS" } },
                                    radioAlbum = currentItem.mediaMetadata.albumTitle?.toString()
                                        ?: "Streaming HLS",
                                    finalUrl = radioUrl
                                )

                                exoPlayer.replaceMediaItem(currentIndex, updatedItem)
                                metadataReloadKey++
                                Log.d("RadioMetadata", "🔄 Metadata actualizada: $artist - $title")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.d("RadioMetadata", "⚠️ Error actualizando metadata: ${e.message}")
                }

                delay(15000L)
            }
        }
    }

    // ✅ DISPOSABLE EFFECT: PLAYER LISTENER
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                super.onMediaItemTransition(mediaItem, reason)
                currentMediaItemIndex = exoPlayer.currentMediaItemIndex

                metadataReloadKey++

                val metadata = mediaItem?.mediaMetadata
                if (metadata != null) {
                    Log.d("MediaMetadata", "📊 Title: ${metadata.title}")
                    Log.d("MediaMetadata", "📊 Artist: ${metadata.artist}")
                    Log.d("MediaMetadata", "📊 Album: ${metadata.albumTitle}")
                }

                Log.d("MainLayout", "🎵 Canción cambió: ${mediaItem?.mediaMetadata?.title} (índice: $currentMediaItemIndex)")

                if (currentMediaItemIndex >= 0 && currentMediaItemIndex < currentPlaylist.value.size) {
                    val currentItem = currentPlaylist.value[currentMediaItemIndex]
                    selectedAudioUri = currentItem.uri
                    selectedAudioName = currentItem.name
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                super.onPlaybackStateChanged(playbackState)
                isPlaying = playbackState == Player.STATE_READY && exoPlayer.isPlaying

                if (playbackState == Player.STATE_READY) {
                    Log.d("PlaybackState", "✅ READY - Reproduciendo")
                }

                if (playbackState == Player.STATE_ENDED) {
                    Log.d("PlaybackEnded", "⏹️ Reproducción terminada")

                    val isRadio = isHttpRadioUri(selectedAudioUri)

                    if (!isRadio && currentPlaylist.value.isNotEmpty() && currentFolderPath.isNotEmpty()) {
                        Log.d("PlaybackEnded", "🎵 Última canción de USB - Intentando cambiar carpeta")
                        cambiarACarpetaSiguienteAutomatico()
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                error.printStackTrace()
                val isRadio = isHttpRadioUri(selectedAudioUri)

                if (!isRadio) {
                    exoPlayer.stop()
                    exoPlayer.clearMediaItems()
                    isSwitchingFolder = false
                    limpiarEstadoUsb()
                    Toast.makeText(context, "❌ Error al reproducir: ${error.errorCodeName}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "⚠️ Error servidor: ${error.localizedMessage}", Toast.LENGTH_LONG).show()

                    lastRadioUri = selectedAudioUri
                    lastRadioName = selectedAudioName

                    coroutineScope.launch {
                        val delayMs = when (radioRetryCount) {
                            0 -> 2000L
                            1 -> 4000L
                            else -> 8000L
                        }

                        delay(delayMs)
                        radioRetryCount++

                        if (radioRetryCount <= 3 && lastRadioUri != null) {
                            Log.d("RadioReconnect", "🔄 Reintentando conexión radio... ($radioRetryCount/3)")

                            try {
                                val retryUri = lastRadioUri!!
                                val retryName = lastRadioName.ifBlank { "Radio Online" }
                                val retryMediaItem = buildRadioMediaItem(
                                    retryUri,
                                    retryName,
                                    "Streaming",
                                    "Radio Online",
                                    retryUri.toString()
                                )

                                exoPlayer.stop()
                                exoPlayer.clearMediaItems()
                                exoPlayer.setMediaItem(retryMediaItem)
                                exoPlayer.prepare()

                                delay(150L)

                                exoPlayer.playWhenReady = true
                                exoPlayer.play()

                                eqAudioController.attachToPlayer(exoPlayer)
                                audioNormalizer.aplicarNormalizacion(exoPlayer)

                                selectedAudioUri = retryUri
                                selectedAudioName = retryName
                                currentPlaylist.value = emptyList()
                                currentPlaylistIndex = 0
                                currentFolderPath = ""
                                currentFolderName = ""
                                isShuffleActive = false
                                originalPlaylist = emptyList()
                                currentScreen = "AHORA SUENA"
                                metadataReloadKey++

                                Log.d("RadioReconnect", "✅ Reconexión exitosa")
                            } catch (e: Exception) {
                                e.printStackTrace()
                                Log.d("RadioReconnect", "❌ Error en reconexión: ${e.message}")
                            }
                        } else {
                            radioRetryCount = 0
                            Toast.makeText(context, "❌ No se pudo conectar a la radio tras 3 intentos", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    // ✅ DISPOSABLE EFFECT: USB BROADCAST RECEIVER
    DisposableEffect(Unit) {
        val usbReceiver = object : BroadcastReceiver() {
            override fun onReceive(cntx: Context?, intent: Intent?) {
                val action = intent?.action

                if (action == Intent.ACTION_MEDIA_UNMOUNTED ||
                    action == Intent.ACTION_MEDIA_REMOVED ||
                    action == Intent.ACTION_MEDIA_EJECT ||
                    action == UsbManager.ACTION_USB_DEVICE_DETACHED
                ) {
                    val isRadio = isHttpRadioUri(selectedAudioUri)

                    if (!isRadio) {
                        try {
                            exoPlayer.stop()
                            exoPlayer.clearMediaItems()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }

                        try {
                            eqAudioController.release()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }

                        rootUri = null
                        navigationHistory.clear()
                        currentElements.clear()

                        limpiarEstadoUsb()
                        currentScreen = "DIRECTORIO"

                        Toast.makeText(cntx ?: context, "Unidad USB desconectada", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(cntx ?: context, "USB desconectado (Radio en reproducción)", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        val filterMedia = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_REMOVED)
            addAction(Intent.ACTION_MEDIA_EJECT)
            addDataScheme("file")
        }

        val filterUsb = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }

        try {
            context.registerReceiver(usbReceiver, filterMedia)
            context.registerReceiver(usbReceiver, filterUsb)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        onDispose {
            try {
                context.unregisterReceiver(usbReceiver)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // ✅ DISPOSABLE EFFECT: LOCATION LISTENER
    DisposableEffect(Unit) {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

        val locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                isCarMoving = location.hasSpeed() && location.speed > 1.0f
                currentSpeed = location.speed * 3.6f

                adaptiveVolumeManager.applyAdaptiveVolume(currentSpeed)

                if (isCarMoving && !wasMoving && !hasAutoplayedOnStart && autoplayManager.isEnabled()) {
                    Log.d("Autoplay", "🚗 ¡Vehículo en movimiento! Iniciando autoplay...")

                    val lastAudio = autoplayManager.getLastAudio()
                    if (lastAudio != null) {
                        Log.d("Autoplay", "✅ Reproduciendo: ${lastAudio.name} (${lastAudio.type})")

                        when (lastAudio.type) {
                            "RADIO" -> {
                                reproducirRadioDesdeUri(lastAudio.uri, lastAudio.name)
                                hasAutoplayedOnStart = true
                                Toast.makeText(context, "🎙️ Autoplay: ${lastAudio.name}", Toast.LENGTH_SHORT).show()
                            }
                            "USB" -> {
                                val audioList = listOf(ElementoUsb(name = lastAudio.name, uri = lastAudio.uri, isDirectory = false))
                                reproducirListaUsb(audioList, 0)
                                hasAutoplayedOnStart = true
                                Toast.makeText(context, "🎵 Autoplay: ${lastAudio.name}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        Log.d("Autoplay", "⚠️ No hay audio guardado para autoplay")
                    }
                }

                if (isCarMoving && !wasMoving) {
                    wasMoving = true
                } else if (!isCarMoving && wasMoving) {
                    wasMoving = false
                    hasAutoplayedOnStart = false
                }
            }

            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {
                isCarMoving = false
            }
            override fun onStatusChanged(provider: String, status: Int, extras: Bundle?) {}
        }

        try {
            val fineGranted = ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            val coarseGranted = ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (fineGranted || coarseGranted) {
                locationManager?.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    2000L,
                    1f,
                    locationListener
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        onDispose {
            try {
                locationManager?.removeUpdates(locationListener)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // ✅ DISPOSABLE EFFECT: EQ Y AUDIO FOCUS
    DisposableEffect(exoPlayer) {
        eqAudioController.attachToPlayer(exoPlayer)

        if (cargarAudioFocusEnabled(context)) {
            audioFocusManager.requestAudioFocus()
        }

        val listener = object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                eqAudioController.attachToAudioSession(audioSessionId)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    try {
                        eqAudioController.attachToPlayer(exoPlayer)
                    } catch (e: Exception) {
                        Log.e("AudioController", "⚠️ Error enlazando EQ: ${e.message}", e)
                    }
                    try {
                        audioNormalizer.aplicarNormalizacion(exoPlayer)
                    } catch (e: Exception) {
                        Log.e("AudioController", "⚠️ Error aplicando normalización: ${e.message}", e)
                    }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                try {
                    eqAudioController.attachToPlayer(exoPlayer)
                } catch (e: Exception) {
                    Log.e("AudioController", "⚠️ Error enlazando EQ en transición: ${e.message}", e)
                }
                try {
                    audioNormalizer.aplicarNormalizacion(exoPlayer)
                } catch (e: Exception) {
                    Log.e("AudioController", "⚠️ Error normalizando en transición: ${e.message}", e)
                }
            }
        }

        exoPlayer.addListener(listener)

        onDispose {
            try {
                exoPlayer.removeListener(listener)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            try {
                eqAudioController.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            audioFocusManager.release()
            adaptiveVolumeManager.cleanup()
        }
    }

    LaunchedEffect(currentFolderPath) {
        if (currentFolderPath.isNotBlank() &&
            currentFolderPath != "SHUFFLE_GLOBAL" &&
            currentFolderPath != "FAVORITOS"
        ) {
            Log.d("Prefetch", "🔍 Precargando carpetas cercanas a: $currentFolderPath")
        }
    }

    val animatedBgColor by animateColorAsState(
        targetValue = colors.backgroundColor,
        animationSpec = tween(durationMillis = 1500)
    )
    // ✅ UI PRINCIPAL
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(animatedBgColor)
            .padding(16.dp)
    ) {

        TopBar(
            selectedTab = currentScreen,
            isCarMoving = isCarMoving,
            onTabSelected = { tab ->
                if ((tab == "PLAYLIST" || tab == "AJUSTES") && isCarMoving) {
                    Toast.makeText(
                        context,
                        "Bloqueado por seguridad: Vehículo en movimiento",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    currentScreen = tab
                }
            }
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(top = 16.dp)
        ) {

            when (currentScreen) {

                "DIRECTORIO" ->
                    DirectorioScreen(
                        rootUri = rootUri,
                        onRootUriChanged = { newUri ->
                            rootUri = newUri
                            if (newUri == null) {
                                navigationHistory.clear()
                                currentElements.clear()
                            }
                        },
                        navigationHistory = navigationHistory,
                        currentElements = currentElements,
                        onElementsChanged = { newElements ->
                            try {
                                currentElements.clear()
                                currentElements.addAll(newElements)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        },
                        onAudioSelected = { audioList, clickedIndex ->
                            reproducirListaUsb(audioList, clickedIndex)
                        },
                        onShuffleAll = { audioList ->
                            if (audioList.isNotEmpty()) {
                                val shuffled = audioList.shuffled()
                                reproducirListaUsb(shuffled, 0)
                            }
                        }
                    )

                "RADIO" ->
                    RadioScreen(
                        stations = radioStations,
                        exoPlayer = exoPlayer,
                        onNavigateToAhoraSuena = {
                            currentScreen = "AHORA SUENA"
                        },
                        onDeleteStation = { station ->
                            radioStations.remove(station)
                            guardarRadioStations(context, radioStations)
                        },
                        onPlayRadio = { uri, name ->
                            Log.d("MainLayout", "📻 onPlayRadio llamado: $name")
                            reproducirRadioDesdeUri(uri, name)
                        }
                    )

                "AHORA SUENA" ->
                    AhoraSuenaScreen(
                        audioUri = selectedAudioUri,
                        audioName = exoPlayer.currentMediaItem?.mediaMetadata?.title?.toString() ?: selectedAudioName,
                        exoPlayer = exoPlayer,
                        playlist = currentPlaylist.value,
                        currentIndex = currentPlaylistIndex,
                        metadataReloadKey = metadataReloadKey,
                        isShuffleActive = isShuffleActive,
                        onShuffleGlobal = {
                            coroutineScope.launch(Dispatchers.Main) {
                                try {
                                    if (isShuffleActive) {
                                        // ✅ DESACTIVAR SHUFFLE — VUELVE A CANCIÓN ANTERIOR
                                        Log.d("Shuffle", "🔄 DESACTIVANDO SHUFFLE")

                                        if (originalPlaylist.isEmpty()) {
                                            Toast.makeText(context, "⚠️ No hay playlist anterior", Toast.LENGTH_SHORT).show()
                                            isShuffleActive = false
                                            return@launch
                                        }

                                        // ✅ GUARDAR ÍNDICE ACTUAL ANTES DE CAMBIAR
                                        val currentIndex = exoPlayer.currentMediaItemIndex.coerceAtLeast(0)
                                        Log.d("Shuffle", "📍 Índice actual guardado: $currentIndex")

                                        AhoraSuenaMetadataCache.clearForced()
                                        isShuffleActive = false

                                        currentFolderPath = savedFolderPathBeforeShuffle
                                        currentFolderName = savedFolderNameBeforeShuffle
                                        currentPlaylist.value = originalPlaylist
                                        currentPlaylistIndex = currentIndex.coerceIn(0, originalPlaylist.lastIndex)
                                        selectedAudioUri = originalPlaylist[currentPlaylistIndex].uri
                                        selectedAudioName = originalPlaylist[currentPlaylistIndex].name
                                        metadataReloadKey++

                                        Log.d("Shuffle", "▶️ Reproduciendo desde índice: $currentPlaylistIndex")
                                        reproducirPlaylistEnExoPlayer(originalPlaylist, currentPlaylistIndex)
                                        delay(300L)

                                        Toast.makeText(context, "✅ Shuffle desactivado", Toast.LENGTH_SHORT).show()
                                        Log.d("Shuffle", "✅ DESACTIVADO - Volviendo a: $currentFolderName (índice: $currentPlaylistIndex)")

                                    } else {
                                        // ✅ ACTIVAR SHUFFLE
                                        Log.d("Shuffle", "🎲 ACTIVANDO SHUFFLE")

                                        // ✅ GUARDAR ESTADO ACTUAL
                                        savedFolderPathBeforeShuffle = currentFolderPath
                                        savedFolderNameBeforeShuffle = currentFolderName
                                        originalPlaylist = currentPlaylist.value.toList()

                                        Log.d("Shuffle", "📂 GUARDADO: path=$savedFolderPathBeforeShuffle, canciones=${originalPlaylist.size}")

                                        val allSongs = withContext(Dispatchers.IO) {
                                            try {
                                                val songs = musicDatabase.getAllSongs()
                                                Log.d("Shuffle", "📊 Total de canciones en BD: ${songs.size}")
                                                songs
                                            } catch (e: Exception) {
                                                Log.e("Shuffle", "❌ Error cargando canciones: ${e.message}")
                                                emptyList()
                                            }
                                        }

                                        if (allSongs.isEmpty()) {
                                            Toast.makeText(context, "No hay canciones en el USB", Toast.LENGTH_SHORT).show()
                                            isShuffleActive = false
                                            return@launch
                                        }

                                        val shuffledElements = allSongs.shuffled().mapIndexed { index, uri ->
                                            ElementoUsb(
                                                name = "Canción ${index + 1}",
                                                uri = Uri.parse(uri),
                                                isDirectory = false
                                            )
                                        }

                                        Log.d("Shuffle", "🎲 Shuffle creado: ${shuffledElements.size} canciones")

                                        AhoraSuenaMetadataCache.clearForced()
                                        isShuffleActive = true

                                        currentFolderPath = "SHUFFLE_GLOBAL"
                                        currentFolderName = "SHUFFLE_GLOBAL"
                                        currentPlaylist.value = shuffledElements
                                        currentPlaylistIndex = 0
                                        selectedAudioUri = shuffledElements[0].uri
                                        selectedAudioName = shuffledElements[0].name
                                        metadataReloadKey++

                                        reproducirPlaylistEnExoPlayer(
                                            shuffledElements,
                                            0,
                                            cargarMetadata = false
                                        )
                                        delay(300L)

                                        Toast.makeText(context, "🎲 Reproduciendo ${shuffledElements.size} canciones aleatorias", Toast.LENGTH_SHORT).show()
                                        Log.d("Shuffle", "✅ ACTIVADO - ${shuffledElements.size} canciones")
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    isShuffleActive = false
                                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                    Log.e("Shuffle", "❌ ERROR: ${e.message}")
                                }
                            }
                        },

                        onPreviousFolder = { cambiarCarpetaRapido("PREV") },
                        onNextFolder = { cambiarCarpetaRapido("NEXT") },
                        onFavorite = {
                            coroutineScope.launch {
                                try {
                                    val favoritesManager = FavoritesManager(context)
                                    val favorites = favoritesManager.getAllFavorites()

                                    if (favorites.isNotEmpty()) {
                                        if (currentFolderPath == "FAVORITOS") {
                                            Log.d("Favoritos", "🔄 DESACTIVANDO FAVORITOS")

                                            AhoraSuenaMetadataCache.clearForced()
                                            isSwitchingFolder = true

                                            val restoredPath = savedFolderPathBeforeShuffle
                                            val restoredName = savedFolderNameBeforeShuffle
                                            val restoredPlaylist = originalPlaylist.toList()

                                            if (restoredPlaylist.isNotEmpty()) {
                                                currentFolderPath = restoredPath
                                                currentFolderName = restoredName
                                                currentPlaylist.value = restoredPlaylist
                                                currentPlaylistIndex = 0
                                                selectedAudioUri = restoredPlaylist[0].uri
                                                selectedAudioName = restoredPlaylist[0].name

                                                metadataReloadKey++
                                                isShuffleActive = false

                                                reproducirPlaylistEnExoPlayer(
                                                    playlist = restoredPlaylist,
                                                    startIndex = 0
                                                )

                                                delay(500L)
                                                isSwitchingFolder = false

                                                Toast.makeText(context, "✅ Favoritos desactivados", Toast.LENGTH_SHORT).show()
                                                Log.d("Favoritos", "✅ DESACTIVADOS")
                                            } else {
                                                isSwitchingFolder = false
                                                Toast.makeText(context, "⚠️ No hay carpeta anterior", Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            Log.d("Favoritos", "❤️ ACTIVANDO FAVORITOS")

                                            val currentPath = currentFolderPath
                                            val currentName = currentFolderName
                                            val currentList = currentPlaylist.value.toList()

                                            savedFolderPathBeforeShuffle = currentPath
                                            savedFolderNameBeforeShuffle = currentName
                                            originalPlaylist = currentList

                                            AhoraSuenaMetadataCache.clearForced()
                                            isSwitchingFolder = true

                                            val favoriteElements = favorites.map { track ->
                                                ElementoUsb(
                                                    name = "${track.artist} - ${track.title}",
                                                    uri = Uri.parse(track.uri),
                                                    isDirectory = false
                                                )
                                            }

                                            currentFolderPath = "FAVORITOS"
                                            currentFolderName = "FAVORITOS"
                                            currentPlaylist.value = favoriteElements
                                            currentPlaylistIndex = 0
                                            selectedAudioUri = favoriteElements[0].uri
                                            selectedAudioName = favoriteElements[0].name

                                            metadataReloadKey++
                                            isShuffleActive = false

                                            reproducirPlaylistEnExoPlayer(
                                                playlist = favoriteElements,
                                                startIndex = 0
                                            )

                                            delay(500L)
                                            isSwitchingFolder = false

                                            Toast.makeText(context, "❤️ Reproduciendo favoritos", Toast.LENGTH_SHORT).show()
                                            Log.d("Favoritos", "✅ ACTIVADOS - ${favoriteElements.size} canciones")
                                        }
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "No hay canciones en favoritos",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    Toast.makeText(
                                        context,
                                        "Error al reproducir favoritos",
                                        Toast.LENGTH_SHORT
                                    ).show()

                                    Log.e("Favoritos", "❌ ERROR: ${e.message}")
                                }
                            }
                        },
                        adaptiveVolumeManager = adaptiveVolumeManager,
                        autoplayManager = autoplayManager
                    )

                "PLAYLIST" ->
                    PlaylistScreen(
                        stations = radioStations,
                        onPlayItem = { uri, name ->
                            Log.d("MainLayout", "📻 onPlayItem llamado desde PLAYLIST: $name")
                            reproducirRadioDesdeUri(uri, name)
                        },
                        onPlayFavorite = { uri, name ->
                            Log.d("MainLayout", "❤️ onPlayFavorite llamado: $name")
                            reproducirListaUsb(
                                listOf(ElementoUsb(name, uri, false)),
                                0
                            )
                        },
                        onAddStation = { name, url ->
                            radioStations.add(RadioStation(name, url))
                            guardarRadioStations(context, radioStations)
                        },
                        onDeleteStation = { station ->
                            radioStations.remove(station)
                            guardarRadioStations(context, radioStations)
                        },
                        exportLauncher = exportLauncher,
                        importLauncher = importLauncher
                    )

                "EQ" ->
                    EqScreen(
                        eqController = eqAudioController,
                        isDriving = isCarMoving
                    )

                "AJUSTES" ->
                    AjustesScreen(
                        backupExportLauncher = backupExportLauncher,
                        backupImportLauncher = backupImportLauncher
                    )
            }
        }
    }

    // ✅ DISPOSABLE EFFECT: CLEANUP AL CERRAR
    DisposableEffect(Unit) {
        onDispose {
            nightModeManager.release()
        }
    }
}