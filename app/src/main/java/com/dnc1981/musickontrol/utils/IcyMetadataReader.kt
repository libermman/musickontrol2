package com.dnc1981.musickontrol.utils

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.net.URL

object IcyMetadataReader {

    suspend fun extraerMetadataIcy(urlString: String): Pair<String, String> {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection

                // ✅ HEADERS IMPORTANTES PARA ICY
                connection.setRequestProperty("User-Agent", "Mozilla/5.0")
                connection.setRequestProperty("Icy-MetaData", "1")
                connection.setRequestProperty("Connection", "close")
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                connection.connect()

                // ✅ LEER HEADERS ICY
                val icyName = connection.getHeaderField("icy-name") ?: ""
                val icyGenre = connection.getHeaderField("icy-genre") ?: ""
                val icyMetaInt = connection.getHeaderField("icy-metaint")?.toIntOrNull() ?: 0

                Log.d("IcyMetadata", "✅ ICY-Name: $icyName")
                Log.d("IcyMetadata", "✅ ICY-Genre: $icyGenre")
                Log.d("IcyMetadata", "✅ ICY-MetaInt: $icyMetaInt")

                if (icyMetaInt > 0) {
                    // ✅ LEER METADATA DEL STREAM
                    val inputStream = BufferedInputStream(connection.inputStream)
                    val buffer = ByteArray(icyMetaInt + 255)
                    var bytesRead = 0

                    try {
                        bytesRead = inputStream.read(buffer)
                    } catch (e: Exception) {
                        Log.e("IcyMetadata", "Error leyendo stream: ${e.message}")
                    }

                    if (bytesRead > 0) {
                        val metadataStr = String(buffer, 0, bytesRead).trim()
                        Log.d("IcyMetadata", "📊 Metadata raw: $metadataStr")

                        // ✅ PARSEAR METADATA
                        val titleMatch = Regex("StreamTitle='([^']*)'").find(metadataStr)
                        val title = titleMatch?.groupValues?.get(1) ?: icyName

                        Log.d("IcyMetadata", "🎵 Título extraído: $title")
                        return@withContext Pair(title, icyGenre)
                    }
                }

                connection.disconnect()
                Pair(icyName, icyGenre)
            } catch (e: Exception) {
                Log.e("IcyMetadata", "❌ Error: ${e.message}")
                e.printStackTrace()
                Pair("", "")
            }
        }
    }
}
