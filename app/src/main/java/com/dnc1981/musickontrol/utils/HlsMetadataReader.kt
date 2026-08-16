package com.dnc1981.musickontrol.utils

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object HlsMetadataReader {

    suspend fun extraerMetadataHls(urlString: String): Pair<String, String> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("HlsMetadata", "📡 Intentando leer M3U8: $urlString")

                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection

                connection.setRequestProperty("User-Agent", "Mozilla/5.0")
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.connect()

                // ✅ LEER CONTENIDO M3U8
                val content = connection.inputStream.bufferedReader().use { it.readText() }
                Log.d("HlsMetadata", "📄 Contenido M3U8:\n$content")

                connection.disconnect()

                // ✅ PARSEAR METADATA DEL M3U8
                val lines = content.split("\n")
                var title = ""
                var artist = ""

                for (i in lines.indices) {
                    val line = lines[i].trim()

                    // Buscar líneas INF (información de segmento)
                    if (line.startsWith("#EXTINF")) {
                        Log.d("HlsMetadata", "📊 EXTINF encontrado: $line")

                        // Formato: #EXTINF:10.0, Title - Artist
                        val parts = line.split(",")
                        if (parts.size > 1) {
                            val metadata = parts[1].trim()
                            if (metadata.isNotEmpty()) {
                                title = metadata
                                Log.d("HlsMetadata", "✅ Título extraído: $title")
                            }
                        }
                    }

                    // Buscar líneas EXT-X-TITLE
                    if (line.startsWith("#EXT-X-TITLE")) {
                        val titlePart = line.substringAfter(":").trim().removeSurrounding("\"")
                        if (titlePart.isNotEmpty()) {
                            title = titlePart
                            Log.d("HlsMetadata", "✅ EXT-X-TITLE: $title")
                        }
                    }

                    // Buscar líneas EXT-X-ARTIST
                    if (line.startsWith("#EXT-X-ARTIST")) {
                        val artistPart = line.substringAfter(":").trim().removeSurrounding("\"")
                        if (artistPart.isNotEmpty()) {
                            artist = artistPart
                            Log.d("HlsMetadata", "✅ EXT-X-ARTIST: $artist")
                        }
                    }
                }

                if (title.isEmpty()) {
                    title = "Radio Online"
                }
                if (artist.isEmpty()) {
                    artist = "Streaming"
                }

                Log.d("HlsMetadata", "🎵 Final: Title=$title, Artist=$artist")
                Pair(title, artist)

            } catch (e: Exception) {
                Log.e("HlsMetadata", "❌ Error: ${e.message}")
                e.printStackTrace()
                Pair("", "")
            }
        }
    }
}
